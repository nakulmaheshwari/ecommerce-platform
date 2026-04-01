package com.ecommerce.recommendation.service;

import com.ecommerce.recommendation.config.RedisKeys;
import com.ecommerce.recommendation.observability.RecommendationMetrics;
import com.ecommerce.recommendation.repository.FbtRepository;
import com.ecommerce.recommendation.repository.ItemSimilarityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ItemRecommendationService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ItemSimilarityRepository      similarityRepository;
    private final FbtRepository                 fbtRepository;
    private final RecommendationMetrics         metrics;

    @Value("${recommendation.cache.ttl.product-similar:86400}")
    private long productSimilarTtlSeconds;

    @Value("${recommendation.cache.ttl.product-fbt:86400}")
    private long productFbtTtlSeconds;

    @Value("${recommendation.cache.ttl.product-also-bought:86400}")
    private long productAlsoBoughtTtlSeconds;

    public List<String> getAlsoBought(String productId, int limit) {
        return readFromCache(RedisKeys.productAlsoBought(productId),
            productId, "I2I_CF", productAlsoBoughtTtlSeconds, limit, "also-bought");
    }

    public List<String> getFrequentlyBoughtTogether(String productId, int limit) {
        return readFromCache(RedisKeys.productFbt(productId),
            productId, "FBT", productFbtTtlSeconds, limit, "fbt");
    }

    public List<String> getSimilarProducts(String productId, int limit) {
        return readFromCache(RedisKeys.productSimilar(productId),
            productId, "CONTENT", productSimilarTtlSeconds, limit, "similar");
    }

    /*
     * Cart cross-sell: for each cart item, fetch FBT recs.
     * Merge by aggregated score (recommended by multiple items = higher score).
     * Exclude products already in cart.
     * Score decays with position: rank 1 = 1.0, rank 2 = 0.95, etc.
     */
    public List<String> getCartCrossSell(List<String> cartProductIds, int limit) {
        if (cartProductIds == null || cartProductIds.isEmpty()) return List.of();

        Set<String> cartSet = new HashSet<>(cartProductIds);
        Map<String, Double> candidateScores = new HashMap<>();

        for (String cartProductId : cartProductIds) {
            List<String> fbtItems = redisTemplate.opsForList()
                .range(RedisKeys.productFbt(cartProductId), 0, 19);

            if (fbtItems != null) {
                for (int i = 0; i < fbtItems.size(); i++) {
                    String candidate = fbtItems.get(i);
                    if (!cartSet.contains(candidate)) {
                        double positionScore = 1.0 - (i * 0.05);
                        candidateScores.merge(candidate, positionScore, Double::sum);
                    }
                }
            }
        }

        return candidateScores.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(limit)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }

    public void storeItemSimilarities(String productId, String algorithm,
                                       List<String> targetIds) {
        String key = switch (algorithm) {
            case "I2I_CF"  -> RedisKeys.productAlsoBought(productId);
            case "FBT"     -> RedisKeys.productFbt(productId);
            case "CONTENT" -> RedisKeys.productSimilar(productId);
            default -> throw new IllegalArgumentException("Unknown: " + algorithm);
        };
        long ttl = switch (algorithm) {
            case "I2I_CF"  -> productAlsoBoughtTtlSeconds;
            case "FBT"     -> productFbtTtlSeconds;
            default        -> productSimilarTtlSeconds;
        };

        redisTemplate.executePipelined(
            (org.springframework.data.redis.connection.RedisConnection conn) -> {
                conn.del(key.getBytes());
                if (!targetIds.isEmpty()) {
                    byte[][] vals = targetIds.stream()
                        .map(String::getBytes).toArray(byte[][]::new);
                    conn.rPush(key.getBytes(), vals);
                }
                conn.expire(key.getBytes(), ttl);
                return null;
            });
    }

    // Invalidate content-based cache when product attributes change
    public void invalidateProductCache(String productId) {
        redisTemplate.delete(RedisKeys.productSimilar(productId));
        log.info("Content similarity cache invalidated productId={}", productId);
    }

    private List<String> readFromCache(String redisKey, String productId,
                                        String algorithm, long ttlSeconds,
                                        int limit, String metricTag) {
        List<String> cached = redisTemplate.opsForList().range(redisKey, 0, limit - 1);
        if (cached != null && !cached.isEmpty()) {
            metrics.recordCacheHit(metricTag);
            return cached.stream().limit(limit).collect(Collectors.toList());
        }

        metrics.recordFallback(metricTag);

        try {
            UUID productUuid = UUID.fromString(productId);
            List<String> dbResults;

            if ("FBT".equals(algorithm)) {
                dbResults = fbtRepository
                    .findTopByProductA(productUuid, PageRequest.of(0, limit))
                    .stream().map(f -> f.getProductBId().toString()).collect(Collectors.toList());
            } else {
                dbResults = similarityRepository
                    .findTopByProductAndAlgorithm(productUuid, algorithm, PageRequest.of(0, limit))
                    .stream().map(s -> s.getTargetProductId().toString()).collect(Collectors.toList());
            }

            if (!dbResults.isEmpty()) storeItemSimilarities(productId, algorithm, dbResults);
            return dbResults;

        } catch (Exception e) {
            log.error("DB fallback failed for {} productId={}", metricTag, productId, e);
            return List.of();
        }
    }
}
