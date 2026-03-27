package com.ecommerce.identity.keycloak;

import com.ecommerce.common.exception.DuplicateResourceException;
import com.ecommerce.common.exception.ServiceUnavailableException;
import com.ecommerce.identity.api.dto.RegisterRequest;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class KeycloakUserService {

    private final Keycloak keycloak;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.server-url}")
    private String serverUrl;

    @Value("${keycloak.client-id}")
    private String clientId;

    @Value("${keycloak.client-secret}")
    private String clientSecret;

    public UUID createUser(RegisterRequest request) {
        UsersResource usersResource = keycloak.realm(realm).users();

        // Build Keycloak user representation
        UserRepresentation user = new UserRepresentation();
        user.setUsername(request.email());
        user.setEmail(request.email());
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setEnabled(true);
        user.setEmailVerified(false);
        user.setAttributes(Map.of("phoneNumber", List.of(
            request.phoneNumber() != null ? request.phoneNumber() : ""
        )));

        // Set password credential
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(request.password());
        credential.setTemporary(false);
        user.setCredentials(List.of(credential));

        try (Response response = usersResource.create(user)) {
            if (response.getStatus() == 409) {
                throw new DuplicateResourceException("User", "email", request.email());
            }
            if (response.getStatus() != 201) {
                log.error("Keycloak user creation failed status={} body={}",
                    response.getStatus(), response.readEntity(String.class));
                throw new ServiceUnavailableException("keycloak",
                    "User creation failed with status: " + response.getStatus());
            }

            // Extract the new user's Keycloak ID from the Location header
            String location = response.getLocation().getPath();
            String keycloakId = location.substring(location.lastIndexOf('/') + 1);

            log.info("Keycloak user created keycloakId={} email={}", keycloakId, request.email());
            return UUID.fromString(keycloakId);
        } catch (DuplicateResourceException | ServiceUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceUnavailableException("keycloak", e.getMessage());
        }
    }

    public void assignRole(String keycloakUserId, String roleName) {
        try {
            RoleRepresentation role = keycloak.realm(realm)
                .roles().get(roleName).toRepresentation();
            keycloak.realm(realm).users()
                .get(keycloakUserId).roles().realmLevel().add(List.of(role));
            log.info("Successfully assigned role={} to keycloakUserId={}", roleName, keycloakUserId);
        } catch (Exception e) {
            log.error("Failed to assign role={} to keycloakUserId={}", roleName, keycloakUserId, e);
            throw new ServiceUnavailableException("keycloak", "Failed to assign role: " + roleName);
        }
    }

    public void disableUser(String keycloakUserId) {
        try {
            UserRepresentation user = keycloak.realm(realm).users().get(keycloakUserId).toRepresentation();
            user.setEnabled(false);
            keycloak.realm(realm).users().get(keycloakUserId).update(user);
            log.info("Successfully disabled keycloakUserId={}", keycloakUserId);
        } catch (Exception e) {
            log.error("Failed to disable keycloakUserId={}", keycloakUserId, e);
            throw new ServiceUnavailableException("keycloak", "Failed to disable user account");
        }
    }

    public TokenResponse getToken(String email, String password) {
        // Direct grant flow — user authenticates with username/password
        // Keycloak validates and issues JWT
        try {
            org.keycloak.admin.client.Keycloak userKeycloak = org.keycloak.admin.client.KeycloakBuilder.builder()
                .serverUrl(serverUrl)
                .realm(realm)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .username(email)
                .password(password)
                .grantType(org.keycloak.OAuth2Constants.PASSWORD)
                .build();
                
            var token = userKeycloak.tokenManager().getAccessToken();
            return new TokenResponse(
                token.getToken(),
                token.getRefreshToken(),
                token.getExpiresIn()
            );
        } catch (Exception e) {
            log.warn("Token request failed for email={}: {}", email, e.getMessage());
            return null;
        }
    }

    public record TokenResponse(String accessToken, String refreshToken, long expiresIn) {}
}
