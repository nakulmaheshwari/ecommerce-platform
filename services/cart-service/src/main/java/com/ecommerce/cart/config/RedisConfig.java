package com.ecommerce.cart.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * RedisTemplate<String, String> — we manage serialization ourselves.
 *
 * Why not RedisTemplate<String, CartItem>?
 * Because we use Redis Hash with individual fields (one field per SKU).
 * The HashOperations API works at the String level — we serialize/deserialize
 * CartItem to/from JSON ourselves. This gives us full control over the
 * JSON format and avoids Redis storing Java type metadata in the value.
 *
 * Key serializer: StringRedisSerializer
 *   → Keys are human-readable: "cart:user-123-uuid"
 *   → Easy to inspect in Redis CLI: HGETALL cart:user-123-uuid
 *
 * Value serializer: StringRedisSerializer
 *   → Values are plain JSON strings
 *   → Can be read by any language/tool without Java class info
 */
@Configuration
public class RedisConfig {

    @Bean
    public ObjectMapper cartObjectMapper() {
        return new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Bean
    public RedisTemplate<String, String> redisTemplate(
            RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setValueSerializer(stringSerializer);
        template.setHashValueSerializer(stringSerializer);
        template.afterPropertiesSet();
        return template;
    }
}
