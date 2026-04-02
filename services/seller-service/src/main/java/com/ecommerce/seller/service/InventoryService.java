package com.ecommerce.seller.service;

import com.ecommerce.seller.domain.SellerInventory;
import com.ecommerce.seller.repository.SellerInventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final SellerInventoryRepository inventoryRepository;

    @Transactional
    public void replenish(UUID inventoryId, int quantity, String notes) {
        log.info("Replenishing inventory: {} with quantity: {}", inventoryId, quantity);
        
        SellerInventory inventory = inventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new RuntimeException("Inventory not found"));

        inventory.setQuantityAvailable(inventory.getQuantityAvailable() + quantity);
        inventory.setLastUpdatedBy("SYSTEM"); // or current user
        
        inventoryRepository.save(inventory);
        
        // TODO: Create InventoryTransaction entry for audit
    }

    @Transactional
    public boolean reserve(UUID inventoryId, int qty) {
        log.info("Reserving inventory: {} for quantity: {}", inventoryId, qty);
        
        int rows = inventoryRepository.reserveInventory(inventoryId, qty);
        if (rows == 0) {
            log.warn("Insufficient inventory for: {} (requested: {})", inventoryId, qty);
            return false;
        }
        return true;
    }

    @Transactional
    public boolean release(UUID inventoryId, int qty) {
        log.info("Releasing reservation for: {} (quantity: {})", inventoryId, qty);
        
        int rows = inventoryRepository.releaseInventory(inventoryId, qty);
        return rows > 0;
    }

    @Transactional
    public boolean confirmSold(UUID inventoryId, int qty) {
        log.info("Confirming sold for: {} (quantity: {})", inventoryId, qty);
        
        int rows = inventoryRepository.confirmSold(inventoryId, qty);
        return rows > 0;
    }

    @Transactional(readOnly = true)
    public SellerInventory getInventoryBySellerProduct(UUID sellerProductId) {
        return inventoryRepository.findBySellerProductId(sellerProductId)
                .orElseThrow(() -> new RuntimeException("Inventory not found"));
    }
}
