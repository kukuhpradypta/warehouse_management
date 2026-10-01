package com.shop.warehouse.service;

import com.shop.warehouse.dto.request.CreateItemRequest;
import com.shop.warehouse.dto.request.UpdateItemRequest;
import com.shop.warehouse.dto.response.ItemResponse;
import com.shop.warehouse.entity.Item;
import com.shop.warehouse.exception.ItemHasVariantsException;
import com.shop.warehouse.exception.ResourceNotFoundException;
import com.shop.warehouse.mapper.ItemMapper;
import com.shop.warehouse.repository.ItemRepository;
import com.shop.warehouse.repository.VariantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ItemService {

    private static final Logger log = LoggerFactory.getLogger(ItemService.class);

    private final ItemRepository itemRepository;
    private final VariantRepository variantRepository;

    public ItemService(ItemRepository itemRepository, VariantRepository variantRepository) {
        this.itemRepository = itemRepository;
        this.variantRepository = variantRepository;
    }

    @Transactional
    public ItemResponse create(CreateItemRequest request) {
        Item item = itemRepository.save(new Item(request.name().trim(), normalizeDescription(request.description())));
        log.info("Created item id={} name='{}'", item.getId(), item.getName());
        return ItemMapper.toResponse(item);
    }

    @Transactional(readOnly = true)
    public ItemResponse getById(Long itemId) {
        return ItemMapper.toResponse(findItem(itemId));
    }

    @Transactional(readOnly = true)
    public List<ItemResponse> getAll() {
        return itemRepository.findAllByOrderByIdAsc().stream()
                .map(ItemMapper::toResponse)
                .toList();
    }

    @Transactional
    public ItemResponse update(Long itemId, UpdateItemRequest request) {
        Item item = findItem(itemId);
        item.updateDetails(request.name().trim(), normalizeDescription(request.description()));
        // Flush so the returned updatedAt reflects the persisted change.
        itemRepository.flush();
        log.info("Updated item id={}", itemId);
        return ItemMapper.toResponse(item);
    }

    /**
     * Items with variants are protected from deletion so stock records are never removed as a side effect.
     * The FK (ON DELETE RESTRICT) enforces the same rule at database level.
     */
    @Transactional
    public void delete(Long itemId) {
        Item item = findItem(itemId);
        if (variantRepository.existsByItemId(itemId)) {
            throw new ItemHasVariantsException(itemId);
        }
        itemRepository.delete(item);
        log.info("Deleted item id={}", itemId);
    }

    private Item findItem(Long itemId) {
        return itemRepository.findById(itemId)
                .orElseThrow(() -> ResourceNotFoundException.item(itemId));
    }

    private static String normalizeDescription(String description) {
        return description == null || description.isBlank() ? null : description.trim();
    }
}
