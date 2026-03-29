package com.ecommerce.order.domain;

import com.ecommerce.common.exception.BaseException;
import java.util.UUID;

public class InvalidOrderTransitionException extends BaseException {
    public InvalidOrderTransitionException(
            UUID orderId, OrderStatus from, OrderStatus to) {
        super("INVALID_ORDER_TRANSITION",
              "Order %s cannot transition from %s to %s".formatted(orderId, from, to),
              409);
    }
}
