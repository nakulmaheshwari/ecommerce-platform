package com.ecommerce.cart.domain;

import com.ecommerce.cart.domain.CartItem;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * CartRepository wraps all Redis operations.
 *
 * Redis data model:
 *   Key type: Hash
 *   Key:      "cart:{userId}"
 *   Field:    "{skuId}"
 *   Value:    JSON string of CartItem
 *   TTL:      7 days (reset on every write)
 *
 * Operations used:
 *   HSET   — add or update one item (O(1))
 *   HGET   — get one item (O(1))
 *   HDEL   — remove one item (O(1))
 *   HGETALL — get all items in cart (O(N) where N = number of items)
 *   HLEN   — count items (O(1))
 *   DEL    — clear entire cart (O(1))
 *   EXPIRE — reset TTL (O(1))
 *
 * All these are O(1) or O(N items in cart), never O(N total carts).
 * This is why Redis is right for carts and Postgres is wrong.
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class CartRepository {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper cartObjectMapper;

    private static final String KEY_PREFIX = "cart:";
    // Cart expires 7 days after last activity
    private static final Duration CART_TTL = Duration.ofDays(7);
    // Max items per cart — prevent abuse
    private static final int MAX_CART_SIZE = 50;

    // ─────────────────────────────────────────────
    // WRITE OPERATIONS
    // ─────────────────────────────────────────────

    public void saveItem(UUID userId, CartItem item) {
        String key = cartKey(userId);

        // Enforce max cart size
        Long currentSize = redisTemplate.opsForHash().size(key);
        boolean isNewItem = !redisTemplate.opsForHash().hasKey(key, item.getSkuId());
        if (isNewItem && currentSize != null && currentSize >= MAX_CART_SIZE) {
            throw new IllegalStateException(
                "Cart is full. Maximum " + MAX_CART_SIZE + " items allowed.");
        }

        try {
            String serialized = cartObjectMapper.writeValueAsString(item);
            // HSET cart:{userId} {skuId} {json}
            redisTemplate.opsForHash().put(key, item.getSkuId(), serialized);
            // Reset TTL on every modification — 7 days from last activity
            redisTemplate.expire(key, CART_TTL);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize cart item", e);
        }
    }

    public void removeItem(UUID userId, String skuId) {
        String key = cartKey(userId);
        // HDEL cart:{userId} {skuId}
        redisTemplate.opsForHash().delete(key, skuId);
        redisTemplate.expire(key, CART_TTL); // Reset TTL even on delete
    }

    public void clearCart(UUID userId) {
        // DEL cart:{userId} — removes the entire hash in one command
        redisTemplate.delete(cartKey(userId));
        log.info("Cart cleared for userId={}", userId);
    }

    // ─────────────────────────────────────────────
    // READ OPERATIONS
    // ─────────────────────────────────────────────

    public List<CartItem> getItems(UUID userId) {
        // HGETALL cart:{userId} — returns all field-value pairs
        Map<Object, Object> entries = redisTemplate.opsForHash()
            .entries(cartKey(userId));

        if (entries.isEmpty()) return List.of();

        return entries.values().stream()
            .map(val -> {
                try {
                    return cartObjectMapper.readValue(
                        (String) val, CartItem.class);
                } catch (JsonProcessingException e) {
                    log.error("Corrupt cart item, skipping: {}", val);
                    return null; // Corrupt entries are skipped, not crashed on
                }
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    public Optional<CartItem> getItem(UUID userId, String skuId) {
        Object val = redisTemplate.opsForHash().get(cartKey(userId), skuId);
        if (val == null) return Optional.empty();
        try {
            return Optional.of(
                cartObjectMapper.readValue((String) val, CartItem.class));
        } catch (JsonProcessingException e) {
            log.error("Corrupt cart item skuId={}", skuId);
            return Optional.empty();
        }
    }

    public boolean exists(UUID userId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(cartKey(userId)));
    }

    public long itemCount(UUID userId) {
        return redisTemplate.opsForHash().size(cartKey(userId));
    }

    private String cartKey(UUID userId) {
        return KEY_PREFIX + userId.toString();
    }
}
