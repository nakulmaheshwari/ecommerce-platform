package com.ecommerce.inventory.repository;

import com.ecommerce.inventory.domain.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Set;

/**
 * Repository for managing {@link Inventory} stock records.
 */
public interface InventoryRepository extends JpaRepository<Inventory, String> {

    /**
     * Batch lookup of inventory status for multiple SKUs.
     * Efficiently fetches stock levels for checkout availability checks.
     * 
     * @param skuIds Set of SKU identifiers.
     * @return List of matching Inventory entities.
     */
    List<Inventory> findBySkuIdIn(Set<String> skuIds);

    /**
     * Custom JPQL query to find all SKUs where availableQty is below the reorderPoint.
     * Used by administrative dashboards and replenishment background tasks.
     * 
     * @return List of items requiring restocking.
     */
    @Query("SELECT i FROM Inventory i WHERE i.availableQty <= i.reorderPoint")
    List<Inventory> findLowStockItems();
}
