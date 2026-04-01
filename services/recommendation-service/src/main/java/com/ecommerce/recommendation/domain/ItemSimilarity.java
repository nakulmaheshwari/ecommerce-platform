package com.ecommerce.recommendation.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "item_similarities",
       indexes = {
           @Index(name = "idx_similarities_source",
                  columnList = "source_product_id, similarity_score DESC")
       })
@IdClass(ItemSimilarityId.class)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ItemSimilarity {

    @Id
    @Column(name = "source_product_id", nullable = false)
    private UUID sourceProductId;

    @Id
    @Column(name = "target_product_id", nullable = false)
    private UUID targetProductId;

    @Id
    @Column(name = "algorithm", nullable = false, length = 30)
    private String algorithm;

    @Column(name = "similarity_score", nullable = false, precision = 6, scale = 4)
    private BigDecimal similarityScore;

    @Column(name = "co_occurrence", nullable = false)
    private Integer coOccurrence;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;
}
