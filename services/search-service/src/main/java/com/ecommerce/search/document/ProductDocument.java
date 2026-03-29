package com.ecommerce.search.document;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Document(indexName = "products", createIndex = false)
@Setting(settingPath = "elasticsearch/product-settings.json")
@Mapping(mappingPath = "elasticsearch/product-mapping.json")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class ProductDocument {

    @Id
    private String id;           // UUID as string — Elasticsearch ID

    private String productId;    // Redundant but useful for cross-referencing
    private String sku;
    private String name;

    private String nameAutocomplete;

    private String description;
    private String brand;
    private String categoryId;
    private String categoryName;
    private String categorySlug;

    private Long pricePaise;
    private Double priceRupees;
    private Long mrpPaise;
    private Integer discountPercent;

    @Field(type = FieldType.Keyword)
    private String status;

    private Double averageRating;
    private Integer totalReviews;

    @Field(type = FieldType.Keyword)
    private List<String> tags;

    @Field(type = FieldType.Flattened)
    private java.util.Map<String, List<String>> attributes;

    private String primaryImageUrl;

    private Double popularityScore;

    private Boolean inStock;
    private Integer stockQuantity;
    private Boolean isDigital;
    private String warehouseId;

    @Field(type = FieldType.Date)
    private Instant publishedAt;

    @Field(type = FieldType.Date)
    private Instant indexedAt;

    @Field(type = FieldType.Date)
    private Instant updatedAt;
}
