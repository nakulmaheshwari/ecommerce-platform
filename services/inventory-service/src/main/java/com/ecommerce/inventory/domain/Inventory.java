package com.ecommerce.inventory.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents the primary stock record for a product variant (SKU).
 * This entity implements a "Total = Available + Reserved" logical model.
 * 
 * - availableQty: Units that are free for new customers to buy.
 * - reservedQty: Units currently on hold for "HELD" orders (awaiting payment).
 * - totalQty(): The sum of both, representing the actual physical units in the warehouse.
 */
@Entity
@Table(name = "inventory")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class Inventory {

    /**
     * Unique identifier for the stock record (Stock Keeping Unit).
     * This is globally unique across the ecommerce platform.
     */
    @Id
    private String skuId;

    /**
     * The unique ID of the product this SKU belongs to.
     * Multiple SKUs (e.g., Color Red, Color Blue) may share the same productId.
     */
    @Column(nullable = false)
    private UUID productId;

    /**
     * Units available for immediate sale.
     * Decreases when a reservation starts ('HELD').
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer availableQty = 0;

    /**
     * Units locked for active orders but not yet physically shipped.
     * Increases when an order is placed; decreases when shipping is confirmed (REDUCE TOTAL)
     * or when a reservation expires (RETURN TO AVAILABLE).
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer reservedQty = 0;

    /**
     * The threshold at which an 'OUT_OF_STOCK' or 'LOW_STOCK' event should be triggered.
     * Once availableQty <= reorderPoint, the service notifies the Catalog and Search services.
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer reorderPoint = 10;

    /**
     * The suggested quantity to order from the supplier when reorderPoint is hit.
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer reorderQty = 100;

    /**
     * The logical or physical location of the stock.
     * Future versions will support multi-warehouse allocation based on this ID.
     */
    @Column(nullable = false)
    @Builder.Default
    private String warehouseId = "WH-MAIN";

    /**
     * The exact moment the stock level was last modified.
     */
    @UpdateTimestamp
    private Instant updatedAt;

    /**
     * Optimistic locking version to prevent lost updates in 
     * extreme concurrency (Flash Sales).
     */
    @Version
    private Integer version;

    /**
     * Calculated value of all physical units currently accounted for in this warehouse.
     */
    public Integer totalQty() {
        return availableQty + reservedQty;
    }

    /**
     * Helper to determine if the SKU is currently flagged for replenishment.
     */
    public boolean isLowStock() {
        return availableQty <= reorderPoint;
    }

    /**
     * Returns true if no units are available for new sales.
     * Note: Total units might still be > 0 if there are active reservations.
     */
    public boolean isOutOfStock() {
        return availableQty == 0;
    }
}
