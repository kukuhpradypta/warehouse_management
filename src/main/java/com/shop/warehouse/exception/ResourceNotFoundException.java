package com.shop.warehouse.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends ApiException {

    private ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", message);
    }

    public static ResourceNotFoundException item(Long itemId) {
        return new ResourceNotFoundException("Item " + itemId + " not found");
    }

    public static ResourceNotFoundException variant(Long itemId, Long variantId) {
        return new ResourceNotFoundException("Variant " + variantId + " not found for item " + itemId);
    }
}
