package com.ecommerce.catalog.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "categories")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Category> children = new ArrayList<>();

    private String description;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @org.hibernate.annotations.CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
