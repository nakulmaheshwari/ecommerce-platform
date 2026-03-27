package com.ecommerce.identity.service;

import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.identity.api.dto.AssignRoleRequest;
import com.ecommerce.identity.api.dto.UpdateStatusRequest;
import com.ecommerce.identity.api.dto.UserResponse;
import com.ecommerce.identity.domain.OutboxEvent;
import com.ecommerce.identity.domain.User;
import com.ecommerce.identity.domain.UserStatus;
import com.ecommerce.identity.keycloak.KeycloakUserService;
import com.ecommerce.identity.repository.OutboxRepository;
import com.ecommerce.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final UserRepository userRepository;
    private final OutboxRepository outboxRepository;
    private final KeycloakUserService keycloakUserService;

    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
            .map(this::mapToUserResponse)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(UUID id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User", id.toString()));
        return mapToUserResponse(user);
    }

    @Transactional
    public void updateUserStatus(UUID id, UpdateStatusRequest request) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User", id.toString()));
        
        user.setStatus(request.status());
        userRepository.save(user);
        log.info("Admin updated status to {} for userId={}", request.status(), id);
    }

    @Transactional
    public void assignRole(UUID id, AssignRoleRequest request) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User", id.toString()));
        
        keycloakUserService.assignRole(user.getKeycloakId().toString(), request.roleName());
        log.info("Admin assigned role={} to userId={}", request.roleName(), id);
    }

    @Transactional
    public void deleteUser(UUID id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User", id.toString()));

        if (user.getStatus() == UserStatus.DELETED) {
            return; // Idempotent
        }

        user.setStatus(UserStatus.DELETED);
        userRepository.save(user);

        // Disable user in Keycloak instead of hard deleting to preserve audit history
        keycloakUserService.disableUser(user.getKeycloakId().toString());

        // Emit domain event for other microservices
        outboxRepository.save(OutboxEvent.builder()
            .aggregateType("User")
            .aggregateId(user.getId())
            .eventType("user.deleted")
            .payload(Map.of(
                "userId", user.getId().toString(),
                "email", user.getEmail(),
                "deletedAt", Instant.now().toString()
            ))
            .build());

        log.info("User deleted userId={} keycloakId={} email={}", user.getId(), user.getKeycloakId(), user.getEmail());
    }

    private UserResponse mapToUserResponse(User user) {
        return new UserResponse(
            user.getId(),
            user.getEmail(),
            user.getFirstName(),
            user.getLastName(),
            user.getStatus().name(),
            user.getEmailVerified(),
            user.getCreatedAt(),
            user.getLastLoginAt()
        );
    }
}
