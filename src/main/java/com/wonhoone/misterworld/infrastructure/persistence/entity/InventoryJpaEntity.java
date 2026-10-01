package com.wonhoone.misterworld.infrastructure.persistence.entity;

import com.wonhoone.misterworld.domain.InventoryItemType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "inventory",
        uniqueConstraints = @UniqueConstraint(name = "uk_inventory_item_type", columnNames = "item_type"))
public class InventoryJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "item_type", nullable = false, length = 32)
    private InventoryItemType itemType;

    @Column(name = "quantity", nullable = false)
    private long quantity;

    protected InventoryJpaEntity() {
    }

    public InventoryJpaEntity(InventoryItemType itemType, long quantity) {
        this.itemType = Objects.requireNonNull(itemType, "itemType must not be null");
        if (quantity < 0) {
            throw new IllegalArgumentException("quantity must not be negative");
        }
        this.quantity = quantity;
    }

    public Long getId() {
        return id;
    }

    public InventoryItemType getItemType() {
        return itemType;
    }

    public long getQuantity() {
        return quantity;
    }
}
