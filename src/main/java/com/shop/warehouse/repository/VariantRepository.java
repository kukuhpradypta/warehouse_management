package com.shop.warehouse.repository;

import com.shop.warehouse.entity.Variant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface VariantRepository extends JpaRepository<Variant, Long> {

    Optional<Variant> findByIdAndItemId(Long id, Long itemId);

    List<Variant> findAllByItemIdOrderByIdAsc(Long itemId);

    boolean existsByItemId(Long itemId);

    boolean existsBySku(String sku);

    boolean existsBySkuAndIdNot(String sku, Long id);

    /**
     * Adds stock in a single statement, so concurrent increases never overwrite each other.
     *
     * @return number of updated rows: 1 on success, 0 if the variant does not exist under the item
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Variant v
               set v.stockQuantity = v.stockQuantity + :quantity,
                   v.updatedAt = :now
             where v.id = :variantId
               and v.item.id = :itemId
            """)
    int increaseStock(@Param("itemId") Long itemId,
                      @Param("variantId") Long variantId,
                      @Param("quantity") int quantity,
                      @Param("now") Instant now);

    /**
     * Check-and-decrement in one statement. The row lock taken by UPDATE serialises concurrent
     * sales, and the {@code stockQuantity >= :quantity} guard is re-evaluated against the latest
     * committed value, so stock can never go below zero.
     *
     * @return 1 on success, 0 if the variant does not exist under the item or stock is insufficient
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Variant v
               set v.stockQuantity = v.stockQuantity - :quantity,
                   v.updatedAt = :now
             where v.id = :variantId
               and v.item.id = :itemId
               and v.stockQuantity >= :quantity
            """)
    int decreaseStock(@Param("itemId") Long itemId,
                      @Param("variantId") Long variantId,
                      @Param("quantity") int quantity,
                      @Param("now") Instant now);
}
