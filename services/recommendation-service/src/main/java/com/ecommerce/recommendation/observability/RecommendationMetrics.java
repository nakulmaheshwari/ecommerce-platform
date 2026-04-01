package com.ecommerce.recommendation.observability;

import io.micrometer.core.instrument.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Slf4j
public class RecommendationMetrics {

    private final MeterRegistry registry;
    private final ConcurrentHashMap<String, AtomicLong> dlqDepths = new ConcurrentHashMap<>();

    public RecommendationMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordImpression(String algorithm, int count) {
        Counter.builder("rec.impressions").tag("algorithm", algorithm)
            .register(registry).increment(count);
    }

    public void recordClick(String recommendationType, int position) {
        Counter.builder("rec.clicks")
            .tag("type", recommendationType)
            .tag("position", bucketPosition(position))
            .register(registry).increment();
    }

    public void recordConversion(String recommendationType) {
        Counter.builder("rec.conversions").tag("type", recommendationType)
            .register(registry).increment();
    }

    public void recordCacheHit(String cacheType) {
        Counter.builder("rec.cache.hits").tag("type", cacheType)
            .register(registry).increment();
    }

    public void recordFallback(String algorithmType) {
        Counter.builder("rec.fallback").tag("algorithm", algorithmType)
            .register(registry).increment();
    }

    public void recordRequest(String endpoint, String algorithm) {
        Counter.builder("rec.api.requests")
            .tag("endpoint", endpoint).tag("algorithm", algorithm)
            .register(registry).increment();
    }

    // Histogram → Prometheus exposes p50, p95, p99
    public void recordLatency(String algorithm, long tookMs) {
        Timer.builder("rec.api.latency").tag("algorithm", algorithm)
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry)
            .record(tookMs, TimeUnit.MILLISECONDS);
    }

    public void recordDlqMessage(String topic) {
        Counter.builder("rec.dlq.messages").tag("topic", topic).register(registry).increment();

        AtomicLong depth = dlqDepths.computeIfAbsent(topic, t -> {
            AtomicLong g = new AtomicLong(0);
            Gauge.builder("rec.dlq.depth", g, AtomicLong::doubleValue)
                .tag("topic", t).register(registry);
            return g;
        });
        depth.incrementAndGet();

        log.error("DLQ message topic={} — manual intervention required", topic);
    }

    public void recordModelUpdate(String modelType) {
        registry.gauge("rec.model.last_updated_epoch",
            java.util.List.of(Tag.of("model", modelType)),
            System.currentTimeMillis() / 1000.0);
    }

    private String bucketPosition(int pos) {
        if (pos <= 3)  return "top_3";
        if (pos <= 10) return "top_10";
        if (pos <= 20) return "top_20";
        return "bottom";
    }
}
