package com.ecommerce.shipping.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "shipments")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID orderId;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, unique = true)
    private String trackingNumber;

    @Column(nullable = false)
    @Builder.Default
    private String carrier = "BlueDart";

    @Column(nullable = false)
    @Builder.Default
    private String status = "CREATED";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> shippingAddress;

    private LocalDate estimatedDeliveryDate;
    private LocalDate actualDeliveryDate;

    @Builder.Default
    private Instant createdAt = Instant.now();

    @UpdateTimestamp
    private Instant updatedAt;

    private Instant shippedAt;
    private Instant deliveredAt;
}
