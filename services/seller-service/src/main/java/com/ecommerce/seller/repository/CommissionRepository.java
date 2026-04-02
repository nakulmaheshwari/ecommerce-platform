package com.ecommerce.seller.repository;

import com.ecommerce.seller.domain.Commission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CommissionRepository extends JpaRepository<Commission, UUID> {
    Optional<Commission> findByCategoryId(UUID categoryId);
}
