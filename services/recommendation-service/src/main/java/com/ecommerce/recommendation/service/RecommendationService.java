package com.ecommerce.recommendation.service;

import com.ecommerce.recommendation.api.dto.RecommendationResponse;
import com.ecommerce.recommendation.api.dto.RecommendationResponse.RecommendationItem;
import com.ecommerce.recommendation.client.ProductCatalogClient.ProductDto;
import com.ecommerce.recommendation.observability.RecommendationMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationService {

    private final PersonalisationService    personalisationService;
    private final ItemRecommendationService itemRecommendationService;
    private final TrendingService           trendingService;
    private final ProductHydrationService   hydrationService;
    private final RecommendationMetrics     metrics;

    private static final int MIN_RESULTS = 4;

    public RecommendationResponse getUserRecommendations(
            UUID userId, int limit, boolean excludeOwned,
            String sessionId, String experimentId) {

        long start = System.currentTimeMillis();
        int effectiveLimit = Math.min(limit, 50);
        String algorithm = resolveAlgorithm(experimentId);
        metrics.recordRequest("user-personal", algorithm);

        // Step 1: ALS personalised
        List<String> candidates = personalisationService
            .getPersonalRecommendations(userId, effectiveLimit, excludeOwned);
        boolean fromCache = !candidates.isEmpty();

        // Step 2: Pad with trending if insufficient
        if (candidates.size() < MIN_RESULTS) {
            List<String> trending = trendingService
                .getGlobalTrending(effectiveLimit - candidates.size(), 0);
            candidates = mergeDeduped(candidates, trending, effectiveLimit);
            metrics.recordFallback("user-personal-trending-pad");
        }

        List<ProductDto> products = hydrationService.filterActive(hydrationService.hydrate(candidates));
        return buildResponse(toItems(products, "USER_PERSONALISED"),
            algorithm, experimentId, fromCache, System.currentTimeMillis() - start);
    }

    public RecommendationResponse getAlsoBought(String productId, int limit) {
        long start = System.currentTimeMillis();
        List<String> ids = itemRecommendationService.getAlsoBought(productId, limit);

        if (ids.size() < MIN_RESULTS) {
            List<String> similar = itemRecommendationService.getSimilarProducts(productId, limit);
            ids = mergeDeduped(ids, similar, limit);
        }

        return buildResponse(
            toItems(hydrationService.filterActive(hydrationService.hydrate(ids)), "ALSO_BOUGHT"),
            "I2I_CF", null, !ids.isEmpty(), System.currentTimeMillis() - start);
    }

    public RecommendationResponse getFrequentlyBoughtTogether(String productId, int limit) {
        long start = System.currentTimeMillis();
        List<String> ids = itemRecommendationService.getFrequentlyBoughtTogether(productId, limit);
        return buildResponse(
            toItems(hydrationService.filterActive(hydrationService.hydrate(ids)), "FBT"),
            "FBT", null, !ids.isEmpty(), System.currentTimeMillis() - start);
    }

    public RecommendationResponse getSimilarProducts(String productId, int limit) {
        long start = System.currentTimeMillis();
        List<String> ids = itemRecommendationService.getSimilarProducts(productId, limit);
        return buildResponse(
            toItems(hydrationService.filterActive(hydrationService.hydrate(ids)), "SIMILAR"),
            "CONTENT_BASED", null, !ids.isEmpty(), System.currentTimeMillis() - start);
    }

    public RecommendationResponse getGlobalTrending(int limit, int offset) {
        long start = System.currentTimeMillis();
        List<String> ids = trendingService.getGlobalTrending(limit, offset);
        return buildResponse(
            toItems(hydrationService.filterActive(hydrationService.hydrate(ids)), "TRENDING_GLOBAL"),
            "POPULARITY_DECAY", null, !ids.isEmpty(), System.currentTimeMillis() - start);
    }

    public RecommendationResponse getCategoryTrending(String categoryId, int limit) {
        long start = System.currentTimeMillis();
        List<String> ids = trendingService.getCategoryTrending(categoryId, limit);
        return buildResponse(
            toItems(hydrationService.filterActive(hydrationService.hydrate(ids)), "TRENDING_CATEGORY"),
            "POPULARITY_DECAY", null, !ids.isEmpty(), System.currentTimeMillis() - start);
    }

    public RecommendationResponse getCartCrossSell(List<String> cartProductIds,
                                                    UUID userId, int limit) {
        long start = System.currentTimeMillis();
        List<String> ids = itemRecommendationService.getCartCrossSell(cartProductIds, limit);

        if (ids.isEmpty() && userId != null) {
            ids = personalisationService.getPersonalRecommendations(userId, limit, true);
        }

        return buildResponse(
            toItems(hydrationService.filterActive(hydrationService.hydrate(ids)), "CART_CROSS_SELL"),
            "FBT_CART", null, !ids.isEmpty(), System.currentTimeMillis() - start);
    }

    public RecommendationResponse getRecentlyViewed(String userId, int limit) {
        long start = System.currentTimeMillis();
        List<String> ids = personalisationService.getRecentlyViewed(userId, limit);
        return buildResponse(toItems(hydrationService.hydrate(ids), "RECENTLY_VIEWED"),
            "RECENCY", null, !ids.isEmpty(), System.currentTimeMillis() - start);
    }

    public Map<String, Double> getAffinityScores(UUID userId,
                                                   Map<String, PersonalisationService.ProductAttributes> productMap) {
        return personalisationService.getAffinityScores(userId, productMap);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private List<String> mergeDeduped(List<String> primary, List<String> secondary, int max) {
        Set<String> seen = new LinkedHashSet<>(primary);
        seen.addAll(secondary);
        return seen.stream().limit(max).collect(Collectors.toList());
    }

    // Rank 1 = score 1.0, rank N = 0.05 minimum. Each item gets unique tracking ID.
    private List<RecommendationItem> toItems(List<ProductDto> products, String type) {
        List<RecommendationItem> items = new ArrayList<>();
        for (int i = 0; i < products.size(); i++) {
            double score = Math.max(0.05, 1.0 - (i * 0.05));
            items.add(new RecommendationItem(
                products.get(i).productId().toString(),
                i + 1, score, type,
                UUID.randomUUID().toString(),
                products.get(i)
            ));
        }
        return items;
    }

    private String resolveAlgorithm(String experimentId) {
        if (experimentId == null) return "ALS_PERSONAL";
        return switch (experimentId) {
            case "hybrid_v1"     -> "ALS_I2I_HYBRID";
            case "content_boost" -> "ALS_CONTENT_HYBRID";
            default              -> "ALS_PERSONAL";
        };
    }

    private RecommendationResponse buildResponse(List<RecommendationItem> items,
                                                   String algorithm, String experimentId,
                                                   boolean fromCache, long tookMs) {
        metrics.recordLatency(algorithm, tookMs);
        if (!items.isEmpty()) metrics.recordImpression(algorithm, items.size());

        return new RecommendationResponse(items, algorithm, experimentId,
            items.size(), 0, items.size(), fromCache, tookMs,
            UUID.randomUUID().toString(), Instant.now());
    }
}
