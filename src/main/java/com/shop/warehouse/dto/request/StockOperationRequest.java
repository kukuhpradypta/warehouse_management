package com.shop.warehouse.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record StockOperationRequest(

        @Schema(example = "3")
        @NotNull(message = "Quantity is required")
        @Positive(message = "Quantity must be greater than zero")
        @Max(value = MAX_QUANTITY_PER_OPERATION, message = "Quantity must be at most {value} per operation")
        Integer quantity) {

    /** Sanity cap that also keeps the stock column far away from integer overflow. */
    public static final int MAX_QUANTITY_PER_OPERATION = 1_000_000;
}
