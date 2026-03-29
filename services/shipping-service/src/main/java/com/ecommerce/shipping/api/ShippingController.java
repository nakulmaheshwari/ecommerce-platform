package com.ecommerce.shipping.api;

import com.ecommerce.shipping.domain.Shipment;
import com.ecommerce.shipping.service.ShippingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shipments")
@RequiredArgsConstructor
public class ShippingController {

    private final ShippingService shippingService;

    @GetMapping("/orders/{orderId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Shipment> getByOrderId(@PathVariable UUID orderId) {
        return ResponseEntity.ok(shippingService.getByOrderId(orderId));
    }

    @GetMapping("/track/{trackingNumber}")
    public ResponseEntity<Shipment> trackShipment(@PathVariable String trackingNumber) {
        return ResponseEntity.ok(shippingService.getByTracking(trackingNumber));
    }

    @PostMapping("/orders/{orderId}/mark-shipped")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Shipment> markShipped(@PathVariable UUID orderId) {
        return ResponseEntity.ok(shippingService.markShipped(orderId));
    }
}
