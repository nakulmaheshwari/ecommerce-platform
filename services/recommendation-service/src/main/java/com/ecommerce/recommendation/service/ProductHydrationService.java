package com.ecommerce.recommendation.service;

import com.ecommerce.recommendation.client.ProductCatalogClient;
import com.ecommerce.recommendation.client.ProductCatalogClient.ProductDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductHydrationService {

    private final ProductCatalogClient catalogClient;

    // One batch call per request — never per-product. Preserves rank order.
    public List<ProductDto> hydrate(List<String> productIds) {
        if (productIds == null || productIds.isEmpty()) return List.of();

        List<UUID> uuids = productIds.stream()
            .filter(Objects::nonNull)
            .map(id -> {
                try { return UUID.fromString(id); }
                catch (IllegalArgumentException e) { return null; }
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        if (uuids.isEmpty()) return List.of();

        List<ProductDto> fetched = catalogClient.getProductsByIds(uuids);

        Map<String, ProductDto> byId = fetched.stream()
            .filter(d -> d.productId() != null)
            .collect(Collectors.toMap(d -> d.productId().toString(), Function.identity(), (a, b) -> a));

        // Restore original order — ranking is significant
        return productIds.stream()
            .filter(id -> id != null && byId.containsKey(id))
            .map(byId::get)
            .collect(Collectors.toList());
    }

    public List<ProductDto> filterActive(List<ProductDto> products) {
        return products.stream()
            .filter(p -> "ACTIVE".equals(p.status()))
            .collect(Collectors.toList());
    }
}
