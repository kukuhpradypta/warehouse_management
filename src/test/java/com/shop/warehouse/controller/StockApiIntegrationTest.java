package com.shop.warehouse.controller;

import com.shop.warehouse.exception.InsufficientStockException;
import com.shop.warehouse.service.StockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StockApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private StockService stockService;

    private long itemId;

    @BeforeEach
    void createParentItem() throws Exception {
        itemId = createItem("T-Shirt");
    }

    @Test
    void sellingFollowsAvailableStockAndNeverGoesNegative() throws Exception {
        long variantId = createVariant(itemId, "SKU-001", 5);

        decrease(variantId, 3).andExpect(status().isOk()).andExpect(jsonPath("$.stockQuantity").value(2));
        decrease(variantId, 2).andExpect(status().isOk()).andExpect(jsonPath("$.stockQuantity").value(0));

        decrease(variantId, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.message").value("Insufficient stock for variant SKU-001: requested 1, available 0"))
                .andExpect(jsonPath("$.path").value(stockUrl(variantId, "decrease")));

        mockMvc.perform(get(variantUrl(itemId, variantId))).andExpect(jsonPath("$.stockQuantity").value(0));
    }

    @Test
    void sellingMoreThanAvailableLeavesStockUnchanged() throws Exception {
        long variantId = createVariant(itemId, "SKU-001", 5);

        decrease(variantId, 6).andExpect(status().isConflict());

        mockMvc.perform(get(variantUrl(itemId, variantId))).andExpect(jsonPath("$.stockQuantity").value(5));
    }

    @Test
    void increaseAddsToStock() throws Exception {
        long variantId = createVariant(itemId, "SKU-001", 0);

        postJson(stockUrl(variantId, "increase"), Map.of("quantity", 7))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockQuantity").value(7))
                .andExpect(jsonPath("$.sku").value("SKU-001"));
    }

    @Test
    void zeroNegativeAndMissingQuantitiesAreRejected() throws Exception {
        long variantId = createVariant(itemId, "SKU-001", 5);

        decrease(variantId, 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.quantity").value("Quantity must be greater than zero"));
        postJson(stockUrl(variantId, "increase"), Map.of("quantity", -2))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.quantity").value("Quantity must be greater than zero"));
        postJson(stockUrl(variantId, "decrease"), Map.of())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.quantity").value("Quantity is required"));

        mockMvc.perform(get(variantUrl(itemId, variantId))).andExpect(jsonPath("$.stockQuantity").value(5));
    }

    @Test
    void stockOperationOnUnknownVariantReturns404() throws Exception {
        decrease(999, 1)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"));
        postJson(stockUrl(999, "increase"), Map.of("quantity", 1)).andExpect(status().isNotFound());
    }

    @Test
    void stockOperationThroughWrongItemReturns404() throws Exception {
        long variantId = createVariant(itemId, "SKU-001", 5);
        long otherItemId = createItem("Sneakers");

        postJson(variantUrl(otherItemId, variantId) + "/stock/decrease", Map.of("quantity", 1))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(variantUrl(itemId, variantId))).andExpect(jsonPath("$.stockQuantity").value(5));
    }

    /**
     * 30 buyers race for 10 units. Exactly 10 sales may succeed, the rest must be rejected,
     * and the final stock must be exactly 0.
     */
    @Test
    void concurrentSalesNeverOversell() throws Exception {
        int initialStock = 10;
        int buyers = 30;
        long variantId = createVariant(itemId, "SKU-RACE", initialStock);

        ExecutorService pool = Executors.newFixedThreadPool(buyers);
        CountDownLatch startSignal = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try {
            for (int i = 0; i < buyers; i++) {
                Callable<Boolean> sale = () -> {
                    startSignal.await();
                    try {
                        stockService.decrease(itemId, variantId, 1);
                        return true;
                    } catch (InsufficientStockException rejected) {
                        return false;
                    }
                };
                results.add(pool.submit(sale));
            }
            startSignal.countDown();

            int succeeded = 0;
            for (Future<Boolean> result : results) {
                if (result.get(30, TimeUnit.SECONDS)) {
                    succeeded++;
                }
            }

            assertThat(succeeded).isEqualTo(initialStock);
            mockMvc.perform(get(variantUrl(itemId, variantId))).andExpect(jsonPath("$.stockQuantity").value(0));
        } finally {
            pool.shutdownNow();
        }
    }

    private ResultActions decrease(long variantId, int quantity) throws Exception {
        return postJson(stockUrl(variantId, "decrease"), Map.of("quantity", quantity));
    }

    private String stockUrl(long variantId, String operation) {
        return variantUrl(itemId, variantId) + "/stock/" + operation;
    }
}
