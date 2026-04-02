package com.ecommerce.seller.service;

import com.ecommerce.common.event.OrderPlacedEvent;
import com.ecommerce.seller.domain.SellerInventory;
import com.ecommerce.seller.domain.SellerProduct;
import com.ecommerce.seller.repository.SellerProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final InventoryService inventoryService;
    private final SellerProductRepository productRepository;

    @KafkaListener(topics = "order-placed", groupId = "seller-service-group")
    public void handleOrderPlaced(OrderPlacedEvent event) {
        log.info("Received OrderPlacedEvent for order: {}", event.getOrderId());
        
        for (OrderPlacedEvent.OrderItemSnapshot item : event.getItems()) {
            try {
                // Find listing by SKU and Product ID
                // Note: In a real scenario, we might need a mapping table or 
                // the order might specify the seller_product_id directly.
                // For now, we search by productId and sku.
                productRepository.findByProductId(item.productId()).stream()
                    .filter(p -> p.getSku().equals(item.skuId()))
                    .findFirst()
                    .ifPresent(product -> {
                        SellerInventory inventory = inventoryService.getInventoryBySellerProduct(product.getId());
                        inventoryService.reserve(inventory.getId(), item.quantity());
                    });
            } catch (Exception e) {
                log.error("Failed to reserve inventory for item {}: {}", item.productId(), e.getMessage());
            }
        }
    }
}
