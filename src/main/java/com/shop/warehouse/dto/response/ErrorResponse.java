package com.shop.warehouse.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * Single error shape for every failure. {@code errors} is only present for field-level validation failures.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> errors) {

    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(Instant.now(), status, error, message, path, null);
    }

    public static ErrorResponse withFieldErrors(int status, String error, String message, String path,
                                                Map<String, String> errors) {
        return new ErrorResponse(Instant.now(), status, error, message, path, errors);
    }
}
