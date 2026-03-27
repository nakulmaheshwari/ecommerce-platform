package com.ecommerce.common.exception;

import lombok.Getter;

@Getter
public abstract class BaseException extends RuntimeException {
    private final String errorCode;
    private final int httpStatus;

    protected BaseException(String errorCode, String message, int httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    protected BaseException(String errorCode, String message, int httpStatus, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }
}
