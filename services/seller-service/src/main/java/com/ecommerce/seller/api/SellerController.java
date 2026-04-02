package com.ecommerce.seller.api;

import com.ecommerce.seller.api.dto.SellerRegistrationRequest;
import com.ecommerce.seller.api.dto.SellerResponse;
import com.ecommerce.seller.service.SellerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/sellers")
@RequiredArgsConstructor
public class SellerController {

    private final SellerService sellerService;

    @PostMapping("/register")
    public ResponseEntity<SellerResponse> register(
            @Valid @RequestBody SellerRegistrationRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        
        // keycloak_id comes from JWT sub claim
        String keycloakId = jwt.getSubject();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(sellerService.register(request, keycloakId));
    }

    @GetMapping("/me")
    public ResponseEntity<SellerResponse> getMyProfile(@AuthenticationPrincipal Jwt jwt) {
        String keycloakId = jwt.getSubject();
        return ResponseEntity.ok(sellerService.getSellerByKeycloakId(keycloakId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SellerResponse> getSeller(@PathVariable java.util.UUID id) {
        return ResponseEntity.ok(sellerService.getSellerById(id));
    }
}
