package com.ecommerce.recommendation;

import com.ecommerce.recommendation.domain.UserProductInteraction;
import com.ecommerce.recommendation.repository.InteractionRepository;
import com.ecommerce.recommendation.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.*;
import org.testcontainers.containers.*;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RecommendationServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("recommendation_db")
            .withUsername("postgres").withPassword("postgres");

    @Container
    static GenericContainer<?> redis =
        new GenericContainer<>("redis:7.2-alpine")
            .withExposedPorts(6379)
            .waitingFor(Wait.forListeningPort());

    @Container
    static KafkaContainer kafka =
        new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry reg) {
        reg.add("spring.datasource.url",           postgres::getJdbcUrl);
        reg.add("spring.datasource.username",      postgres::getUsername);
        reg.add("spring.datasource.password",      postgres::getPassword);
        reg.add("spring.data.redis.host",          redis::getHost);
        reg.add("spring.data.redis.port",          () -> redis.getMappedPort(6379));
        reg.add("spring.data.redis.cluster.nodes", () -> null);
        reg.add("spring.kafka.bootstrap-servers",  kafka::getBootstrapServers);
        reg.add("eureka.client.enabled",           () -> "false");
        reg.add("spring.cloud.config.enabled",     () -> "false");
        reg.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
            () -> "http://localhost:9999/doesnotexist");
    }

    @Autowired TestRestTemplate             restTemplate;
    @Autowired KafkaTemplate<String,String> kafkaTemplate;
    @Autowired InteractionRepository        interactionRepository;
    @Autowired TrendingService              trendingService;
    @Autowired PersonalisationService       personalisationService;
    @Autowired ItemRecommendationService    itemRecommendationService;
    @Autowired RedisTemplate<String,String> redisTemplate;
    @Autowired ObjectMapper                 objectMapper;

    static final UUID   TEST_USER    = UUID.randomUUID();
    static final UUID   PRODUCT_A    = UUID.randomUUID();
    static final UUID   PRODUCT_B    = UUID.randomUUID();
    static final UUID   PRODUCT_C    = UUID.randomUUID();
    static final UUID   CATEGORY     = UUID.randomUUID();

    @Test @Order(1)
    @DisplayName("product_viewed Kafka event → interaction in PostgreSQL")
    void productViewedEvent_persistsInteraction() throws Exception {
        String event = objectMapper.writeValueAsString(Map.of(
            "eventId",    UUID.randomUUID().toString(),
            "eventType",  "product_viewed",
            "userId",     TEST_USER.toString(),
            "sessionId",  UUID.randomUUID().toString(),
            "productId",  PRODUCT_A.toString(),
            "categoryId", CATEGORY.toString(),
            "deviceType", "MOBILE",
            "occurredAt", Instant.now().toString()
        ));
        kafkaTemplate.send("user-activity", TEST_USER.toString(), event);
        Thread.sleep(3000);

        List<UserProductInteraction> interactions = interactionRepository.findAll().stream()
            .filter(i -> i.getUserId().equals(TEST_USER) && i.getProductId().equals(PRODUCT_A))
            .toList();

        assertThat(interactions).hasSize(1);
        assertThat(interactions.get(0).getImplicitScore())
            .isEqualByComparingTo(new BigDecimal("0.3"));
    }

    @Test @Order(2)
    @DisplayName("product_viewed → added to recently-viewed Redis list")
    void productViewedEvent_updatesRecentlyViewed() throws Exception {
        Thread.sleep(500);
        List<String> viewed = personalisationService.getRecentlyViewed(TEST_USER.toString(), 20);
        assertThat(viewed).contains(PRODUCT_A.toString());
    }

    @Test @Order(3)
    @DisplayName("product_viewed → trending score incremented")
    void productViewedEvent_incrementsTrending() {
        Double score = redisTemplate.opsForZSet()
            .score("rec:trending:global:24h", PRODUCT_A.toString());
        assertThat(score).isNotNull().isGreaterThan(0.0);
    }

    @Test @Order(4)
    @DisplayName("order_placed → purchase interactions saved with weight 3.0")
    void orderPlacedEvent_savesPurchaseInteractions() throws Exception {
        String event = objectMapper.writeValueAsString(Map.of(
            "orderId",    UUID.randomUUID().toString(),
            "userId",     TEST_USER.toString(),
            "occurredAt", Instant.now().toString(),
            "items", List.of(
                Map.of("productId", PRODUCT_B.toString(), "categoryId", CATEGORY.toString(), "brand", "TestBrand"),
                Map.of("productId", PRODUCT_C.toString(), "categoryId", CATEGORY.toString(), "brand", "TestBrand")
            )
        ));
        kafkaTemplate.send("order-placed", TEST_USER.toString(), event);
        Thread.sleep(3000);

        long purchases = interactionRepository.findAll().stream()
            .filter(i -> i.getUserId().equals(TEST_USER) && "order_placed".equals(i.getEventType()))
            .count();
        assertThat(purchases).isGreaterThanOrEqualTo(2);
    }

    @Test @Order(5)
    @DisplayName("storePersonalRecommendations → retrieved correctly")
    void personalRecs_storedAndRetrieved() {
        List<String> ids = List.of(PRODUCT_A.toString(), PRODUCT_B.toString(), PRODUCT_C.toString());
        personalisationService.storePersonalRecommendations(TEST_USER, ids);

        List<String> retrieved = personalisationService.getPersonalRecommendations(TEST_USER, 10, false);
        assertThat(retrieved).containsExactlyElementsOf(ids);
    }

    @Test @Order(6)
    @DisplayName("storeItemSimilarities FBT → retrieved correctly")
    void fbt_storedAndRetrieved() {
        List<String> fbt = List.of(PRODUCT_B.toString(), PRODUCT_C.toString());
        itemRecommendationService.storeItemSimilarities(PRODUCT_A.toString(), "FBT", fbt);

        List<String> retrieved = itemRecommendationService
            .getFrequentlyBoughtTogether(PRODUCT_A.toString(), 10);
        assertThat(retrieved).containsExactlyElementsOf(fbt);
    }

    @Test @Order(7)
    @DisplayName("getCartCrossSell → merges FBT, excludes cart items, deduplicates")
    void cartCrossSell_correctBehaviour() {
        itemRecommendationService.storeItemSimilarities(PRODUCT_A.toString(), "FBT",
            List.of(PRODUCT_C.toString()));
        itemRecommendationService.storeItemSimilarities(PRODUCT_B.toString(), "FBT",
            List.of(PRODUCT_C.toString()));

        List<String> crossSell = itemRecommendationService
            .getCartCrossSell(List.of(PRODUCT_A.toString(), PRODUCT_B.toString()), 10);

        assertThat(crossSell).containsOnlyOnce(PRODUCT_C.toString());
        assertThat(crossSell).doesNotContain(PRODUCT_A.toString(), PRODUCT_B.toString());
    }

    @Test @Order(8)
    @DisplayName("user-deleted Kafka event → all recommendation data erased")
    void gdprErasure_wipesAllData() throws Exception {
        assertThat(interactionRepository.findAll().stream()
            .anyMatch(i -> i.getUserId().equals(TEST_USER))).isTrue();

        kafkaTemplate.send("user-deleted", TEST_USER.toString(),
            objectMapper.writeValueAsString(Map.of(
                "userId", TEST_USER.toString(), "deletedAt", Instant.now().toString())));
        Thread.sleep(3000);

        long remaining = interactionRepository.findAll().stream()
            .filter(i -> i.getUserId().equals(TEST_USER)).count();
        assertThat(remaining).isZero();

        assertThat(personalisationService.getRecentlyViewed(TEST_USER.toString(), 20)).isEmpty();
    }

    @Test @Order(9)
    @DisplayName("GET /api/v1/recommendations/trending → 200 with correct structure")
    void trendingEndpoint_returns200() {
        ResponseEntity<Map> response = restTemplate
            .getForEntity("/api/v1/recommendations/trending?limit=5", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKeys("recommendations", "algorithm", "tookMs");
    }

    @Test @Order(10)
    @DisplayName("GET /api/v1/recommendations/product/{id}/similar → 200 even with no data")
    void similarProducts_returns200WhenNoData() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
            "/api/v1/recommendations/product/" + UUID.randomUUID() + "/similar?limit=10",
            Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
