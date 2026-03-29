package com.ecommerce.order.client;

import com.ecommerce.order.client.dto.AvailabilityResponse;
import com.ecommerce.common.exception.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(
    name = "inventory-service",
    fallback = InventoryServiceClient.InventoryFallback.class
)
public interface InventoryServiceClient {

    /**
     * Synchronous availability check BEFORE creating the order.
     *
     * This is a pre-check, not a reservation. The actual reservation
     * happens asynchronously via the order-placed Kafka event consumed
     * by Inventory Service.
     *
     * Why check if we're reserving async anyway?
     * To give the user immediate feedback. If we skip this check, the user
     * places an order, waits, and then gets a cancellation email because
     * the item was out of stock. Bad UX and support cost.
     *
     * The check has a race condition window (between check and async reserve,
     * stock could be taken). The CHECK constraint in Inventory DB handles this.
     */
    @PostMapping("/api/v1/inventory/availability")
    @CircuitBreaker(name = "inventoryService")
    AvailabilityResponse checkAvailability(@RequestBody Map<String, Integer> skuQty);

    @Slf4j
    class InventoryFallback implements InventoryServiceClient {
        @Override
        public AvailabilityResponse checkAvailability(Map<String, Integer> skuQty) {
            // IMPORTANT: Do NOT return "available=true" as fallback here.
            // If inventory is down, we cannot confirm stock exists.
            // Reject the order — overselling is worse than a failed order.
            log.error("Inventory service unavailable — rejecting order");
            throw new ServiceUnavailableException("inventory-service",
                "Cannot verify stock availability. Please try again.");
        }
    }
}
