package com.ecommerce.inventory.repository;

import com.ecommerce.inventory.domain.InventoryMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Repository for accessing the historical {@link InventoryMovement} audit log.
 */
public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, UUID> {
    
    /**
     * Fetches a paginated list of movements for a specific SKU, newest first.
     * Essential for the Admin Audit UI where thousands of movements may exist per SKU.
     */
    Page<InventoryMovement> findBySkuIdOrderByCreatedAtDesc(String skuId, Pageable pageable);
}
