package com.ecommerce.identity.api;

import com.ecommerce.common.security.SecurityUtils;
import com.ecommerce.identity.api.dto.*;
import com.ecommerce.identity.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(
            @Valid @RequestBody RegisterRequest request) {
        RegisterResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    // Protected — requires valid JWT. Used by API Gateway to validate tokens.
    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    public ResponseEntity<RegisterResponse> getMyProfile() {
        return ResponseEntity.ok(
            authService.getProfile(SecurityUtils.getCurrentUserId())
        );
    }

    // Health check for Gateway — returns 200 if service is up and JWT is valid
    @GetMapping("/validate")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> validateToken() {
        return ResponseEntity.ok().build();
    }
}
