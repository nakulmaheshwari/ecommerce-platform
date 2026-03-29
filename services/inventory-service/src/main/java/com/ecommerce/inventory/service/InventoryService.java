package com.ecommerce.inventory.service;

import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.inventory.api.dto.*;
import com.ecommerce.inventory.domain.*;
import com.ecommerce.inventory.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ReservationRepository reservationRepository;
    private final InventoryMovementRepository movementRepository;
    private final OutboxRepository outboxRepository;
    private final JdbcTemplate jdbcTemplate;

    // ─────────────────────────────────────────────
    // CHECK AVAILABILITY — synchronous REST (called before order creation)
    // ─────────────────────────────────────────────

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

    // ─────────────────────────────────────────────
    // RESERVE STOCK — triggered by order-placed Kafka event
    //
    // THE MOST CRITICAL METHOD IN THIS SERVICE.
    // Uses atomic SQL UPDATE to prevent overselling.
    // No SELECT first. The WHERE clause is the lock.
    // ─────────────────────────────────────────────

    @Transactional
    public ReservationResult reserveStock(UUID orderId, Map<String, Integer> skuQty) {
        log.info("Reserving stock for orderId={} skus={}", orderId, skuQty.keySet());

        // Idempotency: if we already reserved for this order, return success
        boolean alreadyReserved = skuQty.keySet().stream()
            .anyMatch(sku -> reservationRepository.existsBySkuIdAndOrderId(sku, orderId));
        if (alreadyReserved) {
            log.warn("Duplicate reservation attempt for orderId={}", orderId);
            return ReservationResult.success(orderId, List.of());
        }

        List<String> failedSkus = new ArrayList<>();
        List<String> reservedSkus = new ArrayList<>();

        for (var entry : skuQty.entrySet()) {
            String skuId = entry.getKey();
            int qty = entry.getValue();

            // ─────────────────────────────────────────────────────────────────
            // ATOMIC UPDATE — this single SQL is the entire concurrency strategy
            //
            // Two threads can run this simultaneously on the same SKU.
            // PostgreSQL row-level locking ensures only one wins.
            // The loser sees updated = 0 rows and knows reservation failed.
            //
            // The CHECK constraint (available_qty >= 0) in the DB schema is
            // the final safety net — it rejects any negative stock at DB level
            // even if application logic is somehow bypassed.
            // ─────────────────────────────────────────────────────────────────
            int updated = jdbcTemplate.update("""
                UPDATE inventory
                SET available_qty = available_qty - ?,
                    reserved_qty  = reserved_qty  + ?,
                    updated_at    = NOW()
                WHERE sku_id = ?
                  AND available_qty >= ?
                """, qty, qty, skuId, qty);

            if (updated == 0) {
                log.warn("Reservation failed skuId={} requestedQty={} orderId={}",
                    skuId, qty, orderId);
                failedSkus.add(skuId);
            } else {
                // Record the reservation row
                reservationRepository.save(Reservation.builder()
                    .skuId(skuId)
                    .orderId(orderId)
                    .quantity(qty)
                    .status(ReservationStatus.HELD)
                    .expiresAt(Instant.now().plusSeconds(900))
                    .build());

                // Audit log
                recordMovement(skuId, "RESERVATION", -qty, orderId, "ORDER");
                reservedSkus.add(skuId);
                log.info("Reserved skuId={} qty={} orderId={}", skuId, qty, orderId);
            }
        }

        if (!failedSkus.isEmpty()) {
            // COMPENSATE: roll back everything we already reserved in this batch
            log.warn("Partial reservation failure orderId={} failedSkus={}. Rolling back.",
                orderId, failedSkus);
            reservedSkus.forEach(sku -> releaseReservation(orderId, sku));

            // Write failed event to outbox
            writeOutboxEvent("inventory.reservation-failed", orderId, Map.of(
                "orderId",    orderId.toString(),
                "failedSkus", failedSkus,
                "reason",     "INSUFFICIENT_STOCK"
            ));

            return ReservationResult.failed(orderId, failedSkus);
        }

        // All reserved successfully — write success event to outbox
        writeOutboxEvent("inventory.reserved", orderId, Map.of(
            "orderId",    orderId.toString(),
            "reservedSkus", reservedSkus
        ));

        return ReservationResult.success(orderId, reservedSkus);
    }

    // ─────────────────────────────────────────────
    // RELEASE — triggered by payment-failed event
    // Also used internally for compensation
    // ─────────────────────────────────────────────

    @Transactional
    public void releaseOrderReservations(UUID orderId) {
        List<Reservation> reservations =
            reservationRepository.findByOrderIdAndStatus(orderId, ReservationStatus.HELD);

        if (reservations.isEmpty()) {
            log.warn("No HELD reservations found for orderId={}", orderId);
            return;
        }

        for (Reservation reservation : reservations) {
            releaseReservation(orderId, reservation.getSkuId());
        }

        log.info("Released all reservations for orderId={}", orderId);
    }

    @Transactional
    public void releaseReservation(UUID orderId, String skuId) {
        reservationRepository.findBySkuIdAndOrderId(skuId, orderId)
            .filter(r -> r.getStatus() == ReservationStatus.HELD)
            .ifPresent(reservation -> {
                int qty = reservation.getQuantity();

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
                log.info("Released reservation skuId={} qty={} orderId={}", skuId, qty, orderId);
            });
    }

    // ─────────────────────────────────────────────
    // CONFIRM — triggered by payment-succeeded event
    // Converts HELD reservation to CONFIRMED
    // ─────────────────────────────────────────────

    @Transactional
    public void confirmOrderReservations(UUID orderId) {
        List<Reservation> reservations =
            reservationRepository.findByOrderIdAndStatus(orderId, ReservationStatus.HELD);

        for (Reservation reservation : reservations) {
            // Stock was already decremented from available_qty during RESERVE.
            // Now we just move it from reserved_qty to "committed" by confirming.
            jdbcTemplate.update("""
                UPDATE inventory
                SET reserved_qty = reserved_qty - ?,
                    updated_at   = NOW()
                WHERE sku_id = ?
                """, reservation.getQuantity(), reservation.getSkuId());

            reservation.setStatus(ReservationStatus.CONFIRMED);
            reservationRepository.save(reservation);

            recordMovement(reservation.getSkuId(), "CONFIRMATION",
                -reservation.getQuantity(), orderId, "ORDER");
        }

        log.info("Confirmed {} reservations for orderId={}", reservations.size(), orderId);
    }

    // ─────────────────────────────────────────────
    // INBOUND STOCK — admin adds new stock
    // ─────────────────────────────────────────────

    @Transactional
    public InventoryResponse addStock(String skuId, int quantity, String notes) {
        Inventory inventory = inventoryRepository.findById(skuId)
            .orElseThrow(() -> new ResourceNotFoundException("Inventory", skuId));

        int before = inventory.getAvailableQty();
        inventory.setAvailableQty(before + quantity);
        inventoryRepository.save(inventory);

        recordMovement(skuId, "INBOUND", quantity, null, "PURCHASE_ORDER");
        log.info("Added stock skuId={} qty={} newTotal={}", skuId, quantity,
            inventory.getAvailableQty());

        return toResponse(inventory);
    }

    // ─────────────────────────────────────────────
    // INITIALIZE STOCK — triggered by product.created event
    // Creates a zero-stock inventory record so the SKU is
    // known to the system. Stock is added later via addStock().
    // Idempotent — safe to call multiple times for the same SKU.
    // ─────────────────────────────────────────────

    @Transactional
    public void initializeStock(UUID productId, String sku) {
        if (inventoryRepository.existsById(sku)) {
            log.warn("Inventory already exists for sku={} — skipping initialization", sku);
            return;
        }

        Inventory inventory = Inventory.builder()
            .skuId(sku)
            .productId(productId)
            .availableQty(0)
            .reservedQty(0)
            .reorderPoint(10)
            .reorderQty(100)
            .warehouseId("WH-MAIN")
            .build();

        inventoryRepository.saveAndFlush(inventory); // flush before recordMovement queries the DB
        recordMovement(sku, "INITIAL", 0, productId, "PRODUCT_CREATED");

        log.info("Initialized inventory record skuId={} productId={}", sku, productId);
    }

    // ─────────────────────────────────────────────
    // EXPIRED RESERVATION CLEANUP — scheduled job
    // ─────────────────────────────────────────────

    @Transactional
    public void expireStaleReservations() {
        List<Reservation> expired =
            reservationRepository.findExpiredReservations(Instant.now());

        if (expired.isEmpty()) return;
        log.info("Expiring {} stale reservations", expired.size());

        for (Reservation reservation : expired) {
            jdbcTemplate.update("""
                UPDATE inventory
                SET available_qty = available_qty + ?,
                    reserved_qty  = reserved_qty  - ?,
                    updated_at    = NOW()
                WHERE sku_id = ?
                """, reservation.getQuantity(), reservation.getQuantity(),
                    reservation.getSkuId());

            reservation.setStatus(ReservationStatus.EXPIRED);
            reservationRepository.save(reservation);

            recordMovement(reservation.getSkuId(), "RELEASE",
                reservation.getQuantity(), reservation.getOrderId(), "EXPIRY");
        }
    }

    // ─────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────

    private void recordMovement(String skuId, String type, int delta,
                                UUID referenceId, String referenceType) {
        // Get current qty for the before/after snapshot
        int currentQty = jdbcTemplate.queryForObject(
            "SELECT available_qty FROM inventory WHERE sku_id = ?",
            Integer.class, skuId);

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

    private void writeOutboxEvent(String eventType, Object aggregateId,
                                  Map<String, Object> payload) {
        outboxRepository.save(OutboxEvent.builder()
            .aggregateType("Inventory")
            .aggregateId(aggregateId.toString())
            .eventType(eventType)
            .payload(payload)
            .build());
    }

    @Transactional(readOnly = true)
    public InventoryResponse getInventory(String skuId) {
        return toResponse(inventoryRepository.findById(skuId)
            .orElseThrow(() -> new ResourceNotFoundException("Inventory", skuId)));
    }

    private InventoryResponse toResponse(Inventory i) {
        return new InventoryResponse(
            i.getSkuId(), i.getProductId(),
            i.getAvailableQty(), i.getReservedQty(),
            i.totalQty(), i.isLowStock(), i.isOutOfStock(),
            i.getWarehouseId(), i.getUpdatedAt()
        );
    }
}
