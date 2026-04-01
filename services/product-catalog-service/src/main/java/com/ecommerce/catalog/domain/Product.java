package com.ecommerce.catalog.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "products")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String sku;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    @Builder.Default
    private Boolean available = true;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    private String brand;

    // Money in paise — NEVER float or double
    @Column(nullable = false)
    private Long pricePaise;

    @Column(nullable = false)
    private Long mrpPaise;

    private Long costPaise;

    @Column(nullable = false)
    private java.math.BigDecimal taxPercent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ProductStatus status = ProductStatus.DRAFT;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isDigital = false;

    private Integer weightGrams;

    @Column(nullable = false)
    @Builder.Default
    private Double averageRating = 0.0;

    @Column(nullable = false)
    @Builder.Default
    private Integer totalReviews = 0;

    @Column(nullable = false)
    private UUID createdBy;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProductVariant> variants = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private List<ProductImage> images = new ArrayList<>();

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    private Instant publishedAt;

    public void publish() {
        if (this.status == ProductStatus.DRAFT || this.status == ProductStatus.INACTIVE) {
            this.status = ProductStatus.ACTIVE;
            this.publishedAt = Instant.now();
        }
    }

    // Discount percentage for display
    public int discountPercent() {
        if (mrpPaise == null || mrpPaise <= 0 || pricePaise == null) return 0;
        if (pricePaise >= mrpPaise) return 0;
        return (int) ((mrpPaise - pricePaise) * 100 / mrpPaise);
    }
}
