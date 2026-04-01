package com.ecommerce.inventory.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a single record in the Inventory Audit Trail.
 * Every modification to the Inventory stock levels MUST create a corresponding movement.
 */
@Entity
@Table(name = "inventory_movements")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class InventoryMovement {

    /**
     * Unique identifier for this specific movement event.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * The SKU affected by this movement.
     */
    @Column(nullable = false)
    private String skuId;

    /**
     * The category of movement (e.g., RESERVATION, RELEASE, INBOUND, ADJUSTMENT).
     * This helps in classifying the stock change for reporting and reconciliation.
     */
    @Column(nullable = false)
    private String movementType;

    /**
     * The amount by which the quantity changed.
     * Negative values indicate stock leaving (Reservations/Sales).
     * Positive values indicate stock arriving (Inbound/Restocking).
     */
    @Column(nullable = false)
    private Integer quantityDelta;

    /**
     * The ID of the business entity that triggered this movement (e.g., Order ID).
     */
    private UUID referenceId;

    /**
     * The type of business entity referenced (e.g., "ORDER", "SUPPLIER_PO").
     */
    private String referenceType;

    /**
     * Snapshot of the 'availableQty' before this movement was applied.
     */
    @Column(nullable = false)
    private Integer beforeQty;

    /**
     * Snapshot of the 'availableQty' after this movement was applied.
     */
    @Column(nullable = false)
    private Integer afterQty;

    /**
     * Optional text description for why this movement occurred.
     */
    private String notes;

    /**
     * The user ID or system component that initiated the change.
     */
    @Builder.Default
    private String createdBy = "system";

    /**
     * Timestamp of the movement for audit timeline purposes.
     */
    @Builder.Default
    private Instant createdAt = Instant.now();
}
