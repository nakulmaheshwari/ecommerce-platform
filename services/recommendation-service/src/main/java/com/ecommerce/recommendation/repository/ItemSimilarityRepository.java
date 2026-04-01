package com.ecommerce.recommendation.repository;

import com.ecommerce.recommendation.domain.ItemSimilarity;
import com.ecommerce.recommendation.domain.ItemSimilarityId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface ItemSimilarityRepository extends JpaRepository<ItemSimilarity, ItemSimilarityId> {

    @Query("SELECT s FROM ItemSimilarity s " +
           "WHERE s.sourceProductId = :productId AND s.algorithm = :algorithm " +
           "ORDER BY s.similarityScore DESC")
    List<ItemSimilarity> findTopByProductAndAlgorithm(
            @Param("productId") UUID productId,
            @Param("algorithm") String algorithm,
            Pageable pageable);

    @Modifying
    @Query("DELETE FROM ItemSimilarity s WHERE s.computedAt < :before AND s.algorithm = :algorithm")
    void deleteStaleByAlgorithm(@Param("before") Instant before,
                                @Param("algorithm") String algorithm);
}
