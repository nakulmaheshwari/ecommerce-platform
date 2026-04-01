package com.ecommerce.recommendation.repository;

import com.ecommerce.recommendation.domain.UserProductInteraction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InteractionRepository extends JpaRepository<UserProductInteraction, UUID> {

    @Modifying
    @Query("DELETE FROM UserProductInteraction u WHERE u.userId = :userId")
    void deleteByUserId(@Param("userId") UUID userId);

    @Query("SELECT COUNT(DISTINCT u.productId) FROM UserProductInteraction u " +
           "WHERE u.userId = :userId AND u.eventType = 'order_placed'")
    long countDistinctPurchasesByUser(@Param("userId") UUID userId);

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN TRUE ELSE FALSE END " +
           "FROM UserProductInteraction u " +
           "WHERE u.userId = :userId AND u.productId = :productId AND u.eventType = 'order_placed'")
    boolean hasUserPurchased(@Param("userId") UUID userId,
                             @Param("productId") UUID productId);

    @Query("SELECT DISTINCT u.productId FROM UserProductInteraction u " +
           "WHERE u.userId = :userId AND u.eventType = 'order_placed'")
    List<UUID> findPurchasedProductIds(@Param("userId") UUID userId);
}
