package com.ecommerce.recommendation.service;

import com.ecommerce.recommendation.config.RedisKeys;
import com.ecommerce.recommendation.observability.RecommendationMetrics;
import com.ecommerce.recommendation.repository.InteractionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PersonalisationService {

    private final RedisTemplate<String, String> redisTemplate;
    private final InteractionRepository         interactionRepository;
    private final RecommendationMetrics         metrics;

    @Value("${recommendation.cache.ttl.user-personal:21600}")
    private long userPersonalTtlSeconds;

    // ── ALS personalised recommendations ─────────────────────────────────────

    public List<String> getPersonalRecommendations(UUID userId, int limit,
                                                    boolean excludeOwned) {
        String key = RedisKeys.userPersonal(userId.toString());
        List<String> cached = redisTemplate.opsForList().range(key, 0, 49);

        if (cached != null && !cached.isEmpty()) {
            metrics.recordCacheHit("user-personal");
            return applyExclusions(cached, userId, excludeOwned, limit);
        }

        metrics.recordFallback("user-personal");
        log.debug("Redis miss for personalised recs userId={}", userId);
        return List.of(); // triggers trending fallback in orchestrator
    }

    public void storePersonalRecommendations(UUID userId, List<String> productIds) {
        String key = RedisKeys.userPersonal(userId.toString());
        redisTemplate.executePipelined(
            (org.springframework.data.redis.connection.RedisConnection conn) -> {
                conn.del(key.getBytes());
                if (!productIds.isEmpty()) {
                    byte[][] vals = productIds.stream()
                        .map(String::getBytes).toArray(byte[][]::new);
                    conn.lPush(key.getBytes(), vals);
                }
                conn.expire(key.getBytes(), userPersonalTtlSeconds);
                return null;
            });
    }

    // ── Recently viewed ───────────────────────────────────────────────────────

    // LPUSH + LREM dedup + LTRIM = most recent first, max 20, no duplicates
    public void recordProductView(String userId, String productId) {
        String key = RedisKeys.userRecentlyViewed(userId);
        redisTemplate.executePipelined(
            (org.springframework.data.redis.connection.RedisConnection conn) -> {
                conn.lRem(key.getBytes(), 0, productId.getBytes());
                conn.lPush(key.getBytes(), productId.getBytes());
                conn.lTrim(key.getBytes(), 0, 19);
                conn.expire(key.getBytes(), 2_592_000L); // 30 days
                return null;
            });
    }

    public List<String> getRecentlyViewed(String userId, int limit) {
        List<String> viewed = redisTemplate.opsForList()
            .range(RedisKeys.userRecentlyViewed(userId), 0, limit - 1);
        return viewed != null ? viewed : List.of();
    }

    // ── Affinity scores for Search reranking ──────────────────────────────────

    public Map<String, Double> getAffinityScores(UUID userId,
                                                  Map<String, ProductAttributes> productMap) {
        if (productMap.isEmpty()) return Map.of();

        String catKey   = RedisKeys.userCategoryAffinity(userId.toString());
        String brandKey = RedisKeys.userBrandAffinity(userId.toString());

        Map<Object, Object> catAffinity   = redisTemplate.opsForHash().entries(catKey);
        Map<Object, Object> brandAffinity = redisTemplate.opsForHash().entries(brandKey);

        Map<String, Double> scores = new HashMap<>();
        productMap.forEach((productId, attrs) -> {
            double catScore   = parseScore(catAffinity.get(attrs.categoryId()));
            double brandScore = parseScore(brandAffinity.get(attrs.brand()));
            scores.put(productId, Math.min(1.0, 0.6 * catScore + 0.4 * brandScore));
        });
        return scores;
    }

    // EWMA update (α=0.1) — slow learner, category tastes change slowly
    public void updateCategoryAffinity(String userId, String categoryId, double weight) {
        String key = RedisKeys.userCategoryAffinity(userId);
        Object current = redisTemplate.opsForHash().get(key, categoryId);
        double old = current != null ? Double.parseDouble(current.toString()) : 0.0;
        double updated = (1 - 0.1) * old + 0.1 * weight;
        redisTemplate.opsForHash().put(key, categoryId, String.valueOf(updated));
        redisTemplate.expire(key, Duration.ofDays(7));
    }

    // α=0.15 for brands — changes slightly faster than categories
    public void updateBrandAffinity(String userId, String brand, double weight) {
        String key = RedisKeys.userBrandAffinity(userId);
        Object current = redisTemplate.opsForHash().get(key, brand);
        double old = current != null ? Double.parseDouble(current.toString()) : 0.0;
        double updated = (1 - 0.15) * old + 0.15 * weight;
        redisTemplate.opsForHash().put(key, brand, String.valueOf(updated));
        redisTemplate.expire(key, Duration.ofDays(7));
    }

    // ── GDPR ─────────────────────────────────────────────────────────────────

    public void eraseUserData(UUID userId) {
        String uid = userId.toString();
        redisTemplate.delete(List.of(
            RedisKeys.userPersonal(uid),
            RedisKeys.userRecentlyViewed(uid),
            RedisKeys.userCategoryAffinity(uid),
            RedisKeys.userBrandAffinity(uid),
            RedisKeys.userPriceAffinity(uid)
        ));
        interactionRepository.deleteByUserId(userId);
        log.info("GDPR: recommendation data erased userId hash={}", userId.hashCode());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<String> applyExclusions(List<String> candidates, UUID userId,
                                          boolean excludeOwned, int limit) {
        if (!excludeOwned) return candidates.stream().limit(limit).collect(Collectors.toList());

        Set<UUID> purchased = new HashSet<>(interactionRepository.findPurchasedProductIds(userId));
        return candidates.stream()
            .filter(id -> !purchased.contains(UUID.fromString(id)))
            .limit(limit).collect(Collectors.toList());
    }

    private double parseScore(Object value) {
        if (value == null) return 0.0;
        try { return Double.parseDouble(value.toString()); }
        catch (NumberFormatException e) { return 0.0; }
    }

    public record ProductAttributes(String categoryId, String brand) {}
}
