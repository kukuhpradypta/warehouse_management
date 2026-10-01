package com.shop.warehouse.service;

import com.shop.warehouse.dto.response.VariantResponse;
import com.shop.warehouse.entity.Variant;
import com.shop.warehouse.exception.InsufficientStockException;
import com.shop.warehouse.exception.InvalidStockQuantityException;
import com.shop.warehouse.exception.ResourceNotFoundException;
import com.shop.warehouse.mapper.VariantMapper;
import com.shop.warehouse.repository.VariantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * The only place where stock levels change.
 *
 * <p>Concurrency strategy: each change is a single conditional UPDATE executed by the database
 * (see {@link VariantRepository#decreaseStock}). There is no read-modify-write window in Java,
 * so no lost updates and no retries are needed; the CHECK (stock_quantity >= 0) constraint is
 * a final safety net.
 */
@Service
public class StockService {

    private static final Logger log = LoggerFactory.getLogger(StockService.class);

    private final VariantRepository variantRepository;
    private final Clock clock;

    public StockService(VariantRepository variantRepository, Clock clock) {
        this.variantRepository = variantRepository;
        this.clock = clock;
    }

    @Transactional
    public VariantResponse increase(Long itemId, Long variantId, int quantity) {
        requirePositive(quantity);
        int updatedRows = variantRepository.increaseStock(itemId, variantId, quantity, Instant.now(clock));
        if (updatedRows == 0) {
            throw ResourceNotFoundException.variant(itemId, variantId);
        }

        Variant variant = loadVariant(itemId, variantId);
        log.info("Stock increased: sku={} quantity={} newStock={}",
                variant.getSku(), quantity, variant.getStockQuantity());
        return VariantMapper.toResponse(variant);
    }

    @Transactional
    public VariantResponse decrease(Long itemId, Long variantId, int quantity) {
        requirePositive(quantity);
        int updatedRows = variantRepository.decreaseStock(itemId, variantId, quantity, Instant.now(clock));
        if (updatedRows == 0) {
            // Either the variant does not exist, or the guarded UPDATE matched nothing because stock is too low.
            Variant variant = loadVariant(itemId, variantId);
            throw new InsufficientStockException(variant.getSku(), variant.getStockQuantity(), quantity);
        }

        Variant variant = loadVariant(itemId, variantId);
        log.info("Stock decreased: sku={} quantity={} newStock={}",
                variant.getSku(), quantity, variant.getStockQuantity());
        return VariantMapper.toResponse(variant);
    }

    private Variant loadVariant(Long itemId, Long variantId) {
        return variantRepository.findByIdAndItemId(variantId, itemId)
                .orElseThrow(() -> ResourceNotFoundException.variant(itemId, variantId));
    }

    /** Request validation already rejects this; kept so the rule holds for any caller of the service. */
    private static void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw new InvalidStockQuantityException(quantity);
        }
    }
}
