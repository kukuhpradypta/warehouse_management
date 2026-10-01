package com.shop.warehouse.service;

import com.shop.warehouse.dto.request.CreateVariantRequest;
import com.shop.warehouse.dto.request.UpdateVariantRequest;
import com.shop.warehouse.dto.response.VariantResponse;
import com.shop.warehouse.entity.Item;
import com.shop.warehouse.entity.Variant;
import com.shop.warehouse.exception.DuplicateSkuException;
import com.shop.warehouse.exception.ResourceNotFoundException;
import com.shop.warehouse.repository.ItemRepository;
import com.shop.warehouse.repository.VariantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VariantServiceTest {

    private static final long ITEM_ID = 1L;
    private static final long VARIANT_ID = 10L;
    private static final BigDecimal PRICE = new BigDecimal("149000.00");

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private VariantRepository variantRepository;

    @InjectMocks
    private VariantService variantService;

    @Test
    void createNormalisesSkuAndKeepsInitialStock() {
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item()));
        when(variantRepository.existsBySku("TS-BLK-M")).thenReturn(false);
        when(variantRepository.save(any(Variant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VariantResponse response = variantService.create(ITEM_ID,
                new CreateVariantRequest(" ts-blk-m ", " Black / M ", PRICE, 10));

        assertThat(response.sku()).isEqualTo("TS-BLK-M");
        assertThat(response.name()).isEqualTo("Black / M");
        assertThat(response.itemId()).isEqualTo(ITEM_ID);
        assertThat(response.stockQuantity()).isEqualTo(10);
    }

    @Test
    void createRejectsDuplicateSku() {
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item()));
        when(variantRepository.existsBySku("TS-BLK-M")).thenReturn(true);

        assertThatThrownBy(() -> variantService.create(ITEM_ID,
                new CreateVariantRequest("TS-BLK-M", "Black / M", PRICE, 10)))
                .isInstanceOf(DuplicateSkuException.class)
                .hasMessageContaining("TS-BLK-M");
        verify(variantRepository, never()).save(any());
    }

    @Test
    void createRequiresExistingItem() {
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> variantService.create(ITEM_ID,
                new CreateVariantRequest("TS-BLK-M", "Black / M", PRICE, 10)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Item 1 not found");
        verify(variantRepository, never()).save(any());
    }

    @Test
    void getByIdThrowsWhenVariantDoesNotBelongToItem() {
        when(variantRepository.findByIdAndItemId(VARIANT_ID, ITEM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> variantService.getById(ITEM_ID, VARIANT_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Variant 10 not found for item 1");
    }

    @Test
    void getAllByItemThrowsWhenItemDoesNotExist() {
        when(itemRepository.existsById(ITEM_ID)).thenReturn(false);

        assertThatThrownBy(() -> variantService.getAllByItem(ITEM_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateChangesDetailsButNotStock() {
        Variant variant = variant();
        when(variantRepository.findByIdAndItemId(VARIANT_ID, ITEM_ID)).thenReturn(Optional.of(variant));
        when(variantRepository.existsBySkuAndIdNot("TS-BLK-M2", VARIANT_ID)).thenReturn(false);

        VariantResponse response = variantService.update(ITEM_ID, VARIANT_ID,
                new UpdateVariantRequest("ts-blk-m2", "Black / M v2", new BigDecimal("139000.00")));

        assertThat(response.sku()).isEqualTo("TS-BLK-M2");
        assertThat(response.price()).isEqualByComparingTo("139000.00");
        assertThat(response.stockQuantity()).isEqualTo(5);
    }

    @Test
    void updateRejectsSkuUsedByAnotherVariant() {
        when(variantRepository.findByIdAndItemId(VARIANT_ID, ITEM_ID)).thenReturn(Optional.of(variant()));
        when(variantRepository.existsBySkuAndIdNot("TS-WHT-M", VARIANT_ID)).thenReturn(true);

        assertThatThrownBy(() -> variantService.update(ITEM_ID, VARIANT_ID,
                new UpdateVariantRequest("TS-WHT-M", "White / M", PRICE)))
                .isInstanceOf(DuplicateSkuException.class);
    }

    @Test
    void deleteRemovesVariant() {
        Variant variant = variant();
        when(variantRepository.findByIdAndItemId(VARIANT_ID, ITEM_ID)).thenReturn(Optional.of(variant));

        variantService.delete(ITEM_ID, VARIANT_ID);

        verify(variantRepository).delete(variant);
    }

    private static Item item() {
        Item item = new Item("T-Shirt", null);
        ReflectionTestUtils.setField(item, "id", ITEM_ID);
        return item;
    }

    private static Variant variant() {
        Variant variant = new Variant(item(), "TS-BLK-M", "Black / M", PRICE, 5);
        ReflectionTestUtils.setField(variant, "id", VARIANT_ID);
        return variant;
    }
}
