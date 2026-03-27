package com.ecommerce.identity.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true)
    private UUID keycloakId;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private UserStatus status = UserStatus.PENDING_VERIFICATION;

    @Column(nullable = false)
    @Builder.Default
    private Boolean emailVerified = false;

    @Column(nullable = false)
    @Builder.Default
    private Integer failedLoginCount = 0;

    private Instant lockedUntil;
    private Instant lastLoginAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE && !isLocked();
    }

    public void recordSuccessfulLogin() {
        this.lastLoginAt = Instant.now();
        this.failedLoginCount = 0;
        this.lockedUntil = null;
    }

    public void recordFailedLogin() {
        this.failedLoginCount++;
        // Lock account for 15 minutes after 5 failed attempts
        if (this.failedLoginCount >= 5) {
            this.lockedUntil = Instant.now().plusSeconds(900);
        }
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }
}
