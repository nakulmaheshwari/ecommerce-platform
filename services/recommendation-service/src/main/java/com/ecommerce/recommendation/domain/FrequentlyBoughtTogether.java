package com.ecommerce.recommendation.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "frequently_bought_together",
       indexes = {
           @Index(name = "idx_fbt_a", columnList = "product_a_id, confidence DESC")
       })
@IdClass(FbtId.class)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class FrequentlyBoughtTogether {

    @Id
    @Column(name = "product_a_id", nullable = false)
    private UUID productAId;

    @Id
    @Column(name = "product_b_id", nullable = false)
    private UUID productBId;

    @Column(name = "co_occurrence", nullable = false)
    private Integer coOccurrence;

    // P(B|A) — probability a buyer of A also buys B
    @Column(name = "confidence", nullable = false, precision = 5, scale = 4)
    private BigDecimal confidence;

    // Values > 1.0 = non-random association. We filter < 1.2 out.
    @Column(name = "lift", nullable = false, precision = 6, scale = 4)
    private BigDecimal lift;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;
}
