package com.ecommerce.notification.service;

import com.ecommerce.notification.domain.*;
import com.ecommerce.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final EmailSender emailSender;

    /**
     * Process a notification-triggered event.
     *
     * IDEMPOTENCY: idempotency key = "{templateId}:{referenceId}"
     * If this key exists in the DB, the notification was already sent — skip.
     *
     * TEMPLATE VARIABLE ROUTING:
     * The event carries templateVars as a Map<String, String>.
     * These are injected into the Thymeleaf template at render time.
     * The event producer decides what variables to include — this service
     * is intentionally decoupled from business domain knowledge.
     */
    @Transactional
    public void processNotification(UUID userId,
                                    String channel,
                                    String templateId,
                                    Map<String, String> templateVars,
                                    String recipient,
                                    String referenceId) {

        String idempotencyKey = templateId + ":" + referenceId;

        if (notificationRepository.existsByIdempotencyKey(idempotencyKey)) {
            log.warn("Duplicate notification skipped key={}", idempotencyKey);
            return;
        }

        NotificationChannel notifChannel;
        try {
            notifChannel = NotificationChannel.valueOf(channel.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.error("Unknown notification channel={}", channel);
            return;
        }

        String subject = buildSubject(templateId, templateVars);

        Notification notification = Notification.builder()
            .idempotencyKey(idempotencyKey)
            .userId(userId)
            .channel(notifChannel)
            .templateId(templateId)
            .recipient(recipient)
            .subject(subject)
            .payloadSnapshot(templateVars)
            .status(NotificationStatus.PENDING)
            .build();

        notificationRepository.save(notification);
        log.info("Notification queued id={} templateId={} channel={}",
            notification.getId(), templateId, channel);

        sendNotification(notification);
    }

    private void sendNotification(Notification notification) {
        try {
            switch (notification.getChannel()) {
                case EMAIL -> emailSender.send(
                    notification.getRecipient(),
                    notification.getSubject(),
                    notification.getTemplateId(),
                    notification.getPayloadSnapshot()
                );
                case SMS -> log.info("SMS send not implemented — skipping recipient={}",
                    notification.getRecipient());
                case PUSH -> log.info("Push send not implemented — skipping userId={}",
                    notification.getUserId());
            }

            notification.markSent();
            notificationRepository.save(notification);
            log.info("Notification sent id={} to={}", notification.getId(), notification.getRecipient());

        } catch (Exception e) {
            notification.markFailed(e.getMessage());
            notificationRepository.save(notification);
            log.error("Notification send failed id={} attempt={} nextRetry={}",
                notification.getId(), notification.getRetryCount(), notification.getNextRetryAt(), e);
        }
    }

    /**
     * Retry scheduler — runs every 2 minutes.
     * Picks up all PENDING notifications where nextRetryAt <= NOW.
     */
    @Scheduled(fixedDelay = 120_000)
    @Transactional
    public void retryFailedNotifications() {
        List<Notification> due = notificationRepository.findDueForProcessing(Instant.now());
        if (due.isEmpty()) return;
        log.info("Retrying {} notifications", due.size());
        due.forEach(this::sendNotification);
    }

    private String buildSubject(String templateId, Map<String, String> vars) {
        return switch (templateId) {
            case "order-confirmed-v1"   -> "Your order #" + vars.getOrDefault("orderId", "") + " is confirmed!";
            case "payment-confirmed-v1" -> "Payment received for order #" + vars.getOrDefault("orderId", "");
            case "order-shipped-v1"     -> "Your order #" + vars.getOrDefault("orderId", "") + " has shipped!";
            case "order-cancelled-v1"   -> "Your order #" + vars.getOrDefault("orderId", "") + " was cancelled";
            case "user-registered-v1"   -> "Welcome to EcomPlatform!";
            default -> "Notification from EcomPlatform";
        };
    }
}
