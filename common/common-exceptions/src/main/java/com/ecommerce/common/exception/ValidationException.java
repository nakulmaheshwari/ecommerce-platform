package com.ecommerce.common.exception;

import java.util.Map;

public class ValidationException extends BaseException {
    private final Map<String, String> fieldErrors;

    public ValidationException(Map<String, String> fieldErrors) {
        super("VALIDATION_FAILED", "Request validation failed", 400);
        this.fieldErrors = fieldErrors;
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
