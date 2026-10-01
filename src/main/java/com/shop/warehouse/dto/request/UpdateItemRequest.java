package com.shop.warehouse.dto.request;

import com.shop.warehouse.entity.Item;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Full replacement of the item's editable fields (PUT semantics): omitting description clears it.
 */
public record UpdateItemRequest(

        @Schema(example = "T-Shirt Premium")
        @NotBlank(message = "Name must not be blank")
        @Size(max = Item.NAME_MAX_LENGTH, message = "Name must be at most {max} characters")
        String name,

        @Schema(example = "Premium combed cotton T-Shirt")
        @Size(max = Item.DESCRIPTION_MAX_LENGTH, message = "Description must be at most {max} characters")
        String description) {
}
