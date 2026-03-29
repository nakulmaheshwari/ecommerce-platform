package com.ecommerce.user.api;

import com.ecommerce.common.security.SecurityUtils;
import com.ecommerce.user.api.dto.*;
import com.ecommerce.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/users/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserProfileResponse> getMyProfile() {
        UUID keycloakId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(userService.getMyProfile(keycloakId));
    }

    @PatchMapping("/users/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserProfileResponse> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request) {
        UUID keycloakId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(userService.updateProfile(keycloakId, request));
    }

    @PatchMapping("/users/me/preferences")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserProfileResponse> updatePreference(
            @Valid @RequestBody UpdatePreferenceRequest request) {
        UUID keycloakId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(userService.updatePreference(keycloakId, request));
    }

    @GetMapping("/users/me/addresses")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<AddressResponse>> getAddresses() {
        UUID keycloakId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(userService.getAddresses(keycloakId));
    }

    @PostMapping("/users/me/addresses")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AddressResponse> addAddress(
            @Valid @RequestBody AddressRequest request) {
        UUID keycloakId = SecurityUtils.getCurrentUserId();
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(userService.addAddress(keycloakId, request));
    }

    @PutMapping("/users/me/addresses/{addressId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AddressResponse> updateAddress(
            @PathVariable UUID addressId,
            @Valid @RequestBody AddressRequest request) {
        UUID keycloakId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(
            userService.updateAddress(keycloakId, addressId, request));
    }

    @PatchMapping("/users/me/addresses/{addressId}/default")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AddressResponse> setDefault(
            @PathVariable UUID addressId) {
        UUID keycloakId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(
            userService.setDefaultAddress(keycloakId, addressId));
    }

    @DeleteMapping("/users/me/addresses/{addressId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteAddress(@PathVariable UUID addressId) {
        UUID keycloakId = SecurityUtils.getCurrentUserId();
        userService.deleteAddress(keycloakId, addressId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/internal/users/{userId}/default-address")
    @PreAuthorize("hasRole('INTERNAL_SERVICE') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getDefaultAddress(
            @PathVariable UUID userId) {
        return ResponseEntity.ok(userService.getDefaultAddressForOrder(userId));
    }

    @GetMapping("/admin/users/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserProfileResponse> getUserById(
            @PathVariable UUID userId) {
        return ResponseEntity.ok(userService.getProfileById(userId));
    }
}
