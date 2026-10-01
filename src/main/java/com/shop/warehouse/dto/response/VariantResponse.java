package com.shop.warehouse.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record VariantResponse(
        Long id,
        Long itemId,
        String sku,
        String name,
        BigDecimal price,
        int stockQuantity,
        Instant createdAt,
        Instant updatedAt) {
}
