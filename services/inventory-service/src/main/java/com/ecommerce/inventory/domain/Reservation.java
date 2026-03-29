package com.ecommerce.inventory.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reservations")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String skuId;

    @Column(nullable = false)
    private UUID orderId;

    @Column(nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ReservationStatus status = ReservationStatus.HELD;

    @Column(nullable = false)
    @Builder.Default
    private Instant expiresAt = Instant.now().plusSeconds(900); // 15 min hold

    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    public boolean isExpired() {
        return status == ReservationStatus.HELD &&
               expiresAt.isBefore(Instant.now());
    }
}
