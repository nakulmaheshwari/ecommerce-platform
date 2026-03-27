package com.ecommerce.common.exception;

public class ServiceUnavailableException extends BaseException {
    public ServiceUnavailableException(String serviceName, String reason) {
        super("SERVICE_UNAVAILABLE",
              "Service '%s' is unavailable: %s".formatted(serviceName, reason),
              503);
    }
}
