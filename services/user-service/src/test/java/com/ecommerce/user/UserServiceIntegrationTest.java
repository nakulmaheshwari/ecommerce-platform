package com.ecommerce.user;

import com.ecommerce.user.domain.UserProfile;
import com.ecommerce.user.repository.UserProfileRepository;
import com.ecommerce.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class UserServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("user_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("eureka.client.enabled",      () -> "false");
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9999");
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
            () -> "http://localhost:9999/doesnotexist");
    }

    @Autowired UserService userService;
    @Autowired UserProfileRepository userProfileRepository;

    @Test
    void createProfile_createsAndReturnsProfile() {
        UUID keycloakId = UUID.randomUUID();

        var response = userService.createProfile(
            keycloakId, "test@example.com",
            "Nakul", "Dev", "+919876543210"
        );

        assertThat(response.email()).isEqualTo("test@example.com");
        assertThat(response.firstName()).isEqualTo("Nakul");
        assertThat(response.fullName()).isEqualTo("Nakul Dev");
        assertThat(response.isActive()).isTrue();

        UserProfile saved = userProfileRepository
            .findByKeycloakId(keycloakId).orElseThrow();
        assertThat(saved.getEmail()).isEqualTo("test@example.com");
    }

    @Test
    void createProfile_isIdempotent_doesNotCreateDuplicate() {
        UUID keycloakId = UUID.randomUUID();

        userService.createProfile(keycloakId, "idempotent@test.com",
            "John", "Doe", null);
        userService.createProfile(keycloakId, "idempotent@test.com",
            "John", "Doe", null);

        long count = userProfileRepository.findAll().stream()
            .filter(p -> p.getKeycloakId().equals(keycloakId))
            .count();
        assertThat(count).isEqualTo(1);
    }
}
