package com.ecommerce.seller.repository;

import com.ecommerce.seller.domain.SellerProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SellerProductRepository extends JpaRepository<SellerProduct, UUID> {
    List<SellerProduct> findBySellerId(UUID sellerId);
    List<SellerProduct> findByProductId(UUID productId);
    Optional<SellerProduct> findBySellerIdAndProductIdAndSku(UUID sellerId, UUID productId, String sku);
    List<SellerProduct> findByStatus(String status);
}
