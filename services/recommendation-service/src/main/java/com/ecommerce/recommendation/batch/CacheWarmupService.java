package com.ecommerce.recommendation.batch;

import com.ecommerce.recommendation.observability.RecommendationMetrics;
import com.ecommerce.recommendation.service.ItemRecommendationService;
import com.ecommerce.recommendation.service.PersonalisationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CacheWarmupService {

    private final JdbcTemplate               jdbcTemplate;
    private final PersonalisationService     personalisationService;
    private final ItemRecommendationService  itemRecommendationService;
    private final RecommendationMetrics      metrics;

    @Value("${recommendation.cache.warmup.batch-size:1000}")
    private int batchSize;

    @Value("${recommendation.cache.warmup.top-users:100000}")
    private int topUsers;

    // Runs at 4:30am daily — after Spark ALS job (3am-4am) completes
    @Scheduled(cron = "0 30 4 * * *")
    public void warmPersonalRecommendations() {
        log.info("Starting cache warmup for top {} users", topUsers);
        long start = System.currentTimeMillis();

        List<String> userIds = jdbcTemplate.queryForList(
            "SELECT user_id::text FROM user_product_interactions " +
            "WHERE occurred_at >= NOW() - INTERVAL '30 days' " +
            "GROUP BY user_id ORDER BY COUNT(*) DESC LIMIT ?",
            String.class, topUsers);

        for (int i = 0; i < userIds.size(); i += batchSize) {
            List<String> batch = userIds.subList(i, Math.min(i + batchSize, userIds.size()));

            for (String userId : batch) {
                try {
                    List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                        "SELECT product_id FROM user_recommendations " +
                        "WHERE user_id = ?::uuid AND expires_at > NOW() " +
                        "ORDER BY rank ASC LIMIT 50", userId);

                    List<String> ids = rows.stream()
                        .map(r -> r.get("product_id").toString())
                        .collect(Collectors.toList());

                    if (!ids.isEmpty())
                        personalisationService.storePersonalRecommendations(UUID.fromString(userId), ids);

                } catch (Exception e) {
                    log.warn("Warmup failed for userId={}", userId, e);
                }
            }

            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }

        log.info("Cache warmup complete: {} users in {}ms", userIds.size(), System.currentTimeMillis() - start);
        metrics.recordModelUpdate("ALS_PERSONAL");
    }

    // Runs at 3:15am — after Spark FBT + I2I jobs (2am-3am)
    @Scheduled(cron = "0 15 3 * * *")
    public void warmItemRecommendations() {
        List<String> productIds = jdbcTemplate.queryForList(
            "SELECT product_id::text FROM user_product_interactions " +
            "WHERE occurred_at >= NOW() - INTERVAL '7 days' " +
            "GROUP BY product_id ORDER BY COUNT(*) DESC LIMIT 50000",
            String.class);

        for (String productId : productIds) {
            // FBT
            List<Map<String, Object>> fbt = jdbcTemplate.queryForList(
                "SELECT product_b_id FROM frequently_bought_together " +
                "WHERE product_a_id = ?::uuid ORDER BY confidence DESC LIMIT 10", productId);
            if (!fbt.isEmpty()) {
                List<String> ids = fbt.stream().map(r -> r.get("product_b_id").toString()).collect(Collectors.toList());
                itemRecommendationService.storeItemSimilarities(productId, "FBT", ids);
            }

            // I2I CF
            List<Map<String, Object>> i2i = jdbcTemplate.queryForList(
                "SELECT target_product_id FROM item_similarities " +
                "WHERE source_product_id = ?::uuid AND algorithm = 'I2I_CF' " +
                "ORDER BY similarity_score DESC LIMIT 20", productId);
            if (!i2i.isEmpty()) {
                List<String> ids = i2i.stream().map(r -> r.get("target_product_id").toString()).collect(Collectors.toList());
                itemRecommendationService.storeItemSimilarities(productId, "I2I_CF", ids);
            }
        }

        log.info("Item rec warmup complete for {} products", productIds.size());
    }

    // Cleanup expired rows from PostgreSQL at 5am
    @Scheduled(cron = "0 0 5 * * *")
    public void cleanupExpired() {
        int deleted = jdbcTemplate.update(
            "DELETE FROM user_recommendations WHERE expires_at < NOW()");
        log.info("Cleaned {} expired user recommendation rows", deleted);
    }
}
