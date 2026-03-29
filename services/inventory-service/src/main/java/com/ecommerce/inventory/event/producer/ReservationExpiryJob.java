package com.ecommerce.inventory.event.producer;

import com.ecommerce.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReservationExpiryJob {

    private final InventoryService inventoryService;

    // Run every 2 minutes — release stock held by abandoned checkouts
    @Scheduled(fixedDelay = 120_000)
    public void expireStaleReservations() {
        log.debug("Running reservation expiry job");
        inventoryService.expireStaleReservations();
    }
}
