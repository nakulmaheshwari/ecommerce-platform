package com.ecommerce.seller.service;

import com.ecommerce.seller.api.dto.ProductListingRequest;
import com.ecommerce.seller.domain.Seller;
import com.ecommerce.seller.domain.SellerInventory;
import com.ecommerce.seller.domain.SellerProduct;
import com.ecommerce.seller.repository.SellerInventoryRepository;
import com.ecommerce.seller.repository.SellerProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductListingService {

    private final SellerProductRepository productRepository;
    private final SellerInventoryRepository inventoryRepository;

    @Transactional
    public SellerProduct createListing(ProductListingRequest request, Seller seller) {
        log.info("Creating product listing for seller: {} (ID: {}) for product ID: {}", 
                seller.getBusinessName(), seller.getId(), request.getProductId());

        // Check if seller is active
        if (!"ACTIVE".equals(seller.getStatus().name())) {
            throw new RuntimeException("Seller must be ACTIVE to list products");
        }

        // Create SellerProduct
        SellerProduct product = SellerProduct.builder()
                .seller(seller)
                .productId(request.getProductId())
                .sku(request.getSku())
                .sellingPricePaise(request.getSellingPricePaise())
                .mrpPaise(request.getMrpPaise())
                .dispatchDays(request.getDispatchDays() != null ? request.getDispatchDays() : 2)
                .shipsFromCity(request.getShipsFromCity())
                .shipsFromState(request.getShipsFromState())
                .status("ACTIVE") // auto-approving for now, wait for listing-approved event logic
                .customTitle(request.getCustomTitle())
                .customDescription(request.getCustomDescription())
                .build();

        SellerProduct savedProduct = productRepository.save(product);

        // Initialize Inventory
        SellerInventory inventory = SellerInventory.builder()
                .sellerProduct(savedProduct)
                .quantityAvailable(request.getInitialQuantity() != null ? request.getInitialQuantity() : 0)
                .quantityReserved(0)
                .quantitySold(0L)
                .build();

        inventoryRepository.save(inventory);
        
        // TODO: Emit listing-created/approved event in Phase 4

        return savedProduct;
    }

    @Transactional(readOnly = true)
    public SellerProduct getListingById(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product listing not found"));
    }
}
