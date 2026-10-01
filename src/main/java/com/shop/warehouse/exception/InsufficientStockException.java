package com.shop.warehouse.exception;

import org.springframework.http.HttpStatus;

public class InsufficientStockException extends ApiException {

    public InsufficientStockException(String sku, int available, int requested) {
        super(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK",
                "Insufficient stock for variant " + sku + ": requested " + requested + ", available " + available);
    }
}
