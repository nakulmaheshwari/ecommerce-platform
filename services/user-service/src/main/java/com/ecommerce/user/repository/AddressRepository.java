package com.ecommerce.user.repository;

import com.ecommerce.user.domain.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AddressRepository extends JpaRepository<Address, UUID> {

    List<Address> findByUserProfileIdAndIsDeletedFalseOrderByIsDefaultDescCreatedAtAsc(
        UUID userId);

    Optional<Address> findByIdAndUserProfileIdAndIsDeletedFalse(
        UUID addressId, UUID userId);

    @Modifying
    @Query("""
        UPDATE Address a SET a.isDefault = FALSE
        WHERE a.userProfile.id = :userId
          AND a.isDeleted = FALSE
        """)
    void unsetAllDefaults(@Param("userId") UUID userId);

    boolean existsByUserProfileIdAndIsDeletedFalse(UUID userId);
}
