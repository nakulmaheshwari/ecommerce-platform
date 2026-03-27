package com.ecommerce.identity.repository;

import com.ecommerce.identity.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    Optional<User> findByKeycloakId(UUID keycloakId);

    boolean existsByEmail(String email);

    @Modifying
    @Query("UPDATE User u SET u.lastLoginAt = :loginAt, u.failedLoginCount = 0 WHERE u.id = :userId")
    void updateLastLogin(UUID userId, Instant loginAt);
}
