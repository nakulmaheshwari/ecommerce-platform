package com.ecommerce.catalog.repository;

import com.ecommerce.catalog.domain.Product;
import com.ecommerce.catalog.domain.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findBySlug(String slug);

    Optional<Product> findBySku(String sku);

    boolean existsBySku(String sku);

    boolean existsBySlug(String slug);

    // Fetch with images in one query — avoids N+1
    @Query("""
        SELECT DISTINCT p FROM Product p
        LEFT JOIN FETCH p.images i
        LEFT JOIN FETCH p.category c
        WHERE p.id = :id AND p.status = 'ACTIVE'
        """)
    Optional<Product> findActiveByIdWithDetails(@Param("id") UUID id);

    @Query("""
        SELECT DISTINCT p FROM Product p
        LEFT JOIN FETCH p.images
        LEFT JOIN FETCH p.category
        WHERE p.slug = :slug AND p.status = 'ACTIVE'
        """)
    Optional<Product> findActiveBySlugWithDetails(@Param("slug") String slug);

    // Category browsing — paginated
    @Query(value = """
        SELECT p FROM Product p
        JOIN FETCH p.category c
        WHERE c.id = :categoryId
          AND p.status = 'ACTIVE'
        """,
        countQuery = """
        SELECT COUNT(p) FROM Product p
        WHERE p.category.id = :categoryId AND p.status = 'ACTIVE'
        """)
    Page<Product> findByCategoryId(
        @Param("categoryId") UUID categoryId, Pageable pageable);

    // Price range filter
    @Query("""
        SELECT p FROM Product p
        WHERE p.category.id = :categoryId
          AND p.status = 'ACTIVE'
          AND p.pricePaise BETWEEN :minPaise AND :maxPaise
        """)
    Page<Product> findByCategoryAndPriceRange(
        @Param("categoryId") UUID categoryId,
        @Param("minPaise") Long minPaise,
        @Param("maxPaise") Long maxPaise,
        Pageable pageable);

    @Query("""
        SELECT DISTINCT p FROM Product p
        LEFT JOIN FETCH p.images
        WHERE p.category.id = :categoryId
          AND p.id != :productId
          AND p.status = 'ACTIVE'
        ORDER BY p.publishedAt DESC
        """)
    Page<Product> findRelatedProducts(
        @Param("categoryId") UUID categoryId,
        @Param("productId") UUID productId,
        Pageable pageable);
}
