package com.ecommerce.recommendation.service;

import com.ecommerce.recommendation.config.RedisKeys;
import com.ecommerce.recommendation.observability.RecommendationMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrendingService {

    private final RedisTemplate<String, String> redisTemplate;
    private final RecommendationMetrics         metrics;

    @Value("${recommendation.limits.trending-max:100}")
    private int trendingMax;

    @Cacheable(value = "trendingRecs", key = "'global:' + #limit + ':' + #offset")
    public List<String> getGlobalTrending(int limit, int offset) {
        Set<ZSetOperations.TypedTuple<String>> results = redisTemplate
            .opsForZSet()
            .reverseRangeWithScores(RedisKeys.TRENDING_GLOBAL_24H, offset, offset + limit - 1);

        if (results == null || results.isEmpty()) {
            log.warn("Trending data not in Redis — Flink job may be behind");
            metrics.recordFallback("trending-global");
            return List.of();
        }

        metrics.recordCacheHit("trending-global");
        return results.stream()
            .map(ZSetOperations.TypedTuple::getValue)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    @Cacheable(value = "trendingRecs", key = "'cat:' + #categoryId + ':' + #limit")
    public List<String> getCategoryTrending(String categoryId, int limit) {
        Set<ZSetOperations.TypedTuple<String>> results = redisTemplate
            .opsForZSet()
            .reverseRangeWithScores(RedisKeys.trendingCategory(categoryId), 0, limit - 1);

        if (results == null || results.isEmpty()) {
            log.debug("No category trending for {}, falling back to global", categoryId);
            metrics.recordFallback("trending-category");
            return getGlobalTrending(limit, 0);
        }

        return results.stream()
            .map(ZSetOperations.TypedTuple::getValue)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    // Called by Flink consumer and UserActivityConsumer
    public void incrementTrendingScore(String productId, double delta, String categoryId) {
        redisTemplate.opsForZSet()
            .incrementScore(RedisKeys.TRENDING_GLOBAL_24H, productId, delta);

        if (categoryId != null && !categoryId.isEmpty()) {
            redisTemplate.opsForZSet()
                .incrementScore(RedisKeys.trendingCategory(categoryId), productId, delta);
        }
    }

    // Purchases have 3x weight. Called by OrderEventConsumer.
    public void recordPurchaseForTrending(String productId, String categoryId) {
        double purchaseWeight = 3.0;
        redisTemplate.opsForZSet()
            .incrementScore(RedisKeys.TRENDING_GLOBAL_24H, productId, purchaseWeight);

        if (categoryId != null) {
            redisTemplate.opsForZSet()
                .incrementScore(RedisKeys.trendingCategory(categoryId), productId, purchaseWeight);
        }

        redisTemplate.opsForHash().increment(
            RedisKeys.productCounters(productId), RedisKeys.COUNTER_PURCHASES, 1);
    }
}
