package com.shop.warehouse.mapper;

import com.shop.warehouse.dto.response.VariantResponse;
import com.shop.warehouse.entity.Variant;

public final class VariantMapper {

    private VariantMapper() {
    }

    public static VariantResponse toResponse(Variant variant) {
        return new VariantResponse(
                variant.getId(),
                variant.getItem().getId(),
                variant.getSku(),
                variant.getName(),
                variant.getPrice(),
                variant.getStockQuantity(),
                variant.getCreatedAt(),
                variant.getUpdatedAt());
    }
}
