package com.wonhoone.misterworld.api.dto;

import com.wonhoone.misterworld.domain.InventoryItemType;
import com.wonhoone.misterworld.infrastructure.persistence.entity.InventoryJpaEntity;

public record InventoryResponse(long id, InventoryItemType itemType, long quantity) {
    public static InventoryResponse from(InventoryJpaEntity inventory) {
        return new InventoryResponse(inventory.getId(), inventory.getItemType(), inventory.getQuantity());
    }
}
