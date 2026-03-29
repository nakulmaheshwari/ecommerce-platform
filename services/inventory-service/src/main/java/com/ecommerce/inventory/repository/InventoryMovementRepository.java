package com.ecommerce.inventory.repository;

import com.ecommerce.inventory.domain.InventoryMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, UUID> {
    Page<InventoryMovement> findBySkuIdOrderByCreatedAtDesc(String skuId, Pageable pageable);
}
