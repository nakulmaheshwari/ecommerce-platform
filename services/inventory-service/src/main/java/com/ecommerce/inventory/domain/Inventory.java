package com.ecommerce.inventory.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class Inventory {

    @Id
    private String skuId;  // Natural key — SKU is the identifier

    @Column(nullable = false)
    private UUID productId;

    @Column(nullable = false)
    @Builder.Default
    private Integer availableQty = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer reservedQty = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer reorderPoint = 10;

    @Column(nullable = false)
    @Builder.Default
    private Integer reorderQty = 100;

    @Column(nullable = false)
    @Builder.Default
    private String warehouseId = "WH-MAIN";

    @UpdateTimestamp
    private Instant updatedAt;

    // Total physical stock
    public Integer totalQty() {
        return availableQty + reservedQty;
    }

    public boolean isLowStock() {
        return availableQty <= reorderPoint;
    }

    public boolean isOutOfStock() {
        return availableQty == 0;
    }
}
