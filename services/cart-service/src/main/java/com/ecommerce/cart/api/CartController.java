package com.ecommerce.cart.api;

import com.ecommerce.cart.api.dto.AddToCartRequest;
import com.ecommerce.cart.api.dto.CartResponse;
import com.ecommerce.cart.api.dto.UpdateCartItemRequest;
import com.ecommerce.cart.service.CartService;
import com.ecommerce.common.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * All cart endpoints are scoped to the currently authenticated user.
 * No user can read or modify another user's cart.
 * The userId always comes from the JWT (SecurityUtils) — never from the URL or request body.
 *
 * This is a security principle: the resource owner is always determined
 * by the authenticated identity, not by a client-supplied parameter.
 */
@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")  // All cart endpoints require login
public class CartController {

    private final CartService cartService;

    @GetMapping
    public ResponseEntity<CartResponse> getCart() {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(cartService.getCart(userId));
    }

    @PostMapping("/items")
    public ResponseEntity<CartResponse> addItem(
            @Valid @RequestBody AddToCartRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(cartService.addItem(userId, request));
    }

    @PutMapping("/items/{skuId}")
    public ResponseEntity<CartResponse> updateItem(
            @PathVariable String skuId,
            @Valid @RequestBody UpdateCartItemRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(cartService.updateItem(userId, skuId, request));
    }

    @DeleteMapping("/items/{skuId}")
    public ResponseEntity<CartResponse> removeItem(@PathVariable String skuId) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(cartService.removeItem(userId, skuId));
    }

    @DeleteMapping
    public ResponseEntity<Void> clearCart() {
        UUID userId = SecurityUtils.getCurrentUserId();
        cartService.clearCart(userId);
        return ResponseEntity.noContent().build();
    }
}
