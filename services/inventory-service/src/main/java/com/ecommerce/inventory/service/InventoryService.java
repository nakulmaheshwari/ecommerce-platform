package com.ecommerce.inventory.service;

import com.ecommerce.common.exception.InsufficientStockException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.event.InventoryStatusChangedEvent;
import com.ecommerce.inventory.api.dto.*;
import com.ecommerce.inventory.domain.*;
import com.ecommerce.inventory.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Core business logic for the Inventory Service.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ReservationRepository reservationRepository;
    private final InventoryMovementRepository movementRepository;
    private final OutboxRepository outboxRepository;
    private final JdbcTemplate jdbcTemplate;

    // ────────────────────────────────────────────────────────────────────────
    // 1. AVAILABILITY CHECKFLOW
    // ────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AvailabilityResponse checkAvailability(Map<String, Integer> skuQty) {
        Set<String> skuIds = skuQty.keySet();
        Map<String, Inventory> inventoryMap = inventoryRepository
                .findBySkuIdIn(skuIds)
                .stream()
                .collect(Collectors.toMap(Inventory::getSkuId, i -> i));

        List<String> unavailable = new ArrayList<>();
        Map<String, Integer> availableQtys = new HashMap<>();

        for (var entry : skuQty.entrySet()) {
            String skuId = entry.getKey();
            int requested = entry.getValue();
            Inventory inv = inventoryMap.get(skuId);

            if (inv == null || inv.getAvailableQty() < requested) {
                unavailable.add(skuId);
                availableQtys.put(skuId, inv == null ? 0 : inv.getAvailableQty());
            } else {
                availableQtys.put(skuId, inv.getAvailableQty());
            }
        }

        return new AvailabilityResponse(unavailable.isEmpty(), unavailable, availableQtys);
    }

    // ────────────────────────────────────────────────────────────────────────
    // 2. RESERVATION FLOW
    // ────────────────────────────────────────────────────────────────────────

    @Transactional
    public ReservationResult reserveStock(UUID orderId, Map<String, Integer> skuQty) {
        log.info("Starting batch reservation process for orderId={}", orderId);

        if (reservationRepository.existsByOrderId(orderId)) {
            log.warn("Idempotency match: Order {} already has reservations.", orderId);
            return ReservationResult.success(orderId, List.of());
        }

        List<String> skuIds = new ArrayList<>(skuQty.keySet());
        
        // Capture availability BEFORE update for flip detection
        Map<String, Boolean> beforeAvailable = skuIds.stream()
            .collect(Collectors.toMap(sku -> sku, sku -> inventoryRepository.findById(sku)
                .map(i -> i.getAvailableQty() > 0).orElse(false)));

        int[] updateResults = jdbcTemplate.batchUpdate("""
                UPDATE inventory
                SET available_qty = available_qty - ?,
                    reserved_qty  = reserved_qty + ?,
                    version       = version + 1,
                    updated_at    = NOW()
                WHERE sku_id = ?
                  AND available_qty >= ?
                """,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        String skuId = skuIds.get(i);
                        int qty = skuQty.get(skuId);
                        ps.setInt(1, qty);
                        ps.setInt(2, qty);
                        ps.setString(3, skuId);
                        ps.setInt(4, qty);
                    }

                    @Override
                    public int getBatchSize() { return skuIds.size(); }
                });

        for (int i = 0; i < updateResults.length; i++) {
            if (updateResults[i] == 0) {
                throw new InsufficientStockException(orderId);
            }
        }

        List<Reservation> reservations = skuIds.stream()
                .map(skuId -> Reservation.builder()
                        .skuId(skuId)
                        .orderId(orderId)
                        .quantity(skuQty.get(skuId))
                        .status(ReservationStatus.HELD)
                        .expiresAt(Instant.now().plusSeconds(900))
                        .build())
                .collect(Collectors.toList());

        reservationRepository.saveAll(reservations);

        for (String skuId : skuIds) {
            recordMovement(skuId, "RESERVATION", -skuQty.get(skuId), orderId, "ORDER");
            
            // Check for flip (Available -> Out of Stock)
            Inventory updated = inventoryRepository.findById(skuId).orElseThrow();
            if (beforeAvailable.get(skuId) && updated.getAvailableQty() == 0) {
                emitStatusChanged(updated, false);
            }
        }

        writeOutboxEvent("inventory.reserved", orderId, Map.of(
                "orderId", orderId.toString(),
                "reservedSkus", skuIds));

        return ReservationResult.success(orderId, skuIds);
    }

    @Transactional
    public void releaseOrderReservations(UUID orderId) {
        List<Reservation> reservations = reservationRepository.findByOrderIdAndStatus(orderId, ReservationStatus.HELD);
        for (Reservation reservation : reservations) {
            releaseReservation(orderId, reservation.getSkuId());
        }
    }

    @Transactional
    public void releaseReservation(UUID orderId, String skuId) {
        reservationRepository.findBySkuIdAndOrderId(skuId, orderId)
                .filter(r -> r.getStatus() == ReservationStatus.HELD)
                .ifPresent(reservation -> {
                    int qty = reservation.getQuantity();

                    Inventory inv = inventoryRepository.findById(skuId).orElseThrow();
                    boolean wasOutOfStock = inv.getAvailableQty() == 0;

                    jdbcTemplate.update("""
                            UPDATE inventory
                            SET available_qty = available_qty + ?,
                                reserved_qty  = reserved_qty  - ?,
                                updated_at    = NOW()
                            WHERE sku_id = ?
                            """, qty, qty, skuId);

                    reservation.setStatus(ReservationStatus.RELEASED);
                    reservationRepository.save(reservation);
                    recordMovement(skuId, "RELEASE", qty, orderId, "ORDER");
                    
                    if (wasOutOfStock && qty > 0) {
                        emitStatusChanged(inv, true);
                    }
                });
    }

    @Transactional
    public void confirmOrderReservations(UUID orderId) {
        List<Reservation> reservations = reservationRepository.findByOrderIdAndStatus(orderId, ReservationStatus.HELD);
        for (Reservation reservation : reservations) {
            jdbcTemplate.update("""
                    UPDATE inventory
                    SET reserved_qty = reserved_qty - ?,
                        updated_at   = NOW()
                    WHERE sku_id = ?
                    """, reservation.getQuantity(), reservation.getSkuId());

            reservation.setStatus(ReservationStatus.CONFIRMED);
            reservationRepository.save(reservation);
            recordMovement(reservation.getSkuId(), "CONFIRMATION", -reservation.getQuantity(), orderId, "ORDER");
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // 3. ADMINISTRATIVE ACTIONS
    // ────────────────────────────────────────────────────────────────────────

    @Transactional
    public InventoryResponse addStock(String skuId, int quantity, String notes) {
        Inventory inv = inventoryRepository.findById(skuId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory", skuId));
        
        boolean wasOutOfStock = inv.getAvailableQty() == 0;

        jdbcTemplate.update("""
                UPDATE inventory
                SET available_qty = available_qty + ?,
                    updated_at    = NOW()
                WHERE sku_id = ?
                """, quantity, skuId);

        Inventory updated = inventoryRepository.findById(skuId).orElseThrow();
        recordMovement(skuId, "INBOUND", quantity, null, 
                notes != null && !notes.isBlank() ? notes : "PURCHASE_ORDER");

        if (wasOutOfStock && quantity > 0) {
            emitStatusChanged(updated, true);
        }

        return toResponse(updated);
    }

    @Transactional
    public void initializeStock(UUID productId, String sku) {
        if (inventoryRepository.existsById(sku)) return;

        Inventory inventory = Inventory.builder()
                .skuId(sku)
                .productId(productId)
                .availableQty(0)
                .reservedQty(0)
                .reorderPoint(10)
                .reorderQty(100)
                .warehouseId("WH-MAIN")
                .build();

        inventoryRepository.saveAndFlush(inventory);
        recordMovement(sku, "INITIAL", 0, productId, "PRODUCT_CREATED");
    }

    @Transactional
    public void expireStaleReservations() {
        List<Reservation> expired = reservationRepository.findExpiredReservations(Instant.now());
        for (Reservation reservation : expired) {
            int qty = reservation.getQuantity();
            String skuId = reservation.getSkuId();

            Inventory inv = inventoryRepository.findById(skuId).orElseThrow();
            boolean wasOutOfStock = inv.getAvailableQty() == 0;

            jdbcTemplate.update("""
                    UPDATE inventory
                    SET available_qty = available_qty + ?,
                        reserved_qty  = reserved_qty  - ?,
                        updated_at    = NOW()
                    WHERE sku_id = ?
                    """, qty, qty, skuId);

            reservation.setStatus(ReservationStatus.EXPIRED);
            reservationRepository.save(reservation);
            recordMovement(skuId, "RELEASE", qty, reservation.getOrderId(), "EXPIRY");
            
            if (wasOutOfStock && qty > 0) {
                emitStatusChanged(inv, true);
            }
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // 4. UTILITIES
    // ────────────────────────────────────────────────────────────────────────

    private void emitStatusChanged(Inventory inventory, boolean available) {
        writeOutboxEvent("inventory.status-changed", inventory.getSkuId(), Map.of(
            "productId", inventory.getProductId().toString(),
            "sku", inventory.getSkuId(),
            "available", available
        ));
        log.info("Availability status changed for SKU {}: isAvailable={}", inventory.getSkuId(), available);
    }

    private void recordMovement(String skuId, String type, int delta, UUID referenceId, String referenceType) {
        int currentQty = jdbcTemplate.queryForObject("SELECT available_qty FROM inventory WHERE sku_id = ?", Integer.class, skuId);
        movementRepository.save(InventoryMovement.builder()
                .skuId(skuId)
                .movementType(type)
                .quantityDelta(delta)
                .referenceId(referenceId)
                .referenceType(referenceType)
                .beforeQty(currentQty - delta)
                .afterQty(currentQty)
                .build());
    }

    private void writeOutboxEvent(String eventType, Object aggregateId, Map<String, Object> payload) {
        outboxRepository.save(OutboxEvent.builder()
                .aggregateType("Inventory")
                .aggregateId(aggregateId.toString())
                .eventType(eventType)
                .payload(payload)
                .build());
    }

    @Transactional(readOnly = true)
    public InventoryResponse getInventory(String skuId) {
        return toResponse(inventoryRepository.findById(skuId).orElseThrow(() -> new ResourceNotFoundException("Inventory", skuId)));
    }

    @Transactional(readOnly = true)
    public List<InventoryMovement> getMovementHistory(String skuId) {
        return movementRepository.findBySkuIdOrderByCreatedAtDesc(skuId, org.springframework.data.domain.PageRequest.of(0, 100)).getContent();
    }

    @Transactional(readOnly = true)
    public List<InventoryResponse> getLowStockItems() {
        return inventoryRepository.findLowStockItems().stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Reservation> getReservationsByOrder(UUID orderId) {
        return reservationRepository.findByOrderId(orderId);
    }

    private InventoryResponse toResponse(Inventory i) {
        return new InventoryResponse(i.getSkuId(), i.getProductId(), i.getAvailableQty(), i.getReservedQty(), i.totalQty(), i.isLowStock(), i.isOutOfStock(), i.getWarehouseId(), i.getUpdatedAt());
    }
}
