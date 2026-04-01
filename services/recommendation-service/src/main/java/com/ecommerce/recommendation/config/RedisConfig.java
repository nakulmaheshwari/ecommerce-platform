package com.ecommerce.recommendation.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class RedisConfig {

    /*
     * All values stored as JSON strings.
     * Language-agnostic — Python Spark scripts can read them during warmup validation.
     */
    @Bean
    @Primary
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        StringRedisSerializer s = new StringRedisSerializer();
        template.setKeySerializer(s);
        template.setValueSerializer(s);
        template.setHashKeySerializer(s);
        template.setHashValueSerializer(s);
        template.setDefaultSerializer(s);
        template.afterPropertiesSet();
        return template;
    }

    /*
     * Caffeine L1 cache (30s TTL, 500 entries per pod).
     * Top 500 items cover ~80% of requests by Pareto law.
     * Used with @Cacheable on trending and affinity score methods.
     */
    @Bean
    public CacheManager caffeineCacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(30, TimeUnit.SECONDS)
            .recordStats());
        manager.setCacheNames(List.of("productRecs", "trendingRecs", "affinityScores"));
        return manager;
    }
}
