package com.ecommerce.seller.domain;

import com.ecommerce.seller.domain.enums.SettlementStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "settlements")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private Seller seller;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_account_id", nullable = false)
    private SellerBankAccount bankAccount;

    @Column(name = "gross_sales_paise", nullable = false)
    private Long grossSalesPaise = 0L;

    @Column(name = "commission_paise", nullable = false)
    private Long commissionPaise = 0L;

    @Column(name = "platform_fee_paise", nullable = false)
    private Long platformFeePaise = 0L;

    @Column(name = "shipping_fee_paise", nullable = false)
    private Long shippingFeePaise = 0L;

    @Column(name = "refund_paise", nullable = false)
    private Long refundPaise = 0L;

    @Column(name = "tax_paise", nullable = false)
    private Long taxPaise = 0L;

    @Column(name = "net_payout_paise", nullable = false)
    private Long netPayoutPaise = 0L;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SettlementStatus status = SettlementStatus.PENDING;

    @Column(name = "period_start", nullable = false)
    private OffsetDateTime periodStart;

    @Column(name = "period_end", nullable = false)
    private OffsetDateTime periodEnd;

    @Column(name = "payout_reference", length = 100)
    private String payoutReference;

    @Column(name = "payout_at")
    private OffsetDateTime payoutAt;

    private String notes;

    @OneToMany(mappedBy = "settlement", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SettlementItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    private Long version;
}
