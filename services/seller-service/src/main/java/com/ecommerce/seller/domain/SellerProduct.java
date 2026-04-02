package com.ecommerce.seller.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "seller_products")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellerProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private Seller seller;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(nullable = false, length = 100)
    private String sku;

    @Column(name = "selling_price_paise", nullable = false)
    private Long sellingPricePaise;

    @Column(name = "mrp_paise", nullable = false)
    private Long mrpPaise;

    @Column(name = "dispatch_days", nullable = false)
    private Integer dispatchDays = 2;

    @Column(name = "ships_from_city")
    private String shipsFromCity;

    @Column(name = "ships_from_state")
    private String shipsFromState;

    @Column(nullable = false, length = 30)
    private String status = "DRAFT";

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "custom_title", length = 500)
    private String customTitle;

    @Column(name = "custom_description")
    private String customDescription;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    private Long version;
}
