package com.shop.warehouse.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VariantApiIntegrationTest extends AbstractIntegrationTest {

    private long itemId;

    @BeforeEach
    void createParentItem() throws Exception {
        itemId = createItem("T-Shirt");
    }

    @Test
    void createVariantReturns201AndNormalisesSku() throws Exception {
        postJson(variantsUrl(itemId), Map.of(
                "sku", "ts-blk-m", "name", "Black / M", "price", 149000, "stockQuantity", 10))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern(".*/api/items/\\d+/variants/\\d+$")))
                .andExpect(jsonPath("$.itemId").value(itemId))
                .andExpect(jsonPath("$.sku").value("TS-BLK-M"))
                .andExpect(jsonPath("$.name").value("Black / M"))
                .andExpect(jsonPath("$.price").value(149000))
                .andExpect(jsonPath("$.stockQuantity").value(10));
    }

    @Test
    void duplicateSkuIsRejectedCaseInsensitively() throws Exception {
        createVariant(itemId, "TS-BLK-M", 10);
        long otherItemId = createItem("Polo Shirt");

        postJson(variantsUrl(otherItemId), Map.of(
                "sku", "ts-blk-m", "name", "Copy", "price", 1000, "stockQuantity", 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DUPLICATE_SKU"))
                .andExpect(jsonPath("$.message").value("Variant with SKU TS-BLK-M already exists"));
    }

    @Test
    void variantForUnknownItemReturns404() throws Exception {
        postJson(variantsUrl(999), Map.of(
                "sku", "TS-BLK-M", "name", "Black / M", "price", 149000, "stockQuantity", 10))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Item 999 not found"));
    }

    @Test
    void invalidVariantPayloadReturnsAllFieldErrors() throws Exception {
        postJson(variantsUrl(itemId), Map.of(
                "sku", "bad sku!", "name", "", "price", -1, "stockQuantity", -5))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.sku").value("SKU may only contain letters, digits, '-' and '_'"))
                .andExpect(jsonPath("$.errors.name").value("Name must not be blank"))
                .andExpect(jsonPath("$.errors.price").value("Price must not be negative"))
                .andExpect(jsonPath("$.errors.stockQuantity").value("Stock quantity must not be negative"));
    }

    @Test
    void priceWithTooManyDecimalsIsRejected() throws Exception {
        postJson(variantsUrl(itemId), Map.of(
                "sku", "TS-BLK-M", "name", "Black / M", "price", 10.123, "stockQuantity", 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.price").exists());
    }

    @Test
    void zeroPriceAndZeroStockAreAllowed() throws Exception {
        postJson(variantsUrl(itemId), Map.of(
                "sku", "TS-FREE", "name", "Giveaway", "price", 0, "stockQuantity", 0))
                .andExpect(status().isCreated());
    }

    @Test
    void listVariantsOfItem() throws Exception {
        createVariant(itemId, "TS-BLK-M", 10);
        createVariant(itemId, "TS-BLK-L", 5);

        mockMvc.perform(get(variantsUrl(itemId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].sku").value("TS-BLK-M"))
                .andExpect(jsonPath("$[1].sku").value("TS-BLK-L"));
    }

    @Test
    void itemWithoutVariantsHasEmptyVariantList() throws Exception {
        mockMvc.perform(get(variantsUrl(itemId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void listVariantsOfUnknownItemReturns404() throws Exception {
        mockMvc.perform(get(variantsUrl(999))).andExpect(status().isNotFound());
    }

    @Test
    void variantIsNotReachableThroughAnotherItem() throws Exception {
        long variantId = createVariant(itemId, "TS-BLK-M", 10);
        long otherItemId = createItem("Sneakers");

        mockMvc.perform(get(variantUrl(otherItemId, variantId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void updateChangesSkuNameAndPriceButNotStock() throws Exception {
        long variantId = createVariant(itemId, "TS-BLK-M", 10);

        mockMvc.perform(put(variantUrl(itemId, variantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("sku", "TS-BLK-M2", "name", "Black / M (v2)", "price", 139000))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("TS-BLK-M2"))
                .andExpect(jsonPath("$.price").value(139000))
                .andExpect(jsonPath("$.stockQuantity").value(10));
    }

    @Test
    void stockCannotBeChangedThroughUpdate() throws Exception {
        long variantId = createVariant(itemId, "TS-BLK-M", 10);

        mockMvc.perform(put(variantUrl(itemId, variantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sku", "TS-BLK-M", "name", "Black / M", "price", 1, "stockQuantity", 999))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown field 'stockQuantity'"));

        mockMvc.perform(get(variantUrl(itemId, variantId)))
                .andExpect(jsonPath("$.stockQuantity").value(10));
    }

    @Test
    void updateToSkuOfAnotherVariantIsRejected() throws Exception {
        createVariant(itemId, "TS-BLK-M", 10);
        long variantId = createVariant(itemId, "TS-BLK-L", 5);

        mockMvc.perform(put(variantUrl(itemId, variantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("sku", "TS-BLK-M", "name", "Black / L", "price", 1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DUPLICATE_SKU"));
    }

    @Test
    void deleteVariantReturns204() throws Exception {
        long variantId = createVariant(itemId, "TS-BLK-M", 10);

        mockMvc.perform(delete(variantUrl(itemId, variantId))).andExpect(status().isNoContent());
        mockMvc.perform(get(variantUrl(itemId, variantId))).andExpect(status().isNotFound());
    }
}
