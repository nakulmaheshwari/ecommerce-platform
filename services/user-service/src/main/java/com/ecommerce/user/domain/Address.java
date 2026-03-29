package com.ecommerce.user.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "addresses")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserProfile userProfile;

    @Column(nullable = false)
    @Builder.Default
    private String label = "Home";

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String phoneNumber;

    @Column(nullable = false)
    private String line1;

    private String line2;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private String state;

    @Column(nullable = false)
    private String pincode;

    @Column(nullable = false)
    @Builder.Default
    private String country = "India";

    @Column(nullable = false)
    @Builder.Default
    private Boolean isDefault = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    public void softDelete() {
        this.isDeleted = true;
        this.isDefault = false;
    }
}
