package com.shop.warehouse.controller;

import com.shop.warehouse.dto.request.CreateVariantRequest;
import com.shop.warehouse.dto.request.UpdateVariantRequest;
import com.shop.warehouse.dto.response.VariantResponse;
import com.shop.warehouse.service.VariantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/items/{itemId}/variants")
@Tag(name = "Variants", description = "Sellable variants of an item (SKU, price, stock)")
public class VariantController {

    private final VariantService variantService;

    public VariantController(VariantService variantService) {
        this.variantService = variantService;
    }

    @PostMapping
    @Operation(summary = "Create a variant for an item")
    public ResponseEntity<VariantResponse> create(@PathVariable Long itemId,
                                                  @Valid @RequestBody CreateVariantRequest request) {
        VariantResponse created = variantService.create(itemId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{variantId}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    @Operation(summary = "List the variants of an item")
    public List<VariantResponse> getAll(@PathVariable Long itemId) {
        return variantService.getAllByItem(itemId);
    }

    @GetMapping("/{variantId}")
    @Operation(summary = "Get a variant")
    public VariantResponse getById(@PathVariable Long itemId, @PathVariable Long variantId) {
        return variantService.getById(itemId, variantId);
    }

    @PutMapping("/{variantId}")
    @Operation(summary = "Replace a variant's SKU, name and price",
            description = "Stock cannot be changed here; use the stock endpoints")
    public VariantResponse update(@PathVariable Long itemId, @PathVariable Long variantId,
                                  @Valid @RequestBody UpdateVariantRequest request) {
        return variantService.update(itemId, variantId, request);
    }

    @DeleteMapping("/{variantId}")
    @Operation(summary = "Delete a variant")
    public ResponseEntity<Void> delete(@PathVariable Long itemId, @PathVariable Long variantId) {
        variantService.delete(itemId, variantId);
        return ResponseEntity.noContent().build();
    }
}
