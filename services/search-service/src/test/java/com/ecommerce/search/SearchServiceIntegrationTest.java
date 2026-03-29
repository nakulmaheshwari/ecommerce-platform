package com.ecommerce.search;

import com.ecommerce.search.api.dto.IndexProductRequest;
import com.ecommerce.search.api.dto.SearchRequest;
import com.ecommerce.search.api.dto.SearchResponse;
import com.ecommerce.search.service.ProductSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class SearchServiceIntegrationTest {

    @Container
    static ElasticsearchContainer elasticsearch =
        new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:8.12.1")
            .withEnv("xpack.security.enabled", "false")
            .withEnv("discovery.type", "single-node");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.elasticsearch.uris", elasticsearch::getHttpHostAddress);
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9999");
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> "http://localhost:9999/doesnotexist");
    }

    @Autowired ProductSearchService searchService;

    @BeforeEach
    void indexTestProducts() throws IOException, InterruptedException {
        searchService.indexProduct(new IndexProductRequest(
            "prod-1", "APPLE-IPH15-128-BLK", "Apple iPhone 15 128GB Black",
            "Latest iPhone with Dynamic Island and USB-C", "Apple", "cat-electronics",
            "Electronics", "electronics", 7999900L, 8999900L, 11, "ACTIVE",
            4.5, 2847, List.of("bestseller", "new-arrival"), Map.of(),
            "https://example.com/iphone15.jpg", true, 50, false
        ));
        Thread.sleep(1500);
    }

    @Test
    void search_byTextQuery_returnsRelevantResults() throws IOException {
        SearchRequest request = new SearchRequest("iphone", null, null, null, null, null, null, null, "RELEVANCE", 0, 20);
        SearchResponse response = searchService.search(request);
        assertThat(response.hits()).isNotEmpty();
        assertThat(response.hits().get(0).name()).containsIgnoringCase("iPhone");
    }

    @Test
    void autocomplete_returnsRelevantSuggestions() throws IOException {
        var suggestions = searchService.autocomplete("iph");
        assertThat(suggestions.suggestions()).isNotEmpty();
        assertThat(suggestions.suggestions().get(0)).containsIgnoringCase("iphone");
    }
}
