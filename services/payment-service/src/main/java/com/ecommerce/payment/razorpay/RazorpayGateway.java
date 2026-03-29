package com.ecommerce.payment.razorpay;

import com.ecommerce.common.exception.ServiceUnavailableException;
import com.razorpay.Order;
import com.razorpay.Payment;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class RazorpayGateway {

    private final RazorpayClient razorpayClient;

    @Value("${razorpay.webhook-secret}")
    private String webhookSecret;

    public RazorpayOrderResponse createOrder(
            long amountPaise, String currency,
            String receiptId, UUID idempotencyKey) {

        long startMs = System.currentTimeMillis();
        try {
            JSONObject options = new JSONObject();
            options.put("amount",   amountPaise);
            options.put("currency", currency);
            options.put("receipt",  receiptId);

            JSONObject notes = new JSONObject();
            notes.put("order_id",        receiptId);
            notes.put("idempotency_key", idempotencyKey.toString());
            options.put("notes", notes);

            Order order = razorpayClient.orders.create(options);

            long durationMs = System.currentTimeMillis() - startMs;
            log.info("Razorpay order created razorpayOrderId={} amountPaise={} durationMs={}",
                order.get("id"), amountPaise, durationMs);

            return new RazorpayOrderResponse(
                order.get("id"),
                order.get("status"),
                amountPaise,
                currency
            );

        } catch (RazorpayException e) {
            long durationMs = System.currentTimeMillis() - startMs;
            log.error("Razorpay createOrder failed durationMs={} error={}",
                durationMs, e.getMessage());
            throw new ServiceUnavailableException("razorpay",
                "Failed to create payment order: " + e.getMessage());
        }
    }

    public PaymentDetails fetchPayment(String razorpayPaymentId) {
        try {
            Payment payment = razorpayClient.payments.fetch(razorpayPaymentId);
            return new PaymentDetails(
                payment.get("id"),
                payment.get("order_id"),
                payment.get("status"),
                ((Number) payment.get("amount")).longValue(),
                payment.get("currency"),
                payment.get("method"),
                payment.get("error_code"),
                payment.get("error_description")
            );
        } catch (RazorpayException e) {
            log.error("Failed to fetch payment razorpayPaymentId={}", razorpayPaymentId, e);
            throw new ServiceUnavailableException("razorpay", e.getMessage());
        }
    }

    public boolean verifyWebhookSignature(String payload, String signature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String computed = HexFormat.of().formatHex(hash);

            boolean valid = computed.equals(signature);
            if (!valid) {
                log.warn("Webhook signature verification FAILED — possible spoofing attempt");
            }
            return valid;
        } catch (Exception e) {
            log.error("Webhook signature verification error", e);
            return false;
        }
    }

    public record RazorpayOrderResponse(
        String razorpayOrderId,
        String status,
        long amountPaise,
        String currency
    ) {}

    public record PaymentDetails(
        String paymentId,
        String orderId,
        String status,
        long amount,
        String currency,
        String method,
        String errorCode,
        String errorDescription
    ) {}
}
