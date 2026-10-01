package com.shop.warehouse.exception;

import org.springframework.http.HttpStatus;

public class InvalidStockQuantityException extends ApiException {

    public InvalidStockQuantityException(int quantity) {
        super(HttpStatus.BAD_REQUEST, "INVALID_STOCK_QUANTITY",
                "Stock quantity must be greater than zero, got " + quantity);
    }
}
