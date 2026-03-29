package com.ecommerce.order.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "order_status_history")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class OrderStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    private OrderStatus fromStatus;  // NULL on initial creation

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus toStatus;

    private String reason;

    @Column(nullable = false)
    @Builder.Default
    private String actor = "system";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Builder.Default
    private Instant createdAt = Instant.now();

    // Factory for initial creation record
    public static OrderStatusHistory initial(UUID orderId, String actor) {
        return OrderStatusHistory.builder()
            .orderId(orderId)
            .fromStatus(null)
            .toStatus(OrderStatus.PENDING)
            .reason("Order created")
            .actor(actor)
            .build();
    }
}
