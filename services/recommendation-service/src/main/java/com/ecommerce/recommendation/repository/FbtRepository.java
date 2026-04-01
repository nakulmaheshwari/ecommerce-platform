package com.ecommerce.recommendation.repository;

import com.ecommerce.recommendation.domain.FbtId;
import com.ecommerce.recommendation.domain.FrequentlyBoughtTogether;
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
public interface FbtRepository extends JpaRepository<FrequentlyBoughtTogether, FbtId> {

    @Query("SELECT f FROM FrequentlyBoughtTogether f " +
           "WHERE f.productAId = :productId ORDER BY f.confidence DESC")
    List<FrequentlyBoughtTogether> findTopByProductA(
            @Param("productId") UUID productId, Pageable pageable);

    @Modifying
    @Query("DELETE FROM FrequentlyBoughtTogether f WHERE f.computedAt < :before")
    void deleteStale(@Param("before") Instant before);
}
