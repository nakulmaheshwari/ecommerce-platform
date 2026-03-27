package com.ecommerce.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Ensures every incoming request gets a Tracking/Correlation ID.
 * This ID follows the request deep into the microservices architecture.
 */
@Component
public class TrackingFilter implements GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(TrackingFilter.class);
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        HttpHeaders headers = exchange.getRequest().getHeaders();
        
        if (!isCorrelationIdPresent(headers)) {
            String correlationId = generateCorrelationId();
            logger.debug("Generating new correlation ID: {}", correlationId);
            
            ServerWebExchange mutatedExchange = exchange.mutate().request(
                exchange.getRequest().mutate()
                    .header(CORRELATION_ID_HEADER, correlationId)
                    .build()
            ).build();
            return chain.filter(mutatedExchange);
        } else {
            logger.debug("Correlation ID found: {}", headers.getFirst(CORRELATION_ID_HEADER));
            return chain.filter(exchange);
        }
    }

    private boolean isCorrelationIdPresent(HttpHeaders headers) {
        return headers.containsKey(CORRELATION_ID_HEADER);
    }

    private String generateCorrelationId() {
        return UUID.randomUUID().toString();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE; // Execute first
    }
}
