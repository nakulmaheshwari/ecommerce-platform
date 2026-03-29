package com.ecommerce.cart.service;

import com.ecommerce.cart.api.dto.*;
import com.ecommerce.cart.client.ProductCatalogClient;
import com.ecommerce.cart.client.dto.ProductDto;
import com.ecommerce.cart.domain.*;
import com.ecommerce.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartService {

    private final CartRepository cartRepository;
    private final ProductCatalogClient productCatalogClient;

    /**
     * Add item to cart.
     *
     * Flow:
     * 1. Fetch product from catalog (validates it exists and is ACTIVE)
     * 2. Check if item already in cart (update qty vs new item)
     * 3. Build snapshot from product data
     * 4. Store in Redis Hash
     *
     * Why we fetch from Product Catalog and not trust client data:
     * Security. A client could send pricePaise=1 to get any item for ₹0.01.
     * Always fetch price from the trusted service.
     */
    public CartResponse addItem(UUID userId, AddToCartRequest request) {
        // Fetch live product data — this call is circuit-breaker protected
        ProductDto product = productCatalogClient.getProductBySku(request.skuId());

        if (!"ACTIVE".equals(product.status())) {
            throw new IllegalStateException(
                "Product is not available: " + request.skuId());
        }

        // Find the matching variant
        ProductDto.VariantDto variant = product.findVariant(request.skuId());
        long pricePaise = variant != null ? variant.pricePaise() : product.pricePaise();
        String variantName = variant != null ? variant.name() : null;
        Map<String, String> attributes = variant != null ? variant.attributes() : Map.of();

        // Check if item already exists in cart
        Optional<CartItem> existing = cartRepository.getItem(userId, request.skuId());

        CartItem item;
        if (existing.isPresent()) {
            // Update quantity — keep original addedAt timestamp
            item = existing.get();
            item.setQuantity(item.getQuantity() + request.quantity());
            item.setUpdatedAt(Instant.now());
            // Re-snapshot the price — if price dropped since last add, customer benefits
            item.setPricePaise(pricePaise);
            item.setMrpPaise(product.mrpPaise());
        } else {
            // New item — full snapshot
            item = CartItem.builder()
                .skuId(request.skuId())
                .productId(product.id())
                .productName(product.name())
                .variantName(variantName)
                .quantity(request.quantity())
                .pricePaise(pricePaise)
                .mrpPaise(product.mrpPaise())
                .imageUrl(product.primaryImageUrl())
                .brand(product.brand())
                .attributes(attributes)
                .addedAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        }

        cartRepository.saveItem(userId, item);
        log.info("Item added to cart userId={} skuId={} qty={}",
            userId, request.skuId(), item.getQuantity());

        return getCart(userId);
    }

    /**
     * Update item quantity.
     * quantity = 0 removes the item entirely.
     * This matches the UX pattern of a quantity stepper that goes to 0 = remove.
     */
    public CartResponse updateItem(UUID userId, String skuId, UpdateCartItemRequest request) {
        if (request.quantity() == 0) {
            return removeItem(userId, skuId);
        }

        CartItem existing = cartRepository.getItem(userId, skuId)
            .orElseThrow(() ->
                new ResourceNotFoundException("CartItem", skuId));

        existing.setQuantity(request.quantity());
        existing.setUpdatedAt(Instant.now());
        cartRepository.saveItem(userId, existing);

        return getCart(userId);
    }

    public CartResponse removeItem(UUID userId, String skuId) {
        cartRepository.removeItem(userId, skuId);
        log.info("Item removed from cart userId={} skuId={}", userId, skuId);
        return getCart(userId);
    }

    public CartResponse getCart(UUID userId) {
        List<CartItem> items = cartRepository.getItems(userId);
        Cart cart = Cart.builder().userId(userId).items(items).build();
        return toResponse(cart);
    }

    /**
     * Called by Order Service during checkout.
     *
     * Returns raw CartItems (not the HTTP response DTO) so
     * the Order Service gets the data it needs to create order items.
     * After checkout, the cart is cleared.
     */
    public List<CartItem> getCartForCheckout(UUID userId) {
        List<CartItem> items = cartRepository.getItems(userId);
        if (items.isEmpty()) {
            throw new IllegalStateException("Cannot checkout with empty cart");
        }
        return items;
    }

    public void clearCart(UUID userId) {
        cartRepository.clearCart(userId);
    }

    // ─────────────────────────────────────────────
    // PRIVATE: assemble response DTO
    // ─────────────────────────────────────────────

    private CartResponse toResponse(Cart cart) {
        List<CartResponse.CartItemResponse> itemResponses = cart.getItems().stream()
            .map(item -> new CartResponse.CartItemResponse(
                item.getSkuId(),
                item.getProductId(),
                item.getProductName(),
                item.getVariantName(),
                item.getQuantity(),
                item.getPricePaise(),
                item.getPricePaise() / 100.0,
                item.getMrpPaise(),
                item.discountPercent(),
                item.totalPaise(),
                item.totalPaise() / 100.0,
                item.getImageUrl(),
                item.getBrand(),
                item.getAttributes()
            ))
            .collect(Collectors.toList());

        return new CartResponse(
            cart.getUserId(),
            itemResponses,
            cart.totalItems(),
            cart.subtotalPaise(),
            cart.subtotalPaise() / 100.0,
            cart.totalSavingsPaise(),
            cart.totalSavingsPaise() / 100.0
        );
    }
}
