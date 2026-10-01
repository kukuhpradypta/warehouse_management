package com.shop.warehouse.service;

import com.shop.warehouse.dto.request.CreateVariantRequest;
import com.shop.warehouse.dto.request.UpdateVariantRequest;
import com.shop.warehouse.dto.response.VariantResponse;
import com.shop.warehouse.entity.Item;
import com.shop.warehouse.entity.Variant;
import com.shop.warehouse.exception.DuplicateSkuException;
import com.shop.warehouse.exception.ResourceNotFoundException;
import com.shop.warehouse.mapper.VariantMapper;
import com.shop.warehouse.repository.ItemRepository;
import com.shop.warehouse.repository.VariantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * Catalogue data of variants (SKU, name, price). Stock changes are handled by {@link StockService}.
 */
@Service
public class VariantService {

    private static final Logger log = LoggerFactory.getLogger(VariantService.class);

    private final ItemRepository itemRepository;
    private final VariantRepository variantRepository;

    public VariantService(ItemRepository itemRepository, VariantRepository variantRepository) {
        this.itemRepository = itemRepository;
        this.variantRepository = variantRepository;
    }

    /**
     * The existsBySku pre-check gives a clear error in the common case; the unique constraint
     * uk_variants_sku still guards the race where two requests create the same SKU at once.
     */
    @Transactional
    public VariantResponse create(Long itemId, CreateVariantRequest request) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> ResourceNotFoundException.item(itemId));
        String sku = normalizeSku(request.sku());
        if (variantRepository.existsBySku(sku)) {
            throw new DuplicateSkuException(sku);
        }

        Variant variant = variantRepository.save(
                new Variant(item, sku, request.name().trim(), request.price(), request.stockQuantity()));
        log.info("Created variant id={} sku={} for item id={} with stock={}",
                variant.getId(), sku, itemId, variant.getStockQuantity());
        return VariantMapper.toResponse(variant);
    }

    @Transactional(readOnly = true)
    public VariantResponse getById(Long itemId, Long variantId) {
        return VariantMapper.toResponse(findVariant(itemId, variantId));
    }

    @Transactional(readOnly = true)
    public List<VariantResponse> getAllByItem(Long itemId) {
        if (!itemRepository.existsById(itemId)) {
            throw ResourceNotFoundException.item(itemId);
        }
        return variantRepository.findAllByItemIdOrderByIdAsc(itemId).stream()
                .map(VariantMapper::toResponse)
                .toList();
    }

    @Transactional
    public VariantResponse update(Long itemId, Long variantId, UpdateVariantRequest request) {
        Variant variant = findVariant(itemId, variantId);
        String sku = normalizeSku(request.sku());
        if (variantRepository.existsBySkuAndIdNot(sku, variantId)) {
            throw new DuplicateSkuException(sku);
        }

        variant.updateDetails(sku, request.name().trim(), request.price());
        // Flush so the returned updatedAt reflects the persisted change.
        variantRepository.flush();
        log.info("Updated variant id={} sku={}", variantId, sku);
        return VariantMapper.toResponse(variant);
    }

    @Transactional
    public void delete(Long itemId, Long variantId) {
        Variant variant = findVariant(itemId, variantId);
        variantRepository.delete(variant);
        log.info("Deleted variant id={} sku={} (stock at deletion: {})",
                variantId, variant.getSku(), variant.getStockQuantity());
    }

    private Variant findVariant(Long itemId, Long variantId) {
        return variantRepository.findByIdAndItemId(variantId, itemId)
                .orElseThrow(() -> ResourceNotFoundException.variant(itemId, variantId));
    }

    /** SKUs are treated case-insensitively: "ts-blk-m" and "TS-BLK-M" are the same SKU. */
    static String normalizeSku(String sku) {
        return sku.trim().toUpperCase(Locale.ROOT);
    }
}
