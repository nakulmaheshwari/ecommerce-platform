package com.ecommerce.common.security;

import lombok.Builder;
import lombok.Getter;

import java.util.Set;
import java.util.UUID;

@Getter
@Builder
public class AuthenticatedUser {
    private final UUID userId;
    private final String email;
    private final Set<String> roles;

    public boolean isAdmin() {
        return roles.contains(SecurityConstants.ROLE_ADMIN);
    }

    public boolean isInternalService() {
        return roles.contains(SecurityConstants.ROLE_INTERNAL);
    }
}
