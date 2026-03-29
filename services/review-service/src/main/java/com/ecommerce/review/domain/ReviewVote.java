package com.ecommerce.review.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "review_votes")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class ReviewVote {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID reviewId;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private Boolean isHelpful;

    @CreationTimestamp
    private Instant createdAt;
}
