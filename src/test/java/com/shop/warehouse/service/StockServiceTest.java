package com.shop.warehouse.service;

import com.shop.warehouse.dto.response.VariantResponse;
import com.shop.warehouse.entity.Item;
import com.shop.warehouse.entity.Variant;
import com.shop.warehouse.exception.InsufficientStockException;
import com.shop.warehouse.exception.InvalidStockQuantityException;
import com.shop.warehouse.exception.ResourceNotFoundException;
import com.shop.warehouse.repository.VariantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers the service's decision logic. The atomicity of the stock UPDATE itself is verified against a
 * real database in StockApiIntegrationTest.
 */
@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    private static final long ITEM_ID = 1L;
    private static final long VARIANT_ID = 10L;
    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    @Mock
    private VariantRepository variantRepository;

    private StockService stockService;

    @BeforeEach
    void setUp() {
        stockService = new StockService(variantRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void increaseReturnsReloadedVariant() {
        when(variantRepository.increaseStock(ITEM_ID, VARIANT_ID, 5, NOW)).thenReturn(1);
        when(variantRepository.findByIdAndItemId(VARIANT_ID, ITEM_ID)).thenReturn(Optional.of(variantWithStock(15)));

        VariantResponse response = stockService.increase(ITEM_ID, VARIANT_ID, 5);

        assertThat(response.stockQuantity()).isEqualTo(15);
    }

    @Test
    void increaseThrowsNotFoundWhenNoRowWasUpdated() {
        when(variantRepository.increaseStock(ITEM_ID, VARIANT_ID, 5, NOW)).thenReturn(0);

        assertThatThrownBy(() -> stockService.increase(ITEM_ID, VARIANT_ID, 5))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void decreaseSucceedsWhenGuardedUpdateMatches() {
        when(variantRepository.decreaseStock(ITEM_ID, VARIANT_ID, 3, NOW)).thenReturn(1);
        when(variantRepository.findByIdAndItemId(VARIANT_ID, ITEM_ID)).thenReturn(Optional.of(variantWithStock(2)));

        VariantResponse response = stockService.decrease(ITEM_ID, VARIANT_ID, 3);

        assertThat(response.stockQuantity()).isEqualTo(2);
        // Stock is only changed by the conditional UPDATE, never by a read-modify-write save().
        verify(variantRepository, never()).save(any());
    }

    @Test
    void decreaseThrowsInsufficientStockWhenVariantExistsButUpdateMatchedNothing() {
        when(variantRepository.decreaseStock(ITEM_ID, VARIANT_ID, 3, NOW)).thenReturn(0);
        when(variantRepository.findByIdAndItemId(VARIANT_ID, ITEM_ID)).thenReturn(Optional.of(variantWithStock(2)));

        assertThatThrownBy(() -> stockService.decrease(ITEM_ID, VARIANT_ID, 3))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessage("Insufficient stock for variant TS-BLK-M: requested 3, available 2");
    }

    @Test
    void decreaseThrowsNotFoundWhenVariantDoesNotExist() {
        when(variantRepository.decreaseStock(ITEM_ID, VARIANT_ID, 3, NOW)).thenReturn(0);
        when(variantRepository.findByIdAndItemId(VARIANT_ID, ITEM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stockService.decrease(ITEM_ID, VARIANT_ID, 3))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void nonPositiveQuantitiesAreRejectedBeforeTouchingTheDatabase(int quantity) {
        assertThatThrownBy(() -> stockService.decrease(ITEM_ID, VARIANT_ID, quantity))
                .isInstanceOf(InvalidStockQuantityException.class);
        assertThatThrownBy(() -> stockService.increase(ITEM_ID, VARIANT_ID, quantity))
                .isInstanceOf(InvalidStockQuantityException.class);
        verifyNoInteractions(variantRepository);
    }

    private static Variant variantWithStock(int stock) {
        Item item = new Item("T-Shirt", null);
        ReflectionTestUtils.setField(item, "id", ITEM_ID);
        Variant variant = new Variant(item, "TS-BLK-M", "Black / M", new BigDecimal("149000.00"), stock);
        ReflectionTestUtils.setField(variant, "id", VARIANT_ID);
        return variant;
    }
}
