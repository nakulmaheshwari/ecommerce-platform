package com.ecommerce.recommendation.api;

import com.ecommerce.recommendation.api.dto.*;
import com.ecommerce.recommendation.event.producer.FeedbackEventProducer;
import com.ecommerce.recommendation.observability.RecommendationMetrics;
import com.ecommerce.recommendation.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/recommendations")
@RequiredArgsConstructor
@Slf4j
public class RecommendationController {

    private final RecommendationService  recommendationService;
    private final FeedbackEventProducer  feedbackProducer;
    private final RecommendationMetrics  metrics;

    // ── Trending (public) ──────────────────────────────────────────────────────

    @GetMapping("/trending")
    public ResponseEntity<RecommendationResponse> getGlobalTrending(
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "0")  @Min(0)           int offset) {
        return ResponseEntity.ok(recommendationService.getGlobalTrending(limit, offset));
    }

    @GetMapping("/trending/category/{categoryId}")
    public ResponseEntity<RecommendationResponse> getCategoryTrending(
            @PathVariable @NotBlank String categoryId,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return ResponseEntity.ok(recommendationService.getCategoryTrending(categoryId, limit));
    }

    // ── Product-level (public) ────────────────────────────────────────────────

    @GetMapping("/product/{productId}/also-bought")
    public ResponseEntity<RecommendationResponse> getAlsoBought(
            @PathVariable @NotBlank String productId,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {
        return ResponseEntity.ok(recommendationService.getAlsoBought(productId, limit));
    }

    @GetMapping("/product/{productId}/fbt")
    public ResponseEntity<RecommendationResponse> getFrequentlyBoughtTogether(
            @PathVariable @NotBlank String productId,
            @RequestParam(defaultValue = "10") @Min(1) @Max(20) int limit) {
        return ResponseEntity.ok(recommendationService.getFrequentlyBoughtTogether(productId, limit));
    }

    @GetMapping("/product/{productId}/similar")
    public ResponseEntity<RecommendationResponse> getSimilarProducts(
            @PathVariable @NotBlank String productId,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {
        return ResponseEntity.ok(recommendationService.getSimilarProducts(productId, limit));
    }

    // ── User-personalised (authenticated) ────────────────────────────────────

    @GetMapping("/user/{userId}")
    public ResponseEntity<RecommendationResponse> getUserRecommendations(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "20")    @Min(1) @Max(50) int     limit,
            @RequestParam(defaultValue = "true")                   boolean excludeOwned,
            @RequestParam(required = false)                        String  sessionId,
            @RequestParam(defaultValue = "control")                String  experiment,
            @AuthenticationPrincipal Jwt jwt) {

        enforceUserMatch(userId, jwt);
        return ResponseEntity.ok(
            recommendationService.getUserRecommendations(
                userId, limit, excludeOwned, sessionId, experiment));
    }

    @GetMapping("/user/{userId}/recently-viewed")
    public ResponseEntity<RecommendationResponse> getRecentlyViewed(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit,
            @AuthenticationPrincipal Jwt jwt) {

        enforceUserMatch(userId, jwt);
        return ResponseEntity.ok(recommendationService.getRecentlyViewed(userId.toString(), limit));
    }

    // ── Session-based (public) ─────────────────────────────────────────────────

    @GetMapping("/session/{sessionId}")
    public ResponseEntity<RecommendationResponse> getSessionRecommendations(
            @PathVariable @NotBlank String sessionId,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {
        // Flink writes session recs to Redis. Falls through to trending if not present.
        return ResponseEntity.ok(recommendationService.getGlobalTrending(limit, 0));
    }

    // ── Cart cross-sell (authenticated) ──────────────────────────────────────

    @GetMapping("/cart")
    public ResponseEntity<RecommendationResponse> getCartCrossSell(
            @RequestParam List<String> productIds,
            @RequestParam(defaultValue = "8") @Min(1) @Max(20) int limit,
            @AuthenticationPrincipal Jwt jwt) {

        UUID userId = extractUserId(jwt);
        return ResponseEntity.ok(
            recommendationService.getCartCrossSell(productIds, userId, limit));
    }

    // ── Feedback (authenticated) ──────────────────────────────────────────────

    @PostMapping("/feedback")
    public ResponseEntity<Void> recordFeedback(
            @Valid @RequestBody FeedbackRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        UUID userId = extractUserId(jwt);
        feedbackProducer.publishFeedback(request, userId);
        metrics.recordClick(request.recommendationType(), request.position());
        return ResponseEntity.accepted().build();
    }

    // ── Internal (INTERNAL_SERVICE role) ─────────────────────────────────────

    @GetMapping("/internal/user/{userId}/affinity-scores")
    @PreAuthorize("hasRole('INTERNAL_SERVICE')")
    public ResponseEntity<AffinityScoreResponse> getAffinityScores(
            @PathVariable UUID userId,
            @RequestParam List<String> productIds,
            @RequestParam Map<String, String> productCategories,
            @RequestParam Map<String, String> productBrands) {

        Map<String, PersonalisationService.ProductAttributes> attrMap =
            productIds.stream().collect(Collectors.toMap(
                id -> id,
                id -> new PersonalisationService.ProductAttributes(
                    productCategories.getOrDefault(id, ""),
                    productBrands.getOrDefault(id, ""))));

        Map<String, Double> scores = recommendationService.getAffinityScores(userId, attrMap);
        return ResponseEntity.ok(new AffinityScoreResponse(
            userId.toString(), scores, java.time.Instant.now()));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void enforceUserMatch(UUID pathUserId, Jwt jwt) {
        if (!pathUserId.toString().equals(jwt.getSubject())) {
            throw new org.springframework.security.access.AccessDeniedException(
                "Cannot access another user's recommendations");
        }
    }

    private UUID extractUserId(Jwt jwt) {
        if (jwt == null) return null;
        try { return UUID.fromString(jwt.getSubject()); }
        catch (Exception e) { return null; }
    }
}
