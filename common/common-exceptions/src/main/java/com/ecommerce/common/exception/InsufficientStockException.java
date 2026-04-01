package com.ecommerce.common.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * Specifically for "All-or-Nothing" inventory operations.
 * When thrown inside a @Transactional method, it triggers a full DB rollback.
 */
public class InsufficientStockException extends BaseException {

    public InsufficientStockException(String skuId) {
        super("INSUFFICIENT_STOCK", String.format("Insufficient stock for SKU: %s", skuId), 409);
    }

    public InsufficientStockException(UUID orderId) {
        super("INSUFFICIENT_STOCK", String.format("Insufficient stock for order: %s. Batch rollback triggered.", orderId), 409);
    }
}
