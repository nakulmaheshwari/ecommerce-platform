package com.ecommerce.seller.domain;

import com.ecommerce.seller.domain.enums.SellerStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "sellers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Seller {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "keycloak_id", unique = true, nullable = false)
    private String keycloakId;

    @Column(unique = true, nullable = false)
    private String email;

    private String phone;

    @Column(name = "business_name", nullable = false)
    private String businessName;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    private String description;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "pan_number", length = 10)
    private String panNumber;

    @Column(length = 15)
    private String gstin;

    @Column(name = "entity_type", nullable = false)
    private String entityType = "INDIVIDUAL";

    @Column(name = "address_line1")
    private String addressLine1;

    @Column(name = "address_line2")
    private String addressLine2;

    private String city;
    private String state;
    private String pincode;
    private String country = "India";

    @Column(name = "commission_rate_percent")
    private BigDecimal commissionRatePercent;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SellerStatus status = SellerStatus.PENDING_KYC;

    @Column(name = "average_rating")
    private BigDecimal averageRating = BigDecimal.ZERO;

    @Column(name = "total_ratings")
    private Integer totalRatings = 0;

    @Column(name = "total_sales_paise")
    private Long totalSalesPaise = 0L;

    @Column(name = "total_settled_paise")
    private Long totalSettledPaise = 0L;

    @Column(name = "pending_settlement_paise")
    private Long pendingSettlementPaise = 0L;

    @OneToMany(mappedBy = "seller", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SellerBankAccount> bankAccounts = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    private Long version;
}
