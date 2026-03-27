package com.ecommerce.common.security;

public final class SecurityConstants {
    private SecurityConstants() {}

    public static final String CLAIM_USER_ID   = "sub";
    public static final String CLAIM_EMAIL     = "email";
    public static final String CLAIM_ROLES     = "realm_access";
    public static final String HEADER_USER_ID  = "X-User-Id";
    public static final String HEADER_TRACE_ID = "X-B3-TraceId";

    public static final String ROLE_CUSTOMER = "ROLE_CUSTOMER";
    public static final String ROLE_ADMIN    = "ROLE_ADMIN";
    public static final String ROLE_INTERNAL = "ROLE_INTERNAL"; // service-to-service
}
