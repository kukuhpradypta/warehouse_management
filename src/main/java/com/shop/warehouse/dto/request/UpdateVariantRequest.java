package com.shop.warehouse.dto.request;

import com.shop.warehouse.entity.Variant;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Replaces the variant's descriptive fields. Stock is deliberately absent: it can only be
 * changed through the stock endpoints so the "never below zero" rule lives in one place.
 */
public record UpdateVariantRequest(

        @Schema(example = "TS-BLK-M")
        @NotBlank(message = "SKU must not be blank")
        @Size(max = Variant.SKU_MAX_LENGTH, message = "SKU must be at most {max} characters")
        @Pattern(regexp = SkuFormat.REGEX, message = SkuFormat.MESSAGE)
        String sku,

        @Schema(example = "Black / M")
        @NotBlank(message = "Name must not be blank")
        @Size(max = Variant.NAME_MAX_LENGTH, message = "Name must be at most {max} characters")
        String name,

        @Schema(example = "139000.00", description = "Price in IDR")
        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.00", message = "Price must not be negative")
        @Digits(integer = Variant.PRICE_PRECISION - Variant.PRICE_SCALE, fraction = Variant.PRICE_SCALE,
                message = "Price must have at most {integer} integer digits and {fraction} decimals")
        BigDecimal price) {
}
