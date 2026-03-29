package com.ecommerce.catalog.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "product_images")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id")
    private ProductVariant variant;

    @Column(nullable = false)
    private String url;

    private String altText;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isPrimary = false;

    @Column(nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @org.hibernate.annotations.CreationTimestamp
    private Instant createdAt;
}

