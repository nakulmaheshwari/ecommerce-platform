package com.ecommerce.seller.repository;

import com.ecommerce.seller.domain.Seller;
import com.ecommerce.seller.domain.enums.SellerStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

@Repository
public interface SellerRepository extends JpaRepository<Seller, UUID> {
    Optional<Seller> findByKeycloakId(String keycloakId);
    Optional<Seller> findByEmail(String email);
    List<Seller> findByStatus(SellerStatus status);
}
