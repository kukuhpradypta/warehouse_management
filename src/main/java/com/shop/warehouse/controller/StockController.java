package com.shop.warehouse.controller;

import com.shop.warehouse.dto.request.StockOperationRequest;
import com.shop.warehouse.dto.response.VariantResponse;
import com.shop.warehouse.service.StockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stock changes are modelled as actions (POST) rather than a PUT of the absolute value, so concurrent
 * clients express intent ("sell 3") instead of overwriting each other's results.
 */
@RestController
@RequestMapping("/api/items/{itemId}/variants/{variantId}/stock")
@Tag(name = "Stock", description = "Restock and sell variants")
public class StockController {

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    @PostMapping("/increase")
    @Operation(summary = "Increase stock (restock)")
    public VariantResponse increase(@PathVariable Long itemId, @PathVariable Long variantId,
                                    @Valid @RequestBody StockOperationRequest request) {
        return stockService.increase(itemId, variantId, request.quantity());
    }

    @PostMapping("/decrease")
    @Operation(summary = "Decrease stock (sell)",
            description = "Rejected with 409 INSUFFICIENT_STOCK if the quantity exceeds the available stock")
    public VariantResponse decrease(@PathVariable Long itemId, @PathVariable Long variantId,
                                    @Valid @RequestBody StockOperationRequest request) {
        return stockService.decrease(itemId, variantId, request.quantity());
    }
}
