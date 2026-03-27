package com.ecommerce.common.security;

public final class Roles {

    private Roles() {
        // Prevent instantiation
    }

    public static final String CUSTOMER = "CUSTOMER";
    public static final String ADMIN = "ADMIN";
    public static final String SELLER = "SELLER";
    public static final String INTERNAL_SERVICE = "INTERNAL_SERVICE";
}
