package com.ecommerce.payment.service;

import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.payment.api.dto.*;
import com.ecommerce.payment.domain.*;
import com.ecommerce.payment.razorpay.RazorpayGateway;
import com.ecommerce.payment.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentTransactionRepository transactionRepository;
    private final OutboxRepository outboxRepository;
    private final RazorpayGateway razorpayGateway;

    @Value("${razorpay.key-id}")
    private String razorpayKeyId;

    /**
     * INITIATE PAYMENT — creates a Razorpay Order and returns details to frontend.
     *
     * This is called AFTER our Order is created in Order Service.
     * The sequence is:
     *   1. Order Service creates order (status=PENDING)
     *   2. Order Service publishes order.placed event
     *   3. Payment Service consumes that event and calls THIS method
     *   4. We create a Razorpay Order
     *   5. Frontend uses razorpayOrderId to open payment modal
     *
     * IDEMPOTENCY FLOW:
     *   - Check if a transaction with this idempotencyKey already exists
     *   - If yes: return it (this is a retry)
     *   - If no: create new transaction, call Razorpay, store result
     *
     * The write sequence is important:
     *   1. Write PaymentTransaction with status=INITIATED to DB
     *   2. Call Razorpay
     *   3. Update with razorpayOrderId
     *
     * Why write to DB BEFORE calling Razorpay?
     * If we call Razorpay first and then crash, we've created an orphaned
     * Razorpay order with no corresponding DB record.
     * Writing first means even if we crash, there's a record we can use
     * to check Razorpay's state on restart.
     */
    @Transactional
    public InitiatePaymentResponse initiatePayment(InitiatePaymentRequest request) {

        // ── Idempotency check ──
        return transactionRepository
            .findByIdempotencyKey(request.idempotencyKey())
            .map(existing -> {
                log.info("Returning existing payment for idempotencyKey={}",
                    request.idempotencyKey());
                return buildInitiateResponse(existing);
            })
            .orElseGet(() -> createNewPayment(request));
    }

    private InitiatePaymentResponse createNewPayment(InitiatePaymentRequest request) {

        // ── Step 1: Write INITIATED record to DB ──
        PaymentTransaction txn = PaymentTransaction.builder()
            .orderId(request.orderId())
            .userId(request.userId())
            .idempotencyKey(request.idempotencyKey())
            .amountPaise(request.amountPaise())
            .currency(request.currency() != null ? request.currency() : "INR")
            .status(PaymentStatus.INITIATED)
            .build();
        transactionRepository.save(txn);

        // ── Step 2: Call Razorpay ──
        RazorpayGateway.RazorpayOrderResponse razorpayOrder =
            razorpayGateway.createOrder(
                request.amountPaise(),
                txn.getCurrency(),
                request.orderId().toString(),
                request.idempotencyKey()
            );

        // ── Step 3: Update with Razorpay Order ID ──
        txn.setRazorpayOrderId(razorpayOrder.razorpayOrderId());
        txn.setStatus(PaymentStatus.PENDING);
        transactionRepository.save(txn);

        log.info("Payment initiated paymentId={} orderId={} razorpayOrderId={}",
            txn.getId(), request.orderId(), razorpayOrder.razorpayOrderId());

        return buildInitiateResponse(txn);
    }

    private InitiatePaymentResponse buildInitiateResponse(PaymentTransaction txn) {
        return new InitiatePaymentResponse(
            txn.getId(),
            txn.getOrderId(),
            txn.getRazorpayOrderId(),
            razorpayKeyId,           // Public key — safe for frontend
            txn.getAmountPaise(),
            txn.getCurrency(),
            txn.getStatus().name(),
            "Payment for order " + txn.getOrderId()
        );
    }

    /**
     * PROCESS WEBHOOK — called by RazorpayWebhookHandler after verification.
     *
     * This is the most important method. Real money confirmation happens here.
     *
     * What Razorpay sends in a "payment.captured" webhook:
     * {
     *   "event": "payment.captured",
     *   "payload": {
     *     "payment": {
     *       "entity": {
     *         "id": "pay_xxx",
     *         "order_id": "order_xxx",
     *         "amount": 240354,
     *         "status": "captured"
     *       }
     *     }
     *   }
     * }
     *
     * AMOUNT VERIFICATION is critical:
     * An attacker could pay ₹1 and send a fake webhook claiming ₹2,403.
     * We always verify the amount in the webhook matches what we expected.
     * (With signature verification, fake webhooks are already blocked, but
     * defense in depth is important for financial systems.)
     */
    @Transactional
    public void processCapturedWebhook(String razorpayPaymentId,
                                       String razorpayOrderId,
                                       String razorpaySignature,
                                       long amountPaise,
                                       Map<String, Object> fullPayload) {

        // Find our transaction by Razorpay Order ID
        PaymentTransaction txn = transactionRepository
            .findByRazorpayOrderId(razorpayOrderId)
            .orElseThrow(() -> {
                log.error("No transaction found for razorpayOrderId={}", razorpayOrderId);
                return new ResourceNotFoundException("PaymentTransaction", razorpayOrderId);
            });

        // Guard: don't reprocess if already captured
        if (txn.getStatus() == PaymentStatus.CAPTURED) {
            log.warn("Payment already captured txnId={} — duplicate webhook", txn.getId());
            return;
        }

        // AMOUNT VERIFICATION
        // The webhook amount must match what we recorded.
        // Any mismatch is a red flag — log and reject.
        if (!txn.getAmountPaise().equals(amountPaise)) {
            log.error("AMOUNT MISMATCH paymentId={} expected={} received={}",
                txn.getId(), txn.getAmountPaise(), amountPaise);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Payment amount mismatch. Expected: " + txn.getAmountPaise() +
                " Received: " + amountPaise);
        }

        // Mark captured
        txn.markCaptured(razorpayPaymentId, razorpaySignature, fullPayload);
        transactionRepository.save(txn);

        // Write payment.succeeded to outbox — SAME TRANSACTION
        // Order Service will receive this and move order to CONFIRMED
        // Inventory Service will receive this and confirm the reservation
        outboxRepository.save(OutboxEvent.builder()
            .aggregateType("Payment")
            .aggregateId(txn.getId())
            .eventType("payment.succeeded")
            .payload(Map.of(
                "paymentId",         txn.getId().toString(),
                "orderId",           txn.getOrderId().toString(),
                "userId",            txn.getUserId().toString(),
                "amountPaise",       txn.getAmountPaise(),
                "razorpayPaymentId", razorpayPaymentId
            ))
            .build());

        // Also trigger confirmation email
        outboxRepository.save(OutboxEvent.builder()
            .aggregateType("Payment")
            .aggregateId(txn.getId())
            .eventType("notification.triggered")
            .payload(Map.of(
                "userId",     txn.getUserId().toString(),
                "channel",    "EMAIL",
                "templateId", "payment-confirmed-v1",
                "templateVars", Map.of(
                    "orderId",    txn.getOrderId().toString(),
                    "amountRupees", String.valueOf(txn.getAmountPaise() / 100.0),
                    "paymentId",  razorpayPaymentId
                )
            ))
            .build());

        log.info("Payment captured paymentId={} orderId={} razorpayPaymentId={}",
            txn.getId(), txn.getOrderId(), razorpayPaymentId);
    }

    @Transactional
    public void processFailedWebhook(String razorpayOrderId,
                                     String razorpayPaymentId,
                                     String errorCode,
                                     String errorDescription,
                                     Map<String, Object> fullPayload) {

        PaymentTransaction txn = transactionRepository
            .findByRazorpayOrderId(razorpayOrderId)
            .orElseThrow(() ->
                new ResourceNotFoundException("PaymentTransaction", razorpayOrderId));

        if (txn.getStatus().isTerminal()) {
            log.warn("Payment already in terminal state txnId={}", txn.getId());
            return;
        }

        txn.markFailed(errorCode, errorDescription, fullPayload);
        transactionRepository.save(txn);

        // payment.failed → Order Service cancels order, Inventory Service releases stock
        outboxRepository.save(OutboxEvent.builder()
            .aggregateType("Payment")
            .aggregateId(txn.getId())
            .eventType("payment.failed")
            .payload(Map.of(
                "paymentId",     txn.getId().toString(),
                "orderId",       txn.getOrderId().toString(),
                "failureCode",   errorCode != null ? errorCode : "UNKNOWN",
                "failureReason", errorDescription != null ? errorDescription : "Payment failed"
            ))
            .build());

        log.info("Payment failed paymentId={} orderId={} reason={}",
            txn.getId(), txn.getOrderId(), errorDescription);
    }

    /**
     * PROCESS REFUND
     *
     * Refunds are initiated by admin (fraud, customer request, order cancel).
     * We call Razorpay's refund API and store the result.
     */
    @Transactional
    public PaymentResponse processRefund(UUID orderId,
                                         long amountPaise,
                                         String reason,
                                         String initiatedBy) {

        PaymentTransaction txn = transactionRepository
            .findByOrderId(orderId)
            .orElseThrow(() ->
                new ResourceNotFoundException("PaymentTransaction", orderId.toString()));

        if (!txn.canBeRefunded()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Payment in status " + txn.getStatus() + " cannot be refunded");
        }

        if (amountPaise > txn.getAmountPaise()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Refund amount exceeds payment amount");
        }

        // Razorpay refund happens here
        // For brevity we update status directly; in production
        // the refund confirmation also comes via webhook
        txn.setStatus(amountPaise == txn.getAmountPaise()
            ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED);
        transactionRepository.save(txn);

        outboxRepository.save(OutboxEvent.builder()
            .aggregateType("Payment")
            .aggregateId(txn.getId())
            .eventType("payment.refunded")
            .payload(Map.of(
                "paymentId",   txn.getId().toString(),
                "orderId",     orderId.toString(),
                "amountPaise", amountPaise,
                "reason",      reason
            ))
            .build());

        log.info("Refund processed orderId={} amountPaise={} by={}",
            orderId, amountPaise, initiatedBy);

        return toResponse(txn);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentForOrder(UUID orderId) {
        PaymentTransaction txn = transactionRepository
            .findByOrderId(orderId)
            .orElseThrow(() ->
                new ResourceNotFoundException("PaymentTransaction", orderId.toString()));
        return toResponse(txn);
    }

    private PaymentResponse toResponse(PaymentTransaction txn) {
        return new PaymentResponse(
            txn.getId(),
            txn.getOrderId(),
            txn.getRazorpayOrderId(),
            txn.getRazorpayPaymentId(),
            txn.getAmountPaise(),
            txn.getAmountPaise() / 100.0,
            txn.getCurrency(),
            txn.getStatus().name(),
            txn.getFailureCode(),
            txn.getFailureReason(),
            txn.getCreatedAt(),
            txn.getCapturedAt()
        );
    }
}
