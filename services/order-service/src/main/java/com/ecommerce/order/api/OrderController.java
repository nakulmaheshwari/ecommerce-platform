package com.ecommerce.order.api;

import com.ecommerce.common.security.SecurityUtils;
import com.ecommerce.order.api.dto.*;
import com.ecommerce.order.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class OrderController {

    private final OrderService orderService;

    /**
     * POST /api/v1/orders — place an order.
     *
     * HttpServletRequest is injected to extract the Authorization header.
     * We forward this JWT to Cart Service so it can identify the user.
     * This is the "header forwarding" pattern for service-to-service calls
     * on behalf of a user.
     */
    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(
            @Valid @RequestBody PlaceOrderRequest request,
            HttpServletRequest httpRequest) {

        UUID userId = SecurityUtils.getCurrentUserId();
        String authHeader = httpRequest.getHeader("Authorization");

        OrderResponse response = orderService.placeOrder(userId, request, authHeader);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable UUID orderId) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(orderService.getOrder(orderId, userId));
    }

    @GetMapping
    public ResponseEntity<Page<OrderResponse>> getMyOrders(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(orderService.getMyOrders(userId, page, size));
    }
}
