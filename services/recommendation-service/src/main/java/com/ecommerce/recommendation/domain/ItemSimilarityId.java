package com.ecommerce.recommendation.domain;

import lombok.*;
import java.io.Serializable;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class ItemSimilarityId implements Serializable {
    private UUID   sourceProductId;
    private UUID   targetProductId;
    private String algorithm;
}
