package com.ecommerce.review.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "review_reports")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class ReviewReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID reviewId;

    @Column(nullable = false)
    private UUID reporterId;

    @Column(nullable = false)
    private String reason;    // SPAM, FAKE, OFFENSIVE, IRRELEVANT, OTHER

    private String details;

    @CreationTimestamp
    private Instant createdAt;
}
