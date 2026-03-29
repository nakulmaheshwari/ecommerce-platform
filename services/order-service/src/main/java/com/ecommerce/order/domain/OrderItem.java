package com.ecommerce.order.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "order_items")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // order_id FK set by @JoinColumn on Order.items
    @Column(name = "order_id", insertable = false, updatable = false)
    private UUID orderId;

    @Column(nullable = false)
    private String skuId;

    @Column(nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private String productName;

    private String variantName;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private Long unitPricePaise;

    @Column(nullable = false)
    private Long mrpPaise;

    private String imageUrl;

    public Long lineTotalPaise() {
        return (long) quantity * unitPricePaise;
    }
}
