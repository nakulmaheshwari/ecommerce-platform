package com.ecommerce.cart.domain;

import lombok.*;

import java.util.List;
import java.util.UUID;

/**
 * Cart is not an entity — it's never persisted as a whole object.
 * It's assembled from the Redis Hash on every read.
 *
 * This is the aggregate view returned to clients.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Cart {

    private UUID userId;
    private List<CartItem> items;

    // All computed from items — never stored
    public int totalItems() {
        return items.stream().mapToInt(CartItem::getQuantity).sum();
    }

    public long subtotalPaise() {
        return items.stream().mapToLong(CartItem::totalPaise).sum();
    }

    public long totalSavingsPaise() {
        return items.stream()
            .mapToLong(i -> (long)(i.getMrpPaise() - i.getPricePaise()) * i.getQuantity())
            .sum();
    }

    public boolean isEmpty() {
        return items == null || items.isEmpty();
    }
}
