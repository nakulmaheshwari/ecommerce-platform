package com.ecommerce.seller.api;

import com.ecommerce.seller.api.dto.InventoryUpdateRequest;
import com.ecommerce.seller.api.dto.ProductListingRequest;
import com.ecommerce.seller.domain.Seller;
import com.ecommerce.seller.domain.SellerInventory;
import com.ecommerce.seller.domain.SellerProduct;
import com.ecommerce.seller.repository.SellerRepository;
import com.ecommerce.seller.service.InventoryService;
import com.ecommerce.seller.service.ProductListingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sellers/products")
@RequiredArgsConstructor
public class SellerProductController {

    private final ProductListingService listingService;
    private final InventoryService inventoryService;
    private final SellerRepository sellerRepository;

    @PostMapping
    public ResponseEntity<SellerProduct> createListing(
            @Valid @RequestBody ProductListingRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        
        String keycloakId = jwt.getSubject();
        Seller seller = sellerRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new RuntimeException("Seller not found"));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(listingService.createListing(request, seller));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SellerProduct> getListing(@PathVariable UUID id) {
        return ResponseEntity.ok(listingService.getListingById(id));
    }

    @PostMapping("/{id}/inventory")
    public ResponseEntity<Void> updateInventory(
            @PathVariable UUID id,
            @Valid @RequestBody InventoryUpdateRequest request) {
        
        SellerProduct product = listingService.getListingById(id);
        SellerInventory inventory = inventoryService.getInventoryBySellerProduct(product.getId());
        
        inventoryService.replenish(inventory.getId(), request.getQuantityChange(), request.getNotes());
        
        return ResponseEntity.noContent().build();
    }
}
