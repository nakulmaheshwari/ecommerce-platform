package com.ecommerce.catalog.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "product_variants")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, unique = true)
    private String sku;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Long pricePaise;

    // JSONB column: {"color": "Black", "storage": "128GB", "size": "M"}
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, String> attributes = Map.of();

    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @org.hibernate.annotations.CreationTimestamp
    private Instant createdAt;
}
