package com.ecommerce.catalog.service;

import com.ecommerce.catalog.api.dto.*;
import com.ecommerce.catalog.domain.*;
import com.ecommerce.catalog.mapper.ProductMapper;
import com.ecommerce.catalog.repository.*;
import com.ecommerce.common.exception.DuplicateResourceException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final OutboxRepository outboxRepository;
    private final ProductMapper productMapper;

    // ─────────────────────────────────────────────
    // READ OPERATIONS — cached in Redis
    // ─────────────────────────────────────────────

    // Cache key: "products::uuid-here"
    // TTL configured in RedisConfig: 10 minutes
    // On product update, cache entry is evicted
    @Cacheable(value = "products", key = "#id")
    @Transactional(readOnly = true)
    public ProductResponse getById(UUID id) {
        log.debug("Cache MISS for product id={}", id);
        Product product = productRepository.findActiveByIdWithDetails(id)
            .orElseThrow(() -> new ResourceNotFoundException("Product", id.toString()));
        return productMapper.toResponse(product);
    }



    @Cacheable(value = "products", key = "'slug:' + #slug")
    @Transactional(readOnly = true)
    public ProductResponse getBySlug(String slug) {
        log.debug("Cache MISS for product slug={}", slug);
        Product product = productRepository.findActiveBySlugWithDetails(slug)
            .orElseThrow(() -> new ResourceNotFoundException("Product", slug));
        return productMapper.toResponse(product);
    }





    @Transactional(readOnly = true)
    public ProductPageResponse getByCategory(UUID categoryId, int page, int size, String sortBy) {
        // Validate category exists
        if (!categoryRepository.existsById(categoryId)) {
            throw new ResourceNotFoundException("Category", categoryId.toString());
        }

        Sort sort = switch (sortBy) {
            case "price_asc"  -> Sort.by("pricePaise").ascending();
            case "price_desc" -> Sort.by("pricePaise").descending();
            case "newest"     -> Sort.by("publishedAt").descending();
            default           -> Sort.by("publishedAt").descending();
        };

        Page<Product> productPage = productRepository.findByCategoryId(
            categoryId, PageRequest.of(page, size, sort));

        return new ProductPageResponse(
            productMapper.toResponseList(productPage.getContent()),
            page, size,
            productPage.getTotalElements(),
            productPage.getTotalPages(),
            productPage.hasNext(),
            productPage.hasPrevious()
        );
    }

    @Cacheable(value = "products", key = "'related:' + #productId")
    @Transactional(readOnly = true)
    public List<ProductResponse> getRelatedProducts(UUID productId) {
        log.debug("Cache MISS for related products productId={}", productId);
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new ResourceNotFoundException("Product", productId.toString()));

        // Fast & simple recommendation: same category, ordered by newest, limit 4
        Page<Product> relatedPage = productRepository.findRelatedProducts(
            product.getCategory().getId(), productId, PageRequest.of(0, 4));

        return productMapper.toResponseList(relatedPage.getContent());
    }

    @Cacheable(value = "products", key = "'variants:' + #productId")
    @Transactional(readOnly = true)
    public List<ProductResponse.VariantResponse> getProductVariants(UUID productId) {
        log.debug("Cache MISS for variants productId={}", productId);
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new ResourceNotFoundException("Product", productId.toString()));

        return product.getVariants().stream()
            .filter(ProductVariant::getIsActive)
            .map(productMapper::toVariantResponse)
            .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────
    // WRITE OPERATIONS — admin only
    // ─────────────────────────────────────────────

    @Transactional
    @CacheEvict(value = "products", allEntries = true)
    public ProductResponse createProduct(CreateProductRequest request, UUID adminUserId) {
        if (productRepository.existsBySku(request.sku())) {
            throw new DuplicateResourceException("Product", "sku", request.sku());
        }

        Category category = categoryRepository.findById(request.categoryId())
            .orElseThrow(() ->
                new ResourceNotFoundException("Category", request.categoryId().toString()));

        Product product = Product.builder()
            .sku(request.sku())
            .name(request.name())
            .slug(generateSlug(request.name()))
            .description(request.description())
            .category(category)
            .brand(request.brand())
            .pricePaise(request.pricePaise())
            .mrpPaise(request.mrpPaise())
            .taxPercent(request.taxPercent())
            .isDigital(request.isDigital() != null && request.isDigital())
            .weightGrams(request.weightGrams())
            .createdBy(adminUserId)
            .status(ProductStatus.DRAFT)
            .build();

        // Add variants
        if (request.variants() != null) {
            for (var v : request.variants()) {
                ProductVariant variant = ProductVariant.builder()
                    .product(product)
                    .sku(v.sku())
                    .name(v.name())
                    .pricePaise(v.pricePaise())
                    .attributes(v.attributes())
                    .build();
                product.getVariants().add(variant);
            }
        }

        // Add images
        if (request.imageUrls() != null) {
            for (int i = 0; i < request.imageUrls().size(); i++) {
                ProductImage image = ProductImage.builder()
                    .product(product)
                    .url(request.imageUrls().get(i))
                    .isPrimary(i == 0)
                    .sortOrder(i)
                    .build();
                product.getImages().add(image);
            }
        }

        productRepository.save(product);

        // Outbox event — same transaction
        outboxRepository.save(OutboxEvent.builder()
            .aggregateType("Product")
            .aggregateId(product.getId())
            .eventType("product.created")
            .payload(Map.of(
                "productId",  product.getId().toString(),
                "sku",        product.getSku(),
                "name",       product.getName(),
                "categoryId", category.getId().toString(),
                "pricePaise", product.getPricePaise()
            ))
            .build());

        log.info("Product created productId={} sku={}", product.getId(), product.getSku());
        return productMapper.toResponse(product);
    }

    @Transactional
    @CacheEvict(value = "products", allEntries = true)
    public ProductResponse publishProduct(UUID productId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new ResourceNotFoundException("Product", productId.toString()));

        product.publish();
        productRepository.save(product);

        outboxRepository.save(OutboxEvent.builder()
            .aggregateType("Product")
            .aggregateId(product.getId())
            .eventType("product.published")
            .payload(Map.of(
                "productId", product.getId().toString(),
                "sku",       product.getSku(),
                "name",      product.getName()
            ))
            .build());

        log.info("Product published productId={}", productId);
        return productMapper.toResponse(product);
    }

    // ─────────────────────────────────────────────
    // CATEGORIES
    // ─────────────────────────────────────────────

    @Cacheable(value = "categories", key = "'root'")
    @Transactional(readOnly = true)
    public List<CategoryResponse> getRootCategories() {
        return categoryRepository.findRootCategoriesWithChildren()
            .stream()
            .map(this::toCategoryResponse)
            .collect(Collectors.toList());
    }


    
    private CategoryResponse toCategoryResponse(Category c) {
        return new CategoryResponse(
            c.getId(), c.getName(), c.getSlug(), c.getDescription(),
            c.getChildren().stream()
                .filter(Category::getIsActive)
                .map(ch -> new CategoryResponse(
                    ch.getId(), ch.getName(), ch.getSlug(),
                    ch.getDescription(), List.of()))
                .collect(Collectors.toList())
        );
    }

    private String generateSlug(String name) {
        String base = name.toLowerCase()
            .replaceAll("[^a-z0-9\\s-]", "")
            .replaceAll("\\s+", "-")
            .replaceAll("-+", "-");
        // Ensure uniqueness
        String slug = base;
        int counter = 1;
        while (productRepository.existsBySlug(slug)) {
            slug = base + "-" + counter++;
        }
        return slug;
    }
}
