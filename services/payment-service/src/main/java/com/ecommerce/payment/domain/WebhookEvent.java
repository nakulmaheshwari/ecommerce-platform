package com.ecommerce.payment.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "webhook_events")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class WebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String razorpayEventId;

    @Column(nullable = false)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> payload;

    @Column(nullable = false)
    private Boolean signatureValid;

    @Column(nullable = false)
    @Builder.Default
    private Boolean processed = false;

    private String processingError;
    private String sourceIp;

    @Builder.Default
    private Instant receivedAt = Instant.now();

    private Instant processedAt;
}
