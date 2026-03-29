package com.ecommerce.user.repository;

import com.ecommerce.user.domain.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {

    Optional<UserProfile> findByKeycloakId(UUID keycloakId);

    Optional<UserProfile> findByEmail(String email);

    boolean existsByKeycloakId(UUID keycloakId);

    boolean existsByEmail(String email);

    @Query("""
        SELECT p FROM UserProfile p
        LEFT JOIN FETCH p.addresses a
        WHERE p.keycloakId = :keycloakId
          AND (a IS NULL OR a.isDeleted = FALSE)
        """)
    Optional<UserProfile> findByKeycloakIdWithAddresses(
        @Param("keycloakId") UUID keycloakId);

    @Query("""
        SELECT p FROM UserProfile p
        LEFT JOIN FETCH p.addresses a
        WHERE p.id = :userId
          AND (a IS NULL OR a.isDeleted = FALSE)
        """)
    Optional<UserProfile> findByIdWithAddresses(@Param("userId") UUID userId);
}
