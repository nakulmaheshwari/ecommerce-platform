package com.ecommerce.seller.repository;

import com.ecommerce.seller.domain.SellerInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SellerInventoryRepository extends JpaRepository<SellerInventory, UUID> {
    Optional<SellerInventory> findBySellerProductId(UUID sellerProductId);

    @Modifying
    @Query("UPDATE SellerInventory s SET s.quantityAvailable = s.quantityAvailable - :qty, " +
           "s.quantityReserved = s.quantityReserved + :qty " +
           "WHERE s.id = :inventoryId AND s.quantityAvailable >= :qty")
    int reserveInventory(@Param("inventoryId") UUID inventoryId, @Param("qty") int qty);

    @Modifying
    @Query("UPDATE SellerInventory s SET s.quantityAvailable = s.quantityAvailable + :qty, " +
           "s.quantityReserved = s.quantityReserved - :qty " +
           "WHERE s.id = :inventoryId AND s.quantityReserved >= :qty")
    int releaseInventory(@Param("inventoryId") UUID inventoryId, @Param("qty") int qty);

    @Modifying
    @Query("UPDATE SellerInventory s SET s.quantityReserved = s.quantityReserved - :qty, " +
           "s.quantitySold = s.quantitySold + :qty " +
           "WHERE s.id = :inventoryId AND s.quantityReserved >= :qty")
    int confirmSold(@Param("inventoryId") UUID inventoryId, @Param("qty") int qty);
}
