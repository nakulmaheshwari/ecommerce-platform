package com.ecommerce.user.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Entity
@Table(name = "user_profiles")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID keycloakId;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    private String phoneNumber;
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    private String avatarUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> preferences = new HashMap<>();

    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @OneToMany(mappedBy = "userProfile", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Address> addresses = new ArrayList<>();

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public Optional<Address> getDefaultAddress() {
        return addresses.stream()
            .filter(a -> a.getIsDefault() && !a.getIsDeleted())
            .findFirst();
    }

    public List<Address> getActiveAddresses() {
        return addresses.stream()
            .filter(a -> !a.getIsDeleted())
            .toList();
    }

    public void setDefaultAddress(UUID addressId) {
        addresses.forEach(a -> {
            if (!a.getIsDeleted()) {
                a.setIsDefault(a.getId().equals(addressId));
            }
        });
    }

    public void updatePreference(String key, Object value) {
        this.preferences.put(key, value);
    }
}
