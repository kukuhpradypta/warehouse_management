package com.shop.warehouse.exception;

import org.springframework.http.HttpStatus;

public class DuplicateSkuException extends ApiException {

    public static final String ERROR_CODE = "DUPLICATE_SKU";

    public DuplicateSkuException(String sku) {
        super(HttpStatus.CONFLICT, ERROR_CODE, "Variant with SKU " + sku + " already exists");
    }
}
