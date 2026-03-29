package com.ecommerce.inventory.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_movements")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class InventoryMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String skuId;

    @Column(nullable = false)
    private String movementType;  // RESERVATION, RELEASE, CONFIRMATION, INBOUND, ADJUSTMENT

    @Column(nullable = false)
    private Integer quantityDelta;

    private UUID referenceId;
    private String referenceType;

    @Column(nullable = false)
    private Integer beforeQty;

    @Column(nullable = false)
    private Integer afterQty;

    private String notes;

    @Builder.Default
    private String createdBy = "system";

    @Builder.Default
    private Instant createdAt = Instant.now();
}
