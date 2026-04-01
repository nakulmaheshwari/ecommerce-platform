package com.ecommerce.recommendation;

import com.ecommerce.recommendation.api.dto.RecommendationResponse;
import com.ecommerce.recommendation.client.ProductCatalogClient.ProductDto;
import com.ecommerce.recommendation.observability.RecommendationMetrics;
import com.ecommerce.recommendation.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock PersonalisationService    personalisationService;
    @Mock ItemRecommendationService itemRecommendationService;
    @Mock TrendingService           trendingService;
    @Mock ProductHydrationService   hydrationService;
    @Mock RecommendationMetrics     metrics;

    @InjectMocks RecommendationService recommendationService;

    static final UUID   USER_ID    = UUID.randomUUID();
    static final String P1 = UUID.randomUUID().toString();
    static final String P2 = UUID.randomUUID().toString();
    static final String P3 = UUID.randomUUID().toString();
    static final String P4 = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        lenient().when(hydrationService.hydrate(anyList()))
            .thenAnswer(inv -> {
                List<String> ids = inv.getArgument(0);
                return ids.stream()
                    .map(id -> new ProductDto(UUID.fromString(id), null, "Product",
                        null, null, null, null, null, 100000L, 1000.0, 120000L,
                        17, 4.2, 100, true, false, null, "ACTIVE"))
                    .toList();
            });
        lenient().when(hydrationService.filterActive(anyList()))
            .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("ALS has data → served directly, no trending fallback")
    void alsHasData_noFallback() {
        when(personalisationService.getPersonalRecommendations(USER_ID, 20, true))
            .thenReturn(List.of(P1, P2, P3, P4));

        RecommendationResponse r = recommendationService
            .getUserRecommendations(USER_ID, 20, true, null, "control");

        assertThat(r.recommendations()).hasSize(4);
        assertThat(r.algorithm()).isEqualTo("ALS_PERSONAL");
        verify(trendingService, never()).getGlobalTrending(anyInt(), anyInt());
    }

    @Test
    @DisplayName("ALS empty → falls back to trending")
    void alsEmpty_fallsToTrending() {
        when(personalisationService.getPersonalRecommendations(USER_ID, 20, true))
            .thenReturn(List.of());
        when(trendingService.getGlobalTrending(anyInt(), anyInt()))
            .thenReturn(List.of(P1, P2, P3, P4));

        RecommendationResponse r = recommendationService
            .getUserRecommendations(USER_ID, 20, true, null, "control");

        assertThat(r.recommendations()).isNotEmpty();
        verify(trendingService).getGlobalTrending(anyInt(), anyInt());
        verify(metrics).recordFallback("user-personal-trending-pad");
    }

    @Test
    @DisplayName("ALS + trending overlap → P2 appears only once")
    void deduplicationWorks() {
        when(personalisationService.getPersonalRecommendations(USER_ID, 20, true))
            .thenReturn(List.of(P1, P2));
        when(trendingService.getGlobalTrending(anyInt(), anyInt()))
            .thenReturn(List.of(P2, P3)); // P2 in both

        RecommendationResponse r = recommendationService
            .getUserRecommendations(USER_ID, 20, true, null, "control");

        long p2count = r.recommendations().stream()
            .filter(i -> P2.equals(i.productId())).count();
        assertThat(p2count).isEqualTo(1);
    }

    @Test
    @DisplayName("Rank 1 has score 1.0, ranks decrease monotonically")
    void rankOneHasHighestScore() {
        when(personalisationService.getPersonalRecommendations(USER_ID, 5, true))
            .thenReturn(List.of(P1, P2, P3));

        List<RecommendationResponse.RecommendationItem> items =
            recommendationService.getUserRecommendations(USER_ID, 5, true, null, "control")
                .recommendations();

        assertThat(items.get(0).rank()).isEqualTo(1);
        assertThat(items.get(0).score()).isEqualTo(1.0);
        if (items.size() > 1)
            assertThat(items.get(0).score()).isGreaterThan(items.get(1).score());
    }

    @Test
    @DisplayName("Every item has a unique recommendationId for click tracking")
    void everyItemHasUniqueTrackingId() {
        when(personalisationService.getPersonalRecommendations(USER_ID, 10, true))
            .thenReturn(List.of(P1, P2, P3));

        Set<String> ids = new HashSet<>();
        recommendationService.getUserRecommendations(USER_ID, 10, true, null, "control")
            .recommendations()
            .forEach(i -> ids.add(i.recommendationId()));

        assertThat(ids).hasSize(3);
    }

    @Test
    @DisplayName("Also-bought insufficient → backfills with content-based")
    void alsoBoughtBackfillsWithContent() {
        when(itemRecommendationService.getAlsoBought(anyString(), anyInt()))
            .thenReturn(List.of(P1)); // only 1, less than MIN_RESULTS=4
        when(itemRecommendationService.getSimilarProducts(anyString(), anyInt()))
            .thenReturn(List.of(P2, P3, P4));

        RecommendationResponse r = recommendationService
            .getAlsoBought(UUID.randomUUID().toString(), 10);

        List<String> productIds = r.recommendations().stream()
            .map(RecommendationResponse.RecommendationItem::productId).toList();

        assertThat(productIds).contains(P1, P2, P3, P4);
    }

    @Test
    @DisplayName("All services empty → empty response, no NPE")
    void allEmpty_noException() {
        when(personalisationService.getPersonalRecommendations(any(), anyInt(), anyBoolean()))
            .thenReturn(List.of());
        when(trendingService.getGlobalTrending(anyInt(), anyInt()))
            .thenReturn(List.of());
        when(hydrationService.hydrate(List.of())).thenReturn(List.of());
        when(hydrationService.filterActive(List.of())).thenReturn(List.of());

        RecommendationResponse r = recommendationService
            .getUserRecommendations(USER_ID, 20, true, null, "control");

        assertThat(r).isNotNull();
        assertThat(r.recommendations()).isEmpty();
        assertThat(r.totalCount()).isZero();
    }
}
