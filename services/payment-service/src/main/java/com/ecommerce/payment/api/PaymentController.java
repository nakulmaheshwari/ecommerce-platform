package com.ecommerce.payment.api;

import com.ecommerce.payment.api.dto.*;
import com.ecommerce.payment.service.PaymentService;
import com.ecommerce.payment.webhook.RazorpayWebhookHandler;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;
    private final RazorpayWebhookHandler webhookHandler;

    @PostMapping("/initiate")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_ADMIN')")
    public ResponseEntity<InitiatePaymentResponse> initiatePayment(
            @Valid @RequestBody InitiatePaymentRequest request) {
        log.info("API: Initiate payment orderId={} amountPaise={}",
            request.orderId(), request.amountPaise());
        return ResponseEntity.ok(paymentService.initiatePayment(request));
    }

    @GetMapping("/order/{orderId}")
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_ADMIN')")
    public ResponseEntity<PaymentResponse> getPaymentByOrder(
            @PathVariable UUID orderId) {
        return ResponseEntity.ok(paymentService.getPaymentForOrder(orderId));
    }

    @PostMapping("/refund/{orderId}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<PaymentResponse> refundOrder(
            @PathVariable UUID orderId,
            @Valid @RequestBody RefundRequest request) {
        log.info("API: Refund orderId={} amountPaise={} reason={}",
            orderId, request.amountPaise(), request.reason());
        return ResponseEntity.ok(paymentService.processRefund(
            orderId, request.amountPaise(), request.reason(), "admin"));
    }

    /**
     * Razorpay Webhook Endpoint.
     * This must be public and signature verified.
     */
    @PostMapping("/webhook/razorpay")
    public ResponseEntity<Void> handleRazorpayWebhook(
            @RequestBody String body,
            @RequestHeader("X-Razorpay-Signature") String signature,
            @RequestHeader(value = "X-Forwarded-For", required = false) String forwardedFor,
            @RequestHeader(value = "Remote-Addr",    required = false) String remoteAddr) {

        String sourceIp = (forwardedFor != null) ? forwardedFor : remoteAddr;

        boolean success = webhookHandler.handleWebhook(body, signature, sourceIp);

        if (success) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.status(401).build();
        }
    }
}
