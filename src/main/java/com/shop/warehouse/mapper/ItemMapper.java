package com.shop.warehouse.mapper;

import com.shop.warehouse.dto.response.ItemResponse;
import com.shop.warehouse.entity.Item;

public final class ItemMapper {

    private ItemMapper() {
    }

    public static ItemResponse toResponse(Item item) {
        return new ItemResponse(
                item.getId(),
                item.getName(),
                item.getDescription(),
                item.getCreatedAt(),
                item.getUpdatedAt());
    }
}
