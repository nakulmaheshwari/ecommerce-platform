package com.ecommerce.seller.repository;

import com.ecommerce.seller.domain.Settlement;
import com.ecommerce.seller.domain.enums.SettlementStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface SettlementRepository extends JpaRepository<Settlement, UUID> {
    List<Settlement> findBySellerId(UUID sellerId);
    List<Settlement> findByStatus(SettlementStatus status);
    List<Settlement> findByPeriodStartBetween(OffsetDateTime start, OffsetDateTime end);
}
