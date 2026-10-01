package com.shop.warehouse.exception;

import org.springframework.http.HttpStatus;

public class ItemHasVariantsException extends ApiException {

    public ItemHasVariantsException(Long itemId) {
        super(HttpStatus.CONFLICT, "ITEM_HAS_VARIANTS",
                "Item " + itemId + " still has variants; delete its variants first");
    }
}
