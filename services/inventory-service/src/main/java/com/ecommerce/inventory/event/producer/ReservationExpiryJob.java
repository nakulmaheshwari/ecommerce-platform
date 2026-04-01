package com.ecommerce.inventory.event.producer;

import com.ecommerce.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled maintenance job that recovers stock from abandoned checkouts.
 * 
 * If an order is placed but payment is never completed, the stock remains 
 * in the 'reserved' pool 'HELD' status indefinitely. This job ensures 
 * such stock is returned to the 'available' pool after the 15-minute lease expires.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReservationExpiryJob {

    private final InventoryService inventoryService;

    /**
     * Periodically triggers the expiry logic in the service layer.
     * Runs every 2 minutes (120,000 ms).
     */
    @Scheduled(fixedDelay = 120_000)
    public void expireStaleReservations() {
        log.debug("Starting scheduled reservation expiry scan...");
        inventoryService.expireStaleReservations();
    }
}
