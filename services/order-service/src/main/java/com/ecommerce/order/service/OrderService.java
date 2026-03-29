package com.ecommerce.order.service;

import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.order.api.dto.*;
import com.ecommerce.order.client.*;
import com.ecommerce.order.client.dto.*;
import com.ecommerce.order.domain.*;
import com.ecommerce.order.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final OutboxRepository outboxRepository;
    private final CartServiceClient cartClient;
    private final InventoryServiceClient inventoryClient;

    /**
     * PLACE ORDER — the most critical method in the platform.
     *
     * Step-by-step:
     * 1. IDEMPOTENCY CHECK — if this key exists, return existing order
     * 2. FETCH CART — get items from Cart Service via Feign
     * 3. CHECK AVAILABILITY — synchronous check against Inventory Service
     * 4. CALCULATE TOTALS — server-side, never trust client prices
     * 5. CREATE ORDER — write to DB (status = PENDING)
     * 6. WRITE OUTBOX EVENT — same transaction as step 5
     * 7. CLEAR CART — non-critical, async
     *
     * Steps 5 and 6 are in ONE transaction.
     * If EITHER fails, BOTH roll back. This is the Transactional Outbox pattern.
     *
     * Steps 2, 3, 7 are outside the transaction to avoid
     * holding a DB connection during network calls.
     */
    public OrderResponse placeOrder(UUID userId, PlaceOrderRequest request,
                                    String authHeader) {
        // ── Step 1: Idempotency check ──
        Optional<Order> existingOrder =
            orderRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existingOrder.isPresent()) {
            log.info("Returning existing order for idempotency key={}",
                request.idempotencyKey());
            return toResponse(existingOrder.get());
        }

        // ── Step 2: Fetch cart ──
        List<CartItemDto> cartItems = cartClient.getCartForCheckout(authHeader);
        if (cartItems.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Cannot place order with empty cart");
        }

        // ── Step 3: Availability check ──
        Map<String, Integer> skuQty = cartItems.stream()
            .collect(Collectors.toMap(CartItemDto::skuId, CartItemDto::quantity));

        AvailabilityResponse availability = inventoryClient.checkAvailability(skuQty);
        if (!availability.allAvailable()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Some items are out of stock: " + availability.unavailableSkus());
        }

        // ── Step 4: Calculate totals (server-side — never trust client) ──
        long subtotalPaise = cartItems.stream()
            .mapToLong(i -> (long) i.quantity() * i.pricePaise())
            .sum();
        long taxPaise = calculateTax(subtotalPaise);
        long deliveryPaise = calculateDelivery(subtotalPaise);
        long totalPaise = subtotalPaise + taxPaise + deliveryPaise;

        // ── Steps 5 + 6: Create order + outbox event IN ONE TRANSACTION ──
        Order order = createOrderTransactional(
            userId, request, cartItems,
            subtotalPaise, taxPaise, deliveryPaise, totalPaise
        );

        // ── Step 7: Clear cart (non-critical — best effort) ──
        try {
            cartClient.clearCart(authHeader);
        } catch (Exception e) {
            // Non-fatal. Cart expires via TTL. Log and continue.
            log.warn("Failed to clear cart for userId={} — will expire via TTL", userId);
        }

        log.info("Order placed orderId={} userId={} totalPaise={}",
            order.getId(), userId, totalPaise);

        return toResponse(order);
    }

    /**
     * This method is @Transactional — the DB transaction boundary.
     *
     * Everything in here is ONE atomic unit:
     *   - orders INSERT
     *   - order_items INSERT (batch)
     *   - order_status_history INSERT
     *   - outbox_events INSERT
     *
     * All succeed together or all roll back together.
     * No partial state possible.
     */
    @Transactional
    protected Order createOrderTransactional(
            UUID userId, PlaceOrderRequest request,
            List<CartItemDto> cartItems,
            long subtotalPaise, long taxPaise,
            long deliveryPaise, long totalPaise) {

        // Build shipping address map from DTO
        var addr = request.shippingAddress();
        Map<String, Object> shippingAddr = new LinkedHashMap<>();
        shippingAddr.put("fullName", addr.fullName());
        shippingAddr.put("line1",    addr.line1());
        shippingAddr.put("line2",    addr.line2());
        shippingAddr.put("city",     addr.city());
        shippingAddr.put("state",    addr.state());
        shippingAddr.put("pincode",  addr.pincode());
        shippingAddr.put("country",  addr.country());
        shippingAddr.put("phone",    addr.phone());

        // Build order items from cart item snapshots
        List<OrderItem> items = cartItems.stream()
            .map(ci -> OrderItem.builder()
                .skuId(ci.skuId())
                .productId(ci.productId())
                .productName(ci.productName())
                .variantName(ci.variantName())
                .quantity(ci.quantity())
                .unitPricePaise(ci.pricePaise())
                .mrpPaise(ci.mrpPaise())
                .imageUrl(ci.imageUrl())
                .build())
            .collect(Collectors.toList());

        Order order = Order.builder()
            .userId(userId)
            .idempotencyKey(request.idempotencyKey())
            .status(OrderStatus.PENDING)
            .subtotalPaise(subtotalPaise)
            .taxPaise(taxPaise)
            .deliveryPaise(deliveryPaise)
            .totalPaise(totalPaise)
            .shippingAddress(shippingAddr)
            .notes(request.notes())
            .items(items)
            .build();

        // Set orderId on each item (bi-directional reference)
        items.forEach(item -> item.setOrderId(order.getId()));

        orderRepository.save(order);

        // Write initial status history
        historyRepository.save(OrderStatusHistory.initial(order.getId(), "user:" + userId));

        // Write outbox event — SAME TRANSACTION
        // This event triggers the entire downstream saga:
        //   Inventory Service → reserve stock
        //   Payment Service   → initiate payment (after inventory confirms)
        outboxRepository.save(OutboxEvent.builder()
            .aggregateType("Order")
            .aggregateId(order.getId())
            .eventType("order.placed")
            .payload(buildOrderPlacedPayload(order, items))
            .build());

        return order;
    }

    /**
     * Called when payment.succeeded Kafka event arrives.
     * Transitions order AWAITING_PAYMENT → CONFIRMED.
     */
    @Transactional
    public void handlePaymentSucceeded(UUID orderId, UUID paymentId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new ResourceNotFoundException("Order", orderId.toString()));

        order.transitionTo(OrderStatus.CONFIRMED, "service:payment",
            "Payment succeeded paymentId=" + paymentId);
        orderRepository.save(order);

        historyRepository.save(OrderStatusHistory.builder()
            .orderId(orderId)
            .fromStatus(OrderStatus.AWAITING_PAYMENT)
            .toStatus(OrderStatus.CONFIRMED)
            .reason("Payment confirmed")
            .actor("service:payment")
            .metadata(Map.of("paymentId", paymentId.toString()))
            .build());

        // Trigger notification
        outboxRepository.save(OutboxEvent.builder()
            .aggregateType("Order")
            .aggregateId(orderId)
            .eventType("notification.triggered")
            .payload(Map.of(
                "userId",     order.getUserId().toString(),
                "channel",    "EMAIL",
                "templateId", "order-confirmed-v1",
                "templateVars", Map.of(
                    "orderId",     orderId.toString(),
                    "totalRupees", String.valueOf(order.getTotalPaise() / 100.0)
                )
            ))
            .build());

        log.info("Order confirmed orderId={} paymentId={}", orderId, paymentId);
    }

    /**
     * Called when payment.failed Kafka event arrives.
     * Transitions order AWAITING_PAYMENT → CANCELLED.
     * Inventory Service handles releasing stock separately via its own consumer.
     */
    @Transactional
    public void handlePaymentFailed(UUID orderId, String failureReason) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new ResourceNotFoundException("Order", orderId.toString()));

        order.transitionTo(OrderStatus.CANCELLED, "service:payment",
            "Payment failed: " + failureReason);
        orderRepository.save(order);

        historyRepository.save(OrderStatusHistory.builder()
            .orderId(orderId)
            .fromStatus(OrderStatus.AWAITING_PAYMENT)
            .toStatus(OrderStatus.CANCELLED)
            .reason("Payment failed: " + failureReason)
            .actor("service:payment")
            .build());

        log.info("Order cancelled orderId={} reason={}", orderId, failureReason);
    }

    // ─────────────────────────────────────────────
    // READ OPERATIONS
    // ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID orderId, UUID requestingUserId) {
        Order order = orderRepository.findByIdWithItems(orderId)
            .orElseThrow(() -> new ResourceNotFoundException("Order", orderId.toString()));

        // Authorization: users can only see their own orders
        // Admins can see any order (checked separately)
        if (!order.isOwnedBy(requestingUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Access denied to order " + orderId);
        }

        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getMyOrders(UUID userId, int page, int size) {
        return orderRepository
            .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size))
            .map(this::toResponse);
    }

    // ─────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────

    private long calculateTax(long subtotalPaise) {
        // 18% GST on the subtotal — simplified
        // In production this would be item-category-specific
        return (long) (subtotalPaise * 0.18);
    }

    private long calculateDelivery(long subtotalPaise) {
        // Free delivery above ₹500 (50,000 paise)
        return subtotalPaise >= 50_000L ? 0L : 4900L; // ₹49 delivery fee
    }

    private Map<String, Object> buildOrderPlacedPayload(
            Order order, List<OrderItem> items) {
        List<Map<String, Object>> itemPayloads = items.stream()
            .map(item -> {
                Map<String, Object> m = new HashMap<>();
                m.put("skuId",     item.getSkuId());
                m.put("productId", item.getProductId().toString());
                m.put("quantity",  item.getQuantity());
                m.put("pricePaise", item.getUnitPricePaise());
                return m;
            })
            .collect(Collectors.toList());

        return Map.of(
            "orderId",      order.getId().toString(),
            "userId",       order.getUserId().toString(),
            "totalPaise",   order.getTotalPaise(),
            "currency",     order.getCurrency(),
            "items",        itemPayloads
        );
    }

    private OrderResponse toResponse(Order order) {
        List<OrderResponse.OrderItemResponse> itemResponses =
            order.getItems().stream()
                .map(item -> new OrderResponse.OrderItemResponse(
                    item.getId(),
                    item.getSkuId(),
                    item.getProductId(),
                    item.getProductName(),
                    item.getVariantName(),
                    item.getQuantity(),
                    item.getUnitPricePaise(),
                    item.getUnitPricePaise() / 100.0,
                    item.lineTotalPaise(),
                    item.lineTotalPaise() / 100.0,
                    item.getImageUrl()
                ))
                .collect(Collectors.toList());

        return new OrderResponse(
            order.getId(),
            order.getUserId(),
            order.getStatus().name(),
            order.getCurrency(),
            order.getSubtotalPaise(),
            order.getSubtotalPaise() / 100.0,
            order.getDiscountPaise(),
            order.getTaxPaise(),
            order.getDeliveryPaise(),
            order.getTotalPaise(),
            order.getTotalPaise() / 100.0,
            order.getShippingAddress(),
            itemResponses,
            order.getNotes(),
            order.getCreatedAt(),
            order.getUpdatedAt()
        );
    }
}
