package com.ecommerce.catalog.config;

import com.ecommerce.catalog.domain.*;
import com.ecommerce.catalog.repository.CategoryRepository;
import com.ecommerce.catalog.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class DataLoader implements CommandLineRunner {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (categoryRepository.count() > 0) {
            log.info("Database already contains Data. Skipping dummy data insertion.");
            return;
        }

        log.info("Starting initial seed data insertion for Product Catalog Service...");
        UUID systemUser = UUID.randomUUID();

        // 1. Create Categories
        Category electronics = Category.builder()
            .name("Electronics").slug("electronics").description("All electronic devices").build();
        categoryRepository.save(electronics);

        Category smartphones = Category.builder()
            .name("Smartphones").slug("smartphones").parent(electronics).description("Mobile phones").build();
        Category laptops = Category.builder()
            .name("Laptops").slug("laptops").parent(electronics).description("Computers").build();
        categoryRepository.saveAll(List.of(smartphones, laptops));

        Category clothing = Category.builder()
            .name("Clothing").slug("clothing").description("Apparel for everyone").build();
        categoryRepository.save(clothing);

        Category mensWear = Category.builder()
            .name("Men's Wear").slug("mens-wear").parent(clothing).description("Men's clothing").build();
        categoryRepository.save(mensWear);

        log.info("Categories inserted.");

        // 2. Create Products
        // Product 1: iPhone 15
        Product iphone15 = Product.builder()
            .sku("APP-IPH15-001")
            .name("iPhone 15 Pro")
            .slug("iphone-15-pro")
            .description("Latest Apple iPhone 15 Pro with Titanium build.")
            .brand("Apple")
            .category(smartphones)
            .pricePaise(120000_00L) // 1,20,000 INR
            .mrpPaise(134900_00L)
            .taxPercent(new BigDecimal("18.00"))
            .isDigital(false)
            .weightGrams(187)
            .createdBy(systemUser)
            .status(ProductStatus.ACTIVE)
            .publishedAt(Instant.now())
            .build();

        iphone15.getVariants().addAll(List.of(
            ProductVariant.builder()
                .product(iphone15).sku("APP-IPH15-128-NAT")
                .name("iPhone 15 Pro - 128GB - Natural Titanium")
                .pricePaise(120000_00L)
                .attributes(Map.of("storage", "128GB", "color", "Natural Titanium"))
                .sortOrder(1).build(),
            ProductVariant.builder()
                .product(iphone15).sku("APP-IPH15-256-BLK")
                .name("iPhone 15 Pro - 256GB - Black Titanium")
                .pricePaise(130000_00L)
                .attributes(Map.of("storage", "256GB", "color", "Black Titanium"))
                .sortOrder(2).build()
        ));

        iphone15.getImages().addAll(List.of(
            ProductImage.builder()
                .product(iphone15).url("https://example.com/images/iphone15-main.jpg")
                .altText("Front View").isPrimary(true).sortOrder(1).build(),
            ProductImage.builder()
                .product(iphone15).url("https://example.com/images/iphone15-side.jpg")
                .altText("Side View").isPrimary(false).sortOrder(2).build()
        ));

        // Product 2: MacBook Pro
        Product macbook = Product.builder()
            .sku("APP-MBP-001")
            .name("MacBook Pro 16-inch M3 Max")
            .slug("macbook-pro-16-m3-max")
            .description("The ultimate pro laptop.")
            .brand("Apple")
            .category(laptops)
            .pricePaise(349000_00L) 
            .mrpPaise(349000_00L)
            .taxPercent(new BigDecimal("18.00"))
            .isDigital(false)
            .weightGrams(2140)
            .createdBy(systemUser)
            .status(ProductStatus.ACTIVE)
            .publishedAt(Instant.now())
            .build();

        macbook.getVariants().addAll(List.of(
            ProductVariant.builder()
                .product(macbook).sku("APP-MBP-36GB")
                .name("36GB Unified Memory / 1TB SSD")
                .pricePaise(349000_00L)
                .attributes(Map.of("memory", "36GB", "storage", "1TB"))
                .build()
        ));

        macbook.getImages().addAll(List.of(
            ProductImage.builder()
                .product(macbook).url("https://example.com/images/macbook-pro.jpg")
                .altText("Silver MacBook Pro").isPrimary(true).sortOrder(1).build()
        ));

        // Product 3: Cotton T-Shirt
        Product tshirt = Product.builder()
            .sku("TSO-M-001")
            .name("Essential Cotton Crew Tee")
            .slug("essential-cotton-crew-tee")
            .description("Everyday basic t-shirt in heavy 100% cotton.")
            .brand("Polo")
            .category(mensWear)
            .pricePaise(1499_00L) 
            .mrpPaise(1999_00L)
            .taxPercent(new BigDecimal("5.00"))
            .isDigital(false)
            .weightGrams(200)
            .createdBy(systemUser)
            .status(ProductStatus.ACTIVE)
            .publishedAt(Instant.now())
            .build();

        tshirt.getVariants().addAll(List.of(
            ProductVariant.builder()
                .product(tshirt).sku("TSO-M-RED-L")
                .name("Red - Large")
                .pricePaise(1499_00L)
                .attributes(Map.of("color", "Red", "size", "L"))
                .sortOrder(1).build(),
            ProductVariant.builder()
                .product(tshirt).sku("TSO-M-BLU-M")
                .name("Blue - Medium")
                .pricePaise(1499_00L)
                .attributes(Map.of("color", "Blue", "size", "M"))
                .sortOrder(2).build()
        ));

        tshirt.getImages().add(
            ProductImage.builder()
                .product(tshirt).url("https://example.com/images/tshirt-red.jpg")
                .altText("Red Tee front").isPrimary(true).build()
        );

        // Product 4: Samsung Galaxy S24 Ultra
        Product s24ultra = Product.builder()
            .sku("SAM-S24U-001")
            .name("Samsung Galaxy S24 Ultra")
            .slug("samsung-galaxy-s24-ultra")
            .description("Galaxy AI is here. Titanium exterior and a 6.8-inch flat display.")
            .brand("Samsung")
            .category(smartphones)
            .pricePaise(129999_00L)
            .mrpPaise(134999_00L)
            .taxPercent(new BigDecimal("18.00"))
            .isDigital(false)
            .weightGrams(232)
            .createdBy(systemUser)
            .status(ProductStatus.ACTIVE)
            .publishedAt(Instant.now())
            .build();

        s24ultra.getVariants().addAll(List.of(
            ProductVariant.builder()
                .product(s24ultra).sku("SAM-S24U-256-TI")
                .name("256GB - Titanium Gray")
                .pricePaise(129999_00L)
                .attributes(Map.of("storage", "256GB", "color", "Titanium Gray"))
                .sortOrder(1).build(),
            ProductVariant.builder()
                .product(s24ultra).sku("SAM-S24U-512-TI")
                .name("512GB - Titanium Black")
                .pricePaise(139999_00L)
                .attributes(Map.of("storage", "512GB", "color", "Titanium Black"))
                .sortOrder(2).build()
        ));

        s24ultra.getImages().add(
            ProductImage.builder()
                .product(s24ultra).url("https://example.com/images/s24ultra-front.jpg")
                .altText("S24 Ultra Front").isPrimary(true).build()
        );

        // Product 5: Sony Headphones
        Product sonyXm5 = Product.builder()
            .sku("SON-WH1000XM5")
            .name("Sony WH-1000XM5 Wireless Headphones")
            .slug("sony-wh-1000xm5-wireless-headphones")
            .description("Industry leading noise cancellation.")
            .brand("Sony")
            .category(electronics)
            .pricePaise(27990_00L)
            .mrpPaise(34990_00L)
            .taxPercent(new BigDecimal("18.00"))
            .isDigital(false)
            .weightGrams(250)
            .createdBy(systemUser)
            .status(ProductStatus.ACTIVE)
            .publishedAt(Instant.now())
            .build();

        sonyXm5.getVariants().add(
            ProductVariant.builder()
                .product(sonyXm5).sku("SON-WH1000XM5-BLK")
                .name("Black")
                .pricePaise(27990_00L)
                .attributes(Map.of("color", "Black"))
                .build()
        );

        sonyXm5.getImages().add(
            ProductImage.builder()
                .product(sonyXm5).url("https://example.com/images/sonyxm5.jpg")
                .altText("Sony XM5 Black").isPrimary(true).build()
        );

        // Product 6: Levi's Jeans
        Product levisJeans = Product.builder()
            .sku("LEV-501-001")
            .name("Levi's 501 Original Fit Men's Jeans")
            .slug("levis-501-original-fit-mens-jeans")
            .description("The classic straight fit. Our signature button fly.")
            .brand("Levi's")
            .category(mensWear)
            .pricePaise(3599_00L)
            .mrpPaise(4999_00L)
            .taxPercent(new BigDecimal("5.00"))
            .isDigital(false)
            .weightGrams(500)
            .createdBy(systemUser)
            .status(ProductStatus.ACTIVE)
            .publishedAt(Instant.now())
            .build();

        levisJeans.getVariants().addAll(List.of(
            ProductVariant.builder()
                .product(levisJeans).sku("LEV-501-3232")
                .name("32W x 32L - Dark Indigo")
                .pricePaise(3599_00L)
                .attributes(Map.of("waist", "32", "length", "32", "color", "Dark Indigo"))
                .sortOrder(1).build(),
            ProductVariant.builder()
                .product(levisJeans).sku("LEV-501-3432")
                .name("34W x 32L - Dark Indigo")
                .pricePaise(3599_00L)
                .attributes(Map.of("waist", "34", "length", "32", "color", "Dark Indigo"))
                .sortOrder(2).build()
        ));

        productRepository.saveAll(List.of(iphone15, macbook, tshirt, s24ultra, sonyXm5, levisJeans));

        log.info("Dummy Products, Variants, and Images successfully inserted!");
    }
}
