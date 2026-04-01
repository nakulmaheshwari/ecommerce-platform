package com.ecommerce.recommendation.config;

import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.Map;

/*
 * Single source of truth for event→weight mapping.
 * MUST match what the nightly Spark training jobs use.
 * If you change a weight here, update the Spark job too.
 */
@Component
public class EventSignalWeights {

    public static final Map<String, BigDecimal> WEIGHTS = Map.of(
        "order_placed",               new BigDecimal("3.0"),
        "wishlist_added",             new BigDecimal("0.8"),
        "product_added_to_cart",      new BigDecimal("0.7"),
        "search_result_clicked",      new BigDecimal("0.5"),
        "product_detail_dwell",       new BigDecimal("0.5"),
        "product_viewed",             new BigDecimal("0.3"),
        "search_performed",           new BigDecimal("0.2"),
        "product_removed_from_cart",  new BigDecimal("-0.3")
    );

    public static final BigDecimal DEFAULT_WEIGHT = new BigDecimal("0.1");

    // 5★ = +1.0, 3★ = 0.0, 1★ = -1.0
    public static BigDecimal reviewRatingToPreference(int rating) {
        return BigDecimal.valueOf((rating - 3) / 2.0);
    }

    public static BigDecimal weightOf(String eventType) {
        return WEIGHTS.getOrDefault(eventType, DEFAULT_WEIGHT);
    }
}
