package com.ecommerce.common.event;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.UUID;

@Getter
@SuperBuilder
@NoArgsConstructor
@JsonTypeName("order.placed")
public class OrderPlacedEvent extends DomainEvent {
    public static final String TOPIC = "order-placed";
    public static final String VERSION = "1.0";

    private UUID orderId;
    private UUID userId;
    private Long totalPaise;
    private String currency;
    private List<OrderItemSnapshot> items;

    public record OrderItemSnapshot(
        String skuId,
        UUID productId,
        String productName,
        Integer quantity,
        Long unitPricePaise
    ) {}
}
