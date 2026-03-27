package com.ecommerce.identity.service;

import com.ecommerce.common.exception.DuplicateResourceException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.security.Roles;
import com.ecommerce.identity.api.dto.*;
import com.ecommerce.identity.domain.OutboxEvent;
import com.ecommerce.identity.domain.User;
import com.ecommerce.identity.domain.UserStatus;
import com.ecommerce.identity.keycloak.KeycloakUserService;
import com.ecommerce.identity.repository.OutboxRepository;
import com.ecommerce.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final OutboxRepository outboxRepository;
    private final KeycloakUserService keycloakUserService;

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        // 1. Check duplicate in our DB first (fast, no Keycloak round trip)
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("User", "email", request.email());
        }

        // 2. Create user in Keycloak — Keycloak owns authentication
        UUID keycloakId = keycloakUserService.createUser(request);

        // 3. Assign role to the user in Keycloak
        keycloakUserService.assignRole(keycloakId.toString(), Roles.CUSTOMER);

        // 4. Create user profile in our DB — we own the profile data
        User user = User.builder()
            .keycloakId(keycloakId)
            .email(request.email())
            .firstName(request.firstName())
            .lastName(request.lastName())
            .phoneNumber(request.phoneNumber())
            .status(UserStatus.PENDING_VERIFICATION)
            .emailVerified(false)
            .build();
        userRepository.save(user);

        // 4. Write user.registered event to outbox IN THE SAME TRANSACTION
        //    If the TX rolls back, the event is also rolled back.
        //    The outbox poller will publish this to Kafka asynchronously.
        outboxRepository.save(OutboxEvent.builder()
            .aggregateType("User")
            .aggregateId(user.getId())
            .eventType("user.registered")
            .payload(Map.of(
                "userId",    user.getId().toString(),
                "email",     user.getEmail(),
                "firstName", user.getFirstName(),
                "lastName",  user.getLastName()
            ))
            .build());

        log.info("User registered userId={} email={}", user.getId(), user.getEmail());

        return new RegisterResponse(
            user.getId(),
            user.getEmail(),
            user.getStatus().name(),
            "Registration successful. Please verify your email."
        );
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        // 1. Find user in our DB
        User user = userRepository.findByEmail(request.email())
            .orElseThrow(() ->
                // Return 401 not 404 — don't reveal whether email exists
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        // 2. Check account state
        if (user.isLocked()) {
            throw new ResponseStatusException(HttpStatus.LOCKED,
                "Account locked due to too many failed attempts. Try again later.");
        }
        if (user.getStatus() == UserStatus.DELETED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account deleted");
        }
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account suspended.");
        }

        // 3. Delegate authentication to Keycloak — it verifies the password
        KeycloakUserService.TokenResponse tokenResponse =
            keycloakUserService.getToken(request.email(), request.password());

        if (tokenResponse == null) {
            // Password wrong — record failed attempt
            user.recordFailedLogin();
            userRepository.save(user);
            log.warn("Failed login attempt email={} failedCount={}",
                request.email(), user.getFailedLoginCount());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        // 4. Record successful login
        user.recordSuccessfulLogin();
        user.setStatus(UserStatus.ACTIVE);  // Activate on first successful login
        userRepository.save(user);

        log.info("User logged in userId={} email={}", user.getId(), user.getEmail());

        return AuthResponse.of(
            tokenResponse.accessToken(),
            tokenResponse.refreshToken(),
            tokenResponse.expiresIn(),
            user.getId(),
            user.getEmail(),
            user.getFullName()
        );
    }

    @Transactional(readOnly = true)
    public RegisterResponse getProfile(UUID keycloakId) {
        User user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new ResourceNotFoundException("User", keycloakId.toString()));
        return new RegisterResponse(
            user.getId(), user.getEmail(),
            user.getStatus().name(), null
        );
    }
}
