package com.ecommerce.payment.repository;

import com.ecommerce.payment.domain.PaymentTransaction;
import com.ecommerce.payment.domain.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentTransactionRepository
        extends JpaRepository<PaymentTransaction, UUID> {

    Optional<PaymentTransaction> findByIdempotencyKey(UUID idempotencyKey);

    Optional<PaymentTransaction> findByOrderId(UUID orderId);

    Optional<PaymentTransaction> findByRazorpayOrderId(String razorpayOrderId);

    Optional<PaymentTransaction> findByRazorpayPaymentId(String razorpayPaymentId);

    boolean existsByOrderIdAndStatus(UUID orderId, PaymentStatus status);
}
