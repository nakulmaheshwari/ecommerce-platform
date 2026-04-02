package com.ecommerce.seller.service;

import com.ecommerce.seller.api.dto.SellerRegistrationRequest;
import com.ecommerce.seller.api.dto.SellerResponse;
import com.ecommerce.seller.domain.Seller;
import com.ecommerce.seller.domain.enums.SellerStatus;
import com.ecommerce.seller.repository.SellerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SellerService {

    private final SellerRepository sellerRepository;
    // eventProducer will be added in Phase 4

    @Transactional
    public SellerResponse register(SellerRegistrationRequest request, String keycloakId) {
        log.info("Registering new seller: {} with Keycloak ID: {}", request.getBusinessName(), keycloakId);
        
        if (sellerRepository.findByKeycloakId(keycloakId).isPresent()) {
            throw new RuntimeException("Seller already registered for this user");
        }

        Seller seller = Seller.builder()
                .keycloakId(keycloakId)
                .email(request.getEmail())
                .phone(request.getPhone())
                .businessName(request.getBusinessName())
                .displayName(request.getDisplayName())
                .description(request.getDescription())
                .entityType(request.getEntityType())
                .panNumber(request.getPanNumber())
                .gstin(request.getGstin())
                .status(SellerStatus.PENDING_KYC)
                .build();

        if (request.getAddress() != null) {
            seller.setAddressLine1(request.getAddress().getAddressLine1());
            seller.setAddressLine2(request.getAddress().getAddressLine2());
            seller.setCity(request.getAddress().getCity());
            seller.setState(request.getAddress().getState());
            seller.setPincode(request.getAddress().getPincode());
            seller.setCountry(request.getAddress().getCountry());
        }

        Seller savedSeller = sellerRepository.save(seller);
        
        // TODO: Emit seller-registered event in Phase 4

        return mapToResponse(savedSeller);
    }

    @Transactional(readOnly = true)
    public SellerResponse getSellerByKeycloakId(String keycloakId) {
        return sellerRepository.findByKeycloakId(keycloakId)
                .map(this::mapToResponse)
                .orElseThrow(() -> new RuntimeException("Seller not found"));
    }

    @Transactional(readOnly = true)
    public SellerResponse getSellerById(UUID id) {
        return sellerRepository.findById(id)
                .map(this::mapToResponse)
                .orElseThrow(() -> new RuntimeException("Seller not found"));
    }

    private SellerResponse mapToResponse(Seller seller) {
        return SellerResponse.builder()
                .id(seller.getId())
                .keycloakId(seller.getKeycloakId())
                .email(seller.getEmail())
                .phone(seller.getPhone())
                .businessName(seller.getBusinessName())
                .displayName(seller.getDisplayName())
                .status(seller.getStatus().name())
                .averageRating(seller.getAverageRating())
                .pendingSettlementPaise(seller.getPendingSettlementPaise())
                .createdAt(seller.getCreatedAt())
                .build();
    }
}
