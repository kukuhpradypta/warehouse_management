package com.shop.warehouse.dto.request;

final class SkuFormat {

    /** Letters, digits, '-' and '_' only; surrounding whitespace is tolerated and trimmed later. */
    static final String REGEX = "^\\s*[A-Za-z0-9][A-Za-z0-9_-]*\\s*$";
    static final String MESSAGE = "SKU may only contain letters, digits, '-' and '_'";

    private SkuFormat() {
    }
}
