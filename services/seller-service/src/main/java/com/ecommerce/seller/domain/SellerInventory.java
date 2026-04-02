package com.ecommerce.seller.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "seller_inventories")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellerInventory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_product_id", nullable = false, unique = true)
    private SellerProduct sellerProduct;

    @Column(name = "quantity_available", nullable = false)
    private Integer quantityAvailable = 0;

    @Column(name = "quantity_reserved", nullable = false)
    private Integer quantityReserved = 0;

    @Column(name = "quantity_sold", nullable = false)
    private Long quantitySold = 0L;

    @Column(name = "low_stock_threshold")
    private Integer lowStockThreshold = 5;

    @UpdateTimestamp
    @Column(name = "last_updated_at", nullable = false)
    private OffsetDateTime lastUpdatedAt;

    @Column(name = "last_updated_by")
    private String lastUpdatedBy;

    @Version
    private Long version;
}
