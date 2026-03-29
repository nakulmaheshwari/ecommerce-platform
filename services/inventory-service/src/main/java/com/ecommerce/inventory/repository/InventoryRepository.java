package com.ecommerce.inventory.repository;

import com.ecommerce.inventory.domain.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Set;

public interface InventoryRepository extends JpaRepository<Inventory, String> {

    List<Inventory> findBySkuIdIn(Set<String> skuIds);

    @Query("SELECT i FROM Inventory i WHERE i.availableQty <= i.reorderPoint")
    List<Inventory> findLowStockItems();
}
