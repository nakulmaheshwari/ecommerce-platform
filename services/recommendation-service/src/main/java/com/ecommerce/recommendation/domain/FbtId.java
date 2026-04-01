package com.ecommerce.recommendation.domain;

import lombok.*;
import java.io.Serializable;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class FbtId implements Serializable {
    private UUID productAId;
    private UUID productBId;
}
