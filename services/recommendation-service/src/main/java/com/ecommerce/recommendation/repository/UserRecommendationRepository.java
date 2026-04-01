package com.ecommerce.recommendation.repository;

import com.ecommerce.recommendation.domain.UserRecommendation;
import com.ecommerce.recommendation.domain.UserRecommendationId;
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
public interface UserRecommendationRepository extends JpaRepository<UserRecommendation, UserRecommendationId> {

    @Query("SELECT r FROM UserRecommendation r " +
           "WHERE r.userId = :userId AND r.expiresAt > :now ORDER BY r.rank ASC")
    List<UserRecommendation> findActiveByUser(@Param("userId") UUID userId,
                                              @Param("now") Instant now,
                                              Pageable pageable);

    @Modifying
    @Query("DELETE FROM UserRecommendation r WHERE r.userId = :userId")
    void deleteByUserId(@Param("userId") UUID userId);

    @Modifying
    @Query("DELETE FROM UserRecommendation r WHERE r.expiresAt < :now")
    void deleteExpired(@Param("now") Instant now);
}
