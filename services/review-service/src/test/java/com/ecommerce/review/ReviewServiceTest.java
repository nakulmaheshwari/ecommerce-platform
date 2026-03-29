package com.ecommerce.review;

import com.ecommerce.review.api.dto.CreateReviewRequest;
import com.ecommerce.review.client.OrderServiceClient;
import com.ecommerce.review.repository.ReviewAggregateRepository;
import com.ecommerce.review.repository.ReviewRepository;
import com.ecommerce.review.service.ReviewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
class ReviewServiceTest {

    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("review_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("eureka.client.enabled",      () -> "false");
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9999");
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
            () -> "http://localhost:9999/doesnotexist");
    }

    @Autowired ReviewService reviewService;
    @Autowired ReviewRepository reviewRepository;
    @Autowired ReviewAggregateRepository aggregateRepository;

    @MockBean OrderServiceClient orderServiceClient;

    @Test
    void submitReview_createsReviewAndUpdatesAggregate() {
        UUID productId = UUID.randomUUID();
        UUID userId    = UUID.randomUUID();

        when(orderServiceClient.hasUserPurchasedProduct(any(), any())).thenReturn(true);

        var response = reviewService.submitReview(
            userId, userId, "testuser",
            new CreateReviewRequest(productId, 5, "Excellent!", "Really loved this, would buy again."));

        assertThat(response.rating()).isEqualTo(5);
        assertThat(response.verifiedPurchase()).isTrue();
        assertThat(response.status()).isEqualTo("APPROVED");

        var aggregate = aggregateRepository.findById(productId).orElseThrow();
        assertThat(aggregate.getTotalReviews()).isEqualTo(1);
        assertThat(aggregate.getAverageRating()).isEqualTo(new BigDecimal("5.00"));
        assertThat(aggregate.getRating5Count()).isEqualTo(1);
    }

    @Test
    void submitReview_duplicateReview_throwsDuplicateException() {
        UUID productId = UUID.randomUUID();
        UUID userId    = UUID.randomUUID();

        when(orderServiceClient.hasUserPurchasedProduct(any(), any())).thenReturn(false);

        var request = new CreateReviewRequest(productId, 4, "Good",
            "Pretty good overall, happy with purchase.");
        reviewService.submitReview(userId, userId, "testuser", request);

        assertThatThrownBy(() ->
            reviewService.submitReview(userId, userId, "testuser", request))
            .hasMessageContaining("already exists");
    }

    @Test
    void aggregateAverage_calculatesCorrectlyAcrossMultipleReviews() {
        UUID productId = UUID.randomUUID();
        when(orderServiceClient.hasUserPurchasedProduct(any(), any())).thenReturn(false);

        // 3 ratings: 3, 4, 5
        for (int i = 0; i < 3; i++) {
            int rating = i + 3;
            reviewService.submitReview(
                UUID.randomUUID(), UUID.randomUUID(), "user" + i,
                new CreateReviewRequest(productId, rating,
                    "Title " + i, "Review body text minimum length ok " + i));
        }

        var aggregate = aggregateRepository.findById(productId).orElseThrow();
        assertThat(aggregate.getTotalReviews()).isEqualTo(3);
        // (3 + 4 + 5) / 3 = 4.00
        assertThat(aggregate.getAverageRating()).isEqualTo(new BigDecimal("4.00"));
        assertThat(aggregate.getRating3Count()).isEqualTo(1);
        assertThat(aggregate.getRating4Count()).isEqualTo(1);
        assertThat(aggregate.getRating5Count()).isEqualTo(1);
    }
}
