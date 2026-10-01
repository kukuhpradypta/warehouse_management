package com.shop.warehouse.controller;

import com.shop.warehouse.dto.request.CreateItemRequest;
import com.shop.warehouse.dto.request.UpdateItemRequest;
import com.shop.warehouse.dto.response.ItemResponse;
import com.shop.warehouse.service.ItemService;
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
@RequestMapping("/api/items")
@Tag(name = "Items", description = "Catalogue items")
public class ItemController {

    private final ItemService itemService;

    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    @PostMapping
    @Operation(summary = "Create an item")
    public ResponseEntity<ItemResponse> create(@Valid @RequestBody CreateItemRequest request) {
        ItemResponse created = itemService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    @Operation(summary = "List all items")
    public List<ItemResponse> getAll() {
        return itemService.getAll();
    }

    @GetMapping("/{itemId}")
    @Operation(summary = "Get an item by id")
    public ItemResponse getById(@PathVariable Long itemId) {
        return itemService.getById(itemId);
    }

    @PutMapping("/{itemId}")
    @Operation(summary = "Replace an item's name and description")
    public ItemResponse update(@PathVariable Long itemId, @Valid @RequestBody UpdateItemRequest request) {
        return itemService.update(itemId, request);
    }

    @DeleteMapping("/{itemId}")
    @Operation(summary = "Delete an item", description = "Rejected with 409 ITEM_HAS_VARIANTS while the item still has variants")
    public ResponseEntity<Void> delete(@PathVariable Long itemId) {
        itemService.delete(itemId);
        return ResponseEntity.noContent().build();
    }
}
