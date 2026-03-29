package com.ecommerce.order.repository;

import com.ecommerce.order.domain.OrderStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, UUID> {
    List<OrderStatusHistory> findByOrderIdOrderByCreatedAtAsc(UUID orderId);
}
