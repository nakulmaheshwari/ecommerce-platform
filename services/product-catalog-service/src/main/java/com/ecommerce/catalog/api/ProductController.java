package com.ecommerce.catalog.api;

import com.ecommerce.catalog.api.dto.*;
import com.ecommerce.catalog.service.ProductService;
import com.ecommerce.common.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    // ─── Public read endpoints ───

    @GetMapping("/products/{id}")
    public ResponseEntity<ProductResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(productService.getById(id));
    }

    @GetMapping("/products/slug/{slug}")
    public ResponseEntity<ProductResponse> getBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(productService.getBySlug(slug));
    }

    @GetMapping("/products/{id}/related")
    public ResponseEntity<List<ProductResponse>> getRelatedProducts(@PathVariable UUID id) {
        return ResponseEntity.ok(productService.getRelatedProducts(id));
    }

    @GetMapping("/products/{id}/variants")
    public ResponseEntity<List<ProductResponse.VariantResponse>> getProductVariants(@PathVariable UUID id) {
        return ResponseEntity.ok(productService.getProductVariants(id));
    }

    @GetMapping("/categories/{categoryId}/products")
    public ResponseEntity<ProductPageResponse> getByCategory(
            @PathVariable UUID categoryId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "newest") String sortBy) {
        return ResponseEntity.ok(
            productService.getByCategory(categoryId, page, Math.min(size, 100), sortBy));
    }

    @GetMapping("/categories")
    public ResponseEntity<List<CategoryResponse>> getCategories() {
        return ResponseEntity.ok(productService.getRootCategories());
    }

    // ─── Admin write endpoints ───

    @PostMapping("/admin/products")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> createProduct(
            @Valid @RequestBody CreateProductRequest request) {
        UUID adminId = SecurityUtils.getCurrentUserId();
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(productService.createProduct(request, adminId));
    }

    @PostMapping("/admin/products/{id}/publish")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> publishProduct(@PathVariable UUID id) {
        return ResponseEntity.ok(productService.publishProduct(id));
    }
}
