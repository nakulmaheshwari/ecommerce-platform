package com.ecommerce.cart.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * CartItem is a SNAPSHOT of the product at the time it was added.
 *
 * Why snapshot and not live product data?
 *
 * Option A (live data): Store only skuId, fetch product on every cart read.
 * Problem: If the product price changes while the item is in the cart,
 * the displayed price changes. Customer adds item for ₹999, comes back
 * the next day to find it's ₹1299. This is legally problematic and
 * creates a bad UX. Also, N+1 problem: 10 cart items = 10 product service calls.
 *
 * Option B (snapshot): Store all display data at add-time. Price shown
 * is always what they saw when they added it.
 * Problem: Price can become stale. Fix: re-validate at checkout time
 * against live inventory+price, show a "price changed" warning.
 *
 * Real companies (Amazon, Flipkart) use the snapshot approach with
 * checkout-time re-validation. That's what we implement here.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CartItem implements Serializable {

    private String skuId;           // Partition key within the cart hash
    private UUID productId;
    private String productName;
    private String variantName;     // "128GB - Black"
    private int quantity;
    private long pricePaise;        // Price snapshot at time of add
    private long mrpPaise;          // MRP snapshot — to show discount
    private String imageUrl;        // Primary image — avoids product service call on display
    private String brand;

    // JSONB-style attributes for display: {"color": "Black", "storage": "128GB"}
    private Map<String, String> attributes;

    private Instant addedAt;
    private Instant updatedAt;

    // Computed — not stored in Redis
    public long totalPaise() {
        return (long) quantity * pricePaise;
    }

    public int discountPercent() {
        if (mrpPaise == 0) return 0;
        return (int) ((mrpPaise - pricePaise) * 100 / mrpPaise);
    }
}
