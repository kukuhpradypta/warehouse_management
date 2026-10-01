package com.shop.warehouse.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * A sellable unit of an {@link Item} (e.g. "Black / M"). Owns the price and the stock level.
 */
@Entity
@Table(name = "variants")
public class Variant {

    public static final int SKU_MAX_LENGTH = 64;
    public static final int NAME_MAX_LENGTH = 150;
    public static final int PRICE_PRECISION = 12;
    public static final int PRICE_SCALE = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false, updatable = false)
    private Item item;

    @Column(nullable = false, unique = true, length = SKU_MAX_LENGTH)
    private String sku;

    @Column(nullable = false, length = NAME_MAX_LENGTH)
    private String name;

    @Column(nullable = false, precision = PRICE_PRECISION, scale = PRICE_SCALE)
    private BigDecimal price;

    /*
     * Not updatable through entity dirty checking: stock only changes via the atomic
     * UPDATE statements in VariantRepository. This prevents a concurrent PUT of name/price
     * from writing back a stale stock value and silently undoing a sale.
     */
    @Column(name = "stock_quantity", nullable = false, updatable = false)
    private int stockQuantity;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Variant() {
        // required by JPA
    }

    public Variant(Item item, String sku, String name, BigDecimal price, int initialStock) {
        this.item = item;
        this.stockQuantity = initialStock;
        updateDetails(sku, name, price);
    }

    public void updateDetails(String sku, String name, BigDecimal price) {
        this.sku = sku;
        this.name = name;
        // Consistent scale so 149000 and 149000.00 serialise identically; input is validated to <= 2 decimals.
        this.price = price.setScale(PRICE_SCALE, RoundingMode.UNNECESSARY);
    }

    public Long getId() {
        return id;
    }

    public Item getItem() {
        return item;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getStockQuantity() {
        return stockQuantity;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
