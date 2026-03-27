package com.ecommerce.common.exception;

public class DuplicateResourceException extends BaseException {
    public DuplicateResourceException(String resource, String field, String value) {
        super("DUPLICATE_RESOURCE",
              "%s already exists with %s: %s".formatted(resource, field, value),
              409);
    }
}
