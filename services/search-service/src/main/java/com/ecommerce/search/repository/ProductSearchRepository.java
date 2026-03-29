package com.ecommerce.search.repository;

import com.ecommerce.search.document.ProductDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.Optional;

public interface ProductSearchRepository extends ElasticsearchRepository<ProductDocument, String> {
    Optional<ProductDocument> findByProductId(String productId);
    void deleteByProductId(String productId);
}
