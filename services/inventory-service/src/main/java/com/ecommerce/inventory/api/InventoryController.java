package com.ecommerce.inventory.api;

import com.ecommerce.inventory.api.dto.*;
import com.ecommerce.inventory.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    // Called synchronously by Order Service before creating an order
    @PostMapping("/availability")
    public ResponseEntity<AvailabilityResponse> checkAvailability(
            @RequestBody Map<String, Integer> skuQty) {
        return ResponseEntity.ok(inventoryService.checkAvailability(skuQty));
    }

    // Public — product pages show stock status
    @GetMapping("/{skuId}")
    public ResponseEntity<InventoryResponse> getInventory(@PathVariable String skuId) {
        return ResponseEntity.ok(inventoryService.getInventory(skuId));
    }

    // Admin only — receive goods from warehouse
    @PostMapping("/{skuId}/add-stock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<InventoryResponse> addStock(
            @PathVariable String skuId,
            @Valid @RequestBody AddStockRequest request) {
        return ResponseEntity.ok(
            inventoryService.addStock(skuId, request.quantity(), request.notes()));
    }
}
