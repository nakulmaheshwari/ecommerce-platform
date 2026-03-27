package com.ecommerce.common.event;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Getter
@SuperBuilder
@NoArgsConstructor
@JsonTypeName("payment.succeeded")
public class PaymentSucceededEvent extends DomainEvent {
    public static final String TOPIC = "payment-succeeded";
    public static final String VERSION = "1.0";

    private UUID orderId;
    private UUID paymentId;
    private String pspPaymentId;
    private Long amountPaise;
    private String currency;
}
