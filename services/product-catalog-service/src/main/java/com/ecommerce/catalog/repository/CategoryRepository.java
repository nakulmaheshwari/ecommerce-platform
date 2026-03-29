package com.ecommerce.catalog.repository;

import com.ecommerce.catalog.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    Optional<Category> findBySlug(String slug);

    List<Category> findByParentIsNullAndIsActiveTrueOrderBySortOrderAsc();

    @Query("""
        SELECT c FROM Category c
        LEFT JOIN FETCH c.children ch
        WHERE c.parent IS NULL AND c.isActive = TRUE
        ORDER BY c.sortOrder ASC
        """)
    List<Category> findRootCategoriesWithChildren();
}
