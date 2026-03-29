package com.ecommerce.search.event;

import com.ecommerce.search.api.dto.IndexProductRequest;
import com.ecommerce.search.service.ProductSearchService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductEventConsumer {

    private final ProductSearchService searchService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = {"product-created", "product-published"}, groupId = "search-service-product-group")
    public void handleProductCreatedOrPublished(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JsonNode payload = objectMapper.readTree(record.value());
            searchService.indexProduct(buildIndexRequest(payload));
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to index product", e);
        }
    }

    @KafkaListener(topics = "product-updated", groupId = "search-service-product-group")
    public void handleProductUpdated(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JsonNode payload = objectMapper.readTree(record.value());
            searchService.indexProduct(buildIndexRequest(payload));
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to update product index", e);
        }
    }

    @KafkaListener(topics = {"inventory-reserved", "inventory-released"}, groupId = "search-service-inventory-group")
    public void handleInventoryChange(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JsonNode payload = objectMapper.readTree(record.value());
            if (payload.has("reservedSkus")) {
                payload.get("reservedSkus").forEach(item -> {
                    try {
                        searchService.updateProductAvailability(item.asText(), true, 0);
                    } catch (Exception e) {
                        log.warn("Failed to update availability", e);
                    }
                });
            }
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process inventory event", e);
        }
    }

    private IndexProductRequest buildIndexRequest(JsonNode payload) {
        List<String> tags = new ArrayList<>();
        if (payload.has("tags") && payload.get("tags").isArray()) {
            payload.get("tags").forEach(t -> tags.add(t.asText()));
        }

        Map<String, List<String>> attributes = new HashMap<>();
        if (payload.has("attributes")) {
            payload.get("attributes").fields().forEachRemaining(entry -> {
                List<String> vals = new ArrayList<>();
                if (entry.getValue().isArray()) entry.getValue().forEach(v -> vals.add(v.asText()));
                else vals.add(entry.getValue().asText());
                attributes.put(entry.getKey(), vals);
            });
        }

        return new IndexProductRequest(
            payload.path("productId").asText(), payload.path("sku").asText(), payload.path("name").asText(),
            payload.path("description").asText(null), payload.path("brand").asText(null),
            payload.path("categoryId").asText(null), payload.path("categoryName").asText(null),
            payload.path("categorySlug").asText(null), payload.path("pricePaise").asLong(0),
            payload.path("mrpPaise").asLong(0), payload.path("discountPercent").asInt(0),
            payload.path("status").asText("ACTIVE"), payload.path("averageRating").asDouble(0),
            payload.path("totalReviews").asInt(0), tags, attributes,
            payload.path("primaryImageUrl").asText(null), payload.path("inStock").asBoolean(true),
            payload.path("stockQuantity").asInt(0), payload.path("isDigital").asBoolean(false)
        );
    }
}
