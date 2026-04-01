package com.ecommerce.recommendation.domain;

import lombok.*;
import java.io.Serializable;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class UserRecommendationId implements Serializable {
    private UUID  userId;
    private Short rank;
}
