package com.shop.warehouse.service;

import com.shop.warehouse.dto.request.CreateItemRequest;
import com.shop.warehouse.dto.request.UpdateItemRequest;
import com.shop.warehouse.dto.response.ItemResponse;
import com.shop.warehouse.entity.Item;
import com.shop.warehouse.exception.ItemHasVariantsException;
import com.shop.warehouse.exception.ResourceNotFoundException;
import com.shop.warehouse.repository.ItemRepository;
import com.shop.warehouse.repository.VariantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItemServiceTest {

    private static final long ITEM_ID = 1L;

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private VariantRepository variantRepository;

    @InjectMocks
    private ItemService itemService;

    @Test
    void createTrimsInputAndTreatsBlankDescriptionAsAbsent() {
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));

        ItemResponse response = itemService.create(new CreateItemRequest("  T-Shirt  ", "   "));

        ArgumentCaptor<Item> saved = ArgumentCaptor.forClass(Item.class);
        verify(itemRepository).save(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("T-Shirt");
        assertThat(saved.getValue().getDescription()).isNull();
        assertThat(response.id()).isEqualTo(ITEM_ID);
    }

    @Test
    void getByIdReturnsItem() {
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(withId(new Item("T-Shirt", "Cotton"))));

        ItemResponse response = itemService.getById(ITEM_ID);

        assertThat(response.name()).isEqualTo("T-Shirt");
        assertThat(response.description()).isEqualTo("Cotton");
    }

    @Test
    void getByIdThrowsWhenItemDoesNotExist() {
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> itemService.getById(ITEM_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Item 1 not found");
    }

    @Test
    void getAllReturnsItemsInRepositoryOrder() {
        when(itemRepository.findAllByOrderByIdAsc())
                .thenReturn(List.of(new Item("T-Shirt", null), new Item("Sneakers", null)));

        assertThat(itemService.getAll()).extracting(ItemResponse::name).containsExactly("T-Shirt", "Sneakers");
    }

    @Test
    void updateReplacesNameAndDescription() {
        Item item = withId(new Item("T-Shirt", "Cotton"));
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));

        ItemResponse response = itemService.update(ITEM_ID, new UpdateItemRequest("T-Shirt Premium", null));

        assertThat(response.name()).isEqualTo("T-Shirt Premium");
        assertThat(response.description()).isNull();
        verify(itemRepository).flush();
    }

    @Test
    void updateThrowsWhenItemDoesNotExist() {
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> itemService.update(ITEM_ID, new UpdateItemRequest("x", null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteRemovesItemWithoutVariants() {
        Item item = withId(new Item("T-Shirt", null));
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));
        when(variantRepository.existsByItemId(ITEM_ID)).thenReturn(false);

        itemService.delete(ITEM_ID);

        verify(itemRepository).delete(item);
    }

    @Test
    void deleteIsRejectedWhileItemHasVariants() {
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(withId(new Item("T-Shirt", null))));
        when(variantRepository.existsByItemId(ITEM_ID)).thenReturn(true);

        assertThatThrownBy(() -> itemService.delete(ITEM_ID)).isInstanceOf(ItemHasVariantsException.class);
        verify(itemRepository, never()).delete(any());
    }

    @Test
    void deleteThrowsWhenItemDoesNotExist() {
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> itemService.delete(ITEM_ID)).isInstanceOf(ResourceNotFoundException.class);
        verify(itemRepository, never()).delete(any());
    }

    private static Item withId(Item item) {
        ReflectionTestUtils.setField(item, "id", ITEM_ID);
        return item;
    }
}
