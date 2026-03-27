package com.ecommerce.common.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class SecurityUtils {
    private SecurityUtils() {}

    public static AuthenticatedUser getCurrentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException("No authenticated user in security context");
        }
        return AuthenticatedUser.builder()
            .userId(UUID.fromString(jwt.getSubject()))
            .email(jwt.getClaimAsString(SecurityConstants.CLAIM_EMAIL))
            .roles(extractRoles(jwt))
            .build();
    }

    public static UUID getCurrentUserId() {
        return getCurrentUser().getUserId();
    }

    @SuppressWarnings("unchecked")
    private static Set<String> extractRoles(Jwt jwt) {
        // Keycloak puts roles in realm_access.roles
        Map<String, Object> realmAccess = jwt.getClaim(SecurityConstants.CLAIM_ROLES);
        if (realmAccess == null) return Set.of();
        List<String> roles = (List<String>) realmAccess.get("roles");
        if (roles == null) return Set.of();
        return roles.stream()
            .map(r -> "ROLE_" + r.toUpperCase())
            .collect(Collectors.toSet());
    }
}
