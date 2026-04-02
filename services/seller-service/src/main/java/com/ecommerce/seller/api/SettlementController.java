package com.ecommerce.seller.api;

import com.ecommerce.seller.domain.Seller;
import com.ecommerce.seller.domain.Settlement;
import com.ecommerce.seller.repository.SellerRepository;
import com.ecommerce.seller.service.SettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sellers/settlements")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;
    private final SellerRepository sellerRepository;

    @GetMapping
    public ResponseEntity<List<Settlement>> getMySettlements(@AuthenticationPrincipal Jwt jwt) {
        String keycloakId = jwt.getSubject();
        Seller seller = sellerRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new RuntimeException("Seller not found"));

        return ResponseEntity.ok(settlementService.getSettlementsForSeller(seller.getId()));
    }
}
