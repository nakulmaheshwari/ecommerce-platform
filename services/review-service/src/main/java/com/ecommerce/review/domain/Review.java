package com.ecommerce.review.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reviews")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID productId;

    /*
     * userId — our internal user ID (from user_profiles.id).
     * reviewerKeycloakId — the JWT sub claim.
     *
     * Why store both?
     * - userId for cross-service joins (get all reviews by this user)
     * - reviewerKeycloakId for real-time auth without calling User Service
     */
    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private UUID reviewerKeycloakId;

    /*
     * reviewerName is snapshotted at review time.
     * If the user changes their display name later,
     * old reviews still show who originally wrote them.
     */
    @Column(nullable = false)
    private String reviewerName;

    @Column(nullable = false)
    private Short rating;

    private String title;
    private String body;

    /*
     * verifiedPurchase — set ONLY by our system.
     * Checked by calling Order Service: "did userId ever order productId?"
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean verifiedPurchase = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ReviewStatus status = ReviewStatus.PENDING;

    @Column(nullable = false)
    @Builder.Default
    private Integer helpfulVotes = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer totalVotes = 0;

    private String rejectionReason;
    private UUID moderatedBy;
    private Instant moderatedAt;

    @Column(nullable = false)
    @Builder.Default
    private Integer reportCount = 0;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    // ── Domain logic ──

    public void approve(UUID moderatorId) {
        if (!this.status.canBeModerated()) {
            throw new IllegalStateException(
                "Review in status " + this.status + " cannot be approved");
        }
        this.status = ReviewStatus.APPROVED;
        this.moderatedBy = moderatorId;
        this.moderatedAt = Instant.now();
        this.rejectionReason = null;
    }

    public void reject(UUID moderatorId, String reason) {
        if (!this.status.canBeModerated()) {
            throw new IllegalStateException(
                "Review in status " + this.status + " cannot be rejected");
        }
        this.status = ReviewStatus.REJECTED;
        this.moderatedBy = moderatorId;
        this.moderatedAt = Instant.now();
        this.rejectionReason = reason;
    }

    /*
     * Auto-flag logic: if report count hits threshold (5),
     * move to FLAGGED immediately. Removes visible abuse quickly.
     */
    public void incrementReportCount() {
        this.reportCount++;
        if (this.reportCount >= 5 && this.status == ReviewStatus.APPROVED) {
            this.status = ReviewStatus.FLAGGED;
        }
    }

    public double helpfulnessRatio() {
        if (totalVotes == 0) return 0.0;
        return (double) helpfulVotes / totalVotes;
    }
}
