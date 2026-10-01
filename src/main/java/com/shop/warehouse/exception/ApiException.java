package com.shop.warehouse.exception;

import org.springframework.http.HttpStatus;

/**
 * Base type for expected business failures. Each subclass carries its HTTP status and a stable
 * machine-readable error code, so the global handler needs a single mapping for all of them.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    protected ApiException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
