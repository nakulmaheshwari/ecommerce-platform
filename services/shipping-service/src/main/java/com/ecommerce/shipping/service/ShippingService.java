package com.ecommerce.shipping.service;

import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.shipping.domain.*;
import com.ecommerce.shipping.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShippingService {

    private final ShipmentRepository shipmentRepository;
    private final OutboxRepository outboxRepository;

    /**
     * Create shipment when payment succeeds.
     *
     * In production this calls a carrier API (BlueDart, Delhivery, etc.)
     * to book a pickup and get a real tracking number.
     * Here we simulate with a generated tracking number.
     *
     * Outbox writes (same transaction):
     * 1. order.shipped → Order Service moves to SHIPPED
     * 2. notification.triggered → customer gets shipping email
     */
    @Transactional
    public Shipment createShipment(UUID orderId, UUID userId,
                                   Map<String, Object> shippingAddress) {
        // Idempotency — don't create duplicate shipments
        if (shipmentRepository.existsByOrderId(orderId)) {
            log.warn("Shipment already exists for orderId={}", orderId);
            return shipmentRepository.findByOrderId(orderId).orElseThrow();
        }

        String trackingNumber = generateTrackingNumber();
        LocalDate estimatedDelivery = LocalDate.now().plusDays(3);

        Shipment shipment = Shipment.builder()
            .orderId(orderId)
            .userId(userId)
            .trackingNumber(trackingNumber)
            .carrier("BlueDart")
            .status("CREATED")
            .shippingAddress(shippingAddress)
            .estimatedDeliveryDate(estimatedDelivery)
            .build();

        shipmentRepository.save(shipment);

        // Publish order-shipped event — Order Service listens to this
        outboxRepository.save(OutboxEvent.builder()
            .aggregateType("Shipment")
            .aggregateId(shipment.getId())
            .eventType("order.shipped")
            .payload(Map.of(
                "orderId",           orderId.toString(),
                "shipmentId",        shipment.getId().toString(),
                "trackingNumber",    trackingNumber,
                "carrier",           shipment.getCarrier(),
                "estimatedDelivery", estimatedDelivery.toString()
            ))
            .build());

        // Trigger shipping notification email with tracking details
        outboxRepository.save(OutboxEvent.builder()
            .aggregateType("Shipment")
            .aggregateId(shipment.getId())
            .eventType("notification.triggered")
            .payload(Map.of(
                "userId",     userId.toString(),
                "channel",    "EMAIL",
                "templateId", "order-shipped-v1",
                "templateVars", Map.of(
                    "orderId",           orderId.toString(),
                    "trackingNumber",    trackingNumber,
                    "carrier",           shipment.getCarrier(),
                    "estimatedDelivery", estimatedDelivery.toString()
                )
            ))
            .build());

        log.info("Shipment created shipmentId={} orderId={} tracking={}",
            shipment.getId(), orderId, trackingNumber);

        return shipment;
    }

    @Transactional
    public Shipment markShipped(UUID orderId) {
        Shipment shipment = shipmentRepository.findByOrderId(orderId)
            .orElseThrow(() -> new ResourceNotFoundException("Shipment", orderId.toString()));
        shipment.setStatus("IN_TRANSIT");
        shipment.setShippedAt(Instant.now());
        shipmentRepository.save(shipment);
        log.info("Shipment marked as IN_TRANSIT orderId={}", orderId);
        return shipment;
    }

    @Transactional(readOnly = true)
    public Shipment getByOrderId(UUID orderId) {
        return shipmentRepository.findByOrderId(orderId)
            .orElseThrow(() -> new ResourceNotFoundException("Shipment", orderId.toString()));
    }

    @Transactional(readOnly = true)
    public Shipment getByTracking(String trackingNumber) {
        return shipmentRepository.findByTrackingNumber(trackingNumber)
            .orElseThrow(() -> new ResourceNotFoundException("Shipment", trackingNumber));
    }

    private String generateTrackingNumber() {
        // Real implementation calls carrier API
        // BlueDart format: BD + 11 digits
        return "BD" + System.currentTimeMillis();
    }
}
