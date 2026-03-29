package com.ecommerce.order.repository;

import com.ecommerce.order.domain.Order;
import com.ecommerce.order.domain.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByIdempotencyKey(UUID idempotencyKey);

    /**
     * Fetch with items in single query — avoids N+1.
     *
     * Without JOIN FETCH, accessing order.getItems() triggers a
     * separate SELECT per order. For a list of 20 orders, that's
     * 21 queries. JOIN FETCH makes it 1 query.
     *
     * Note: Can't use JOIN FETCH with pagination (it would fetch ALL
     * items for ALL orders). For list views, we use a separate
     * query for items after pagination.
     */
    @Query("""
        SELECT o FROM Order o
        JOIN FETCH o.items
        WHERE o.id = :id
        """)
    Optional<Order> findByIdWithItems(@Param("id") UUID id);

    Page<Order> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<Order> findByUserIdAndStatusOrderByCreatedAtDesc(
        UUID userId, OrderStatus status, Pageable pageable);
}
