package com.ecommerce.inventory.api;

import com.ecommerce.inventory.api.dto.*;
import com.ecommerce.inventory.domain.InventoryMovement;
import com.ecommerce.inventory.domain.Reservation;
import com.ecommerce.inventory.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST Controller for managing inventory stock levels, batch availability checks, 
 * and administrative audit trails.
 * 
 * This controller serves both public requests (product pages) and administrative 
 * requests (warehouse management).
 */
@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    /**
     * Checks if multiple SKUs are available in requested quantities.
     * Often called by the Order Service or Shopping Cart via Feign/REST
     * during the checkout "pre-check" phase.
     * 
     * @param skuQty A map where the key is the SKU ID and the value is the desired quantity.
     * @return AvailabilityResponse indicating if all items can be fulfilled.
     */
    @PostMapping("/availability")
    public ResponseEntity<AvailabilityResponse> checkAvailability(
            @RequestBody Map<String, Integer> skuQty) {
        return ResponseEntity.ok(inventoryService.checkAvailability(skuQty));
    }

    /**
     * Retrieves the current stock status for a single SKU.
     * Publicly accessible; used primarily to display "In Stock" or "Low Stock"
     * labels on product detail pages.
     * 
     * @param skuId The Stock Keeping Unit identifier.
     * @return Current InventoryResponse for the specified SKU.
     */
    @GetMapping("/{skuId}")
    public ResponseEntity<InventoryResponse> getInventory(@PathVariable String skuId) {
        return ResponseEntity.ok(inventoryService.getInventory(skuId));
    }

    /**
     * Adjusts stock quantity upwards (Restock).
     * Typically called when a physical shipment arrives at the warehouse.
     * Requires ADMIN role.
     * 
     * @param skuId The SKU to restock.
     * @param request Contains quantity and audit notes.
     * @return Updated InventoryResponse.
     */
    @PostMapping("/{skuId}/add-stock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<InventoryResponse> addStock(
            @PathVariable String skuId,
            @Valid @RequestBody AddStockRequest request) {
        return ResponseEntity.ok(
            inventoryService.addStock(skuId, request.quantity(), request.notes()));
    }

    /**
     * Returns the full history of stock movements (RESERVATION, INBOUND, etc.) for a SKU.
     * Vital for auditing discrepancies between physical stock and digital records.
     * Requires ADMIN role.
     * 
     * @param skuId The SKU to audit.
     * @return A list of all historical InventoryMovements.
     */
    @GetMapping("/{skuId}/movements")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<InventoryMovement>> getMovementHistory(@PathVariable String skuId) {
        return ResponseEntity.ok(inventoryService.getMovementHistory(skuId));
    }

    /**
     * Finds all items currently at or below their reorder threshold.
     * Used by warehouse managers to generate replenishment orders.
     * Requires ADMIN role.
     * 
     * @return List of InventoryResponse for all low-stock SKUs.
     */
    @GetMapping("/low-stock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<InventoryResponse>> getLowStockItems() {
        return ResponseEntity.ok(inventoryService.getLowStockItems());
    }

    /**
     * Retrieves all stock reservations associated with a specific Order.
     * Used for debugging "stuck" orders or verifying if stock was correctly held.
     * Requires ADMIN role.
     * 
     * @param orderId The UUID of the order to inspect.
     * @return List of current active/confirmed reservations for the order.
     */
    @GetMapping("/reservations/order/{orderId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Reservation>> getReservationsByOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(inventoryService.getReservationsByOrder(orderId));
    }
}
