package com.ecommerce.payment.webhook;

import com.ecommerce.payment.domain.WebhookEvent;
import com.ecommerce.payment.repository.WebhookEventRepository;
import com.ecommerce.payment.service.PaymentService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ecommerce.payment.razorpay.RazorpayGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class RazorpayWebhookHandler {

    private final RazorpayGateway razorpayGateway;
    private final WebhookEventRepository webhookEventRepository;
    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    @Transactional
    public boolean handleWebhook(String rawBody, String signature, String sourceIp) {

        boolean signatureValid = razorpayGateway.verifyWebhookSignature(rawBody, signature);
        if (!signatureValid) {
            log.warn("SECURITY: Invalid webhook signature from IP={}", sourceIp);
            logInvalidWebhook(rawBody, signature, sourceIp);
            return false;
        }

        try {
            JsonNode root = objectMapper.readTree(rawBody);
            String eventType = root.path("event").asText();
            String eventId   = root.path("id").asText();

            log.info("Webhook received eventId={} eventType={} sourceIp={}",
                eventId, eventType, sourceIp);

            if (webhookEventRepository.existsByRazorpayEventId(eventId)) {
                log.info("Duplicate webhook eventId={} — already processed", eventId);
                return true;
            }

            Map<String, Object> payloadMap = objectMapper.convertValue(
                root, new TypeReference<>() {});

            WebhookEvent webhookRecord = WebhookEvent.builder()
                .razorpayEventId(eventId)
                .eventType(eventType)
                .payload(payloadMap)
                .signatureValid(true)
                .sourceIp(sourceIp)
                .build();
            webhookEventRepository.save(webhookRecord);

            switch (eventType) {
                case "payment.captured" ->
                    handlePaymentCaptured(root, payloadMap, webhookRecord);
                case "payment.failed" ->
                    handlePaymentFailed(root, payloadMap, webhookRecord);
                case "refund.processed" ->
                    handleRefundProcessed(root, webhookRecord);
                default ->
                    log.info("Unhandled webhook eventType={} — acknowledged", eventType);
            }

            webhookRecord.setProcessed(true);
            webhookRecord.setProcessedAt(Instant.now());
            webhookEventRepository.save(webhookRecord);

            return true;

        } catch (Exception e) {
            log.error("Webhook processing failed rawBody={}", rawBody, e);
            return true;
        }
    }

    private void handlePaymentCaptured(JsonNode root,
                                        Map<String, Object> fullPayload,
                                        WebhookEvent webhookRecord) {
        JsonNode paymentEntity = root
            .path("payload")
            .path("payment")
            .path("entity");

        String razorpayPaymentId = paymentEntity.path("id").asText();
        String razorpayOrderId   = paymentEntity.path("order_id").asText();
        long   amountPaise       = paymentEntity.path("amount").asLong();
        String signature         = root.path("payload").path("payment")
                                       .path("signature").asText("");

        log.info("Processing payment.captured razorpayPaymentId={} razorpayOrderId={}",
            razorpayPaymentId, razorpayOrderId);

        paymentService.processCapturedWebhook(
            razorpayPaymentId,
            razorpayOrderId,
            signature,
            amountPaise,
            fullPayload
        );
    }

    private void handlePaymentFailed(JsonNode root,
                                      Map<String, Object> fullPayload,
                                      WebhookEvent webhookRecord) {
        JsonNode paymentEntity = root
            .path("payload")
            .path("payment")
            .path("entity");

        String razorpayOrderId   = paymentEntity.path("order_id").asText();
        String razorpayPaymentId = paymentEntity.path("id").asText();
        JsonNode errorObj        = paymentEntity.path("error");
        String errorCode         = errorObj.path("code").asText("PAYMENT_FAILED");
        String errorDescription  = errorObj.path("description").asText("Payment was declined");

        log.info("Processing payment.failed razorpayOrderId={} code={}",
            razorpayOrderId, errorCode);

        paymentService.processFailedWebhook(
            razorpayOrderId,
            razorpayPaymentId,
            errorCode,
            errorDescription,
            fullPayload
        );
    }

    private void handleRefundProcessed(JsonNode root, WebhookEvent webhookRecord) {
        String refundId = root.path("payload").path("refund")
                              .path("entity").path("id").asText();
        log.info("Refund processed razorpayRefundId={}", refundId);
    }

    private void logInvalidWebhook(String rawBody, String signature, String sourceIp) {
        try {
            WebhookEvent invalidEvent = WebhookEvent.builder()
                .razorpayEventId("INVALID-" + System.currentTimeMillis())
                .eventType("INVALID_SIGNATURE")
                .payload(Map.of("rawBody", rawBody, "signature", signature))
                .signatureValid(false)
                .sourceIp(sourceIp)
                .build();
            webhookEventRepository.save(invalidEvent);
        } catch (Exception e) {
            log.error("Failed to log invalid webhook", e);
        }
    }
}
