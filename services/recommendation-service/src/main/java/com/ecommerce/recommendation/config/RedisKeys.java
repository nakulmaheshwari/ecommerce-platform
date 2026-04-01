package com.ecommerce.recommendation.config;

public final class RedisKeys {

    private RedisKeys() {}

    // Hash tag {userId} ensures all user keys land on the same Redis shard
    public static String userPersonal(String userId) {
        return "rec:{" + userId + "}:personal";
    }

    public static String userRecentlyViewed(String userId) {
        return "rec:{" + userId + "}:recently_viewed";
    }

    public static String userCategoryAffinity(String userId) {
        return "feat:{" + userId + "}:category_affinity";
    }

    public static String userBrandAffinity(String userId) {
        return "feat:{" + userId + "}:brand_affinity";
    }

    public static String userPriceAffinity(String userId) {
        return "feat:{" + userId + "}:price_affinity";
    }

    public static String productSimilar(String productId) {
        return "rec:product:" + productId + ":similar";
    }

    public static String productFbt(String productId) {
        return "rec:product:" + productId + ":fbt";
    }

    public static String productAlsoBought(String productId) {
        return "rec:product:" + productId + ":also_bought";
    }

    public static String productCounters(String productId) {
        return "feat:product:" + productId + ":counters";
    }

    public static final String TRENDING_GLOBAL_24H = "rec:trending:global:24h";

    public static String trendingCategory(String categoryId) {
        return "rec:trending:cat:" + categoryId + ":24h";
    }

    public static String sessionContext(String sessionId) {
        return "rec:session:" + sessionId + ":context";
    }

    public static String sessionRecs(String sessionId) {
        return "rec:session:" + sessionId + ":recs";
    }

    public static final String COUNTER_VIEWS     = "views";
    public static final String COUNTER_CARTS     = "carts";
    public static final String COUNTER_PURCHASES = "purchases";
}
