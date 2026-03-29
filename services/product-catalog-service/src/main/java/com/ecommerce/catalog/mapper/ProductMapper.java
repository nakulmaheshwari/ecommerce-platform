package com.ecommerce.catalog.mapper;

import com.ecommerce.catalog.api.dto.ProductResponse;
import com.ecommerce.catalog.domain.Product;
import com.ecommerce.catalog.domain.ProductImage;
import com.ecommerce.catalog.domain.ProductVariant;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    @Mapping(target = "category", expression = "java(toCategorySummary(product.getCategory()))")
    @Mapping(target = "discountPercent", expression = "java(product.discountPercent())")
    @Mapping(target = "status", expression = "java(product.getStatus().name())")
    ProductResponse toResponse(Product product);

    default ProductResponse.CategorySummary toCategorySummary(
            com.ecommerce.catalog.domain.Category category) {
        if (category == null) return null;
        return new ProductResponse.CategorySummary(
            category.getId(), category.getName(), category.getSlug());
    }

    @Mapping(target = "isActive", source = "isActive")
    ProductResponse.VariantResponse toVariantResponse(ProductVariant variant);

    @Mapping(target = "isPrimary", source = "isPrimary")
    ProductResponse.ImageResponse toImageResponse(ProductImage image);

    List<ProductResponse> toResponseList(List<Product> products);
}
