package com.ecommerce.common.exception;

public class ResourceNotFoundException extends BaseException {
    public ResourceNotFoundException(String resource, String id) {
        super("RESOURCE_NOT_FOUND",
              "%s not found with id: %s".formatted(resource, id),
              404);
    }
}
