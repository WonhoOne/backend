package com.wonhoone.misterworld.api.dto;

import com.wonhoone.misterworld.domain.InventoryItemType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record InventoryAddRequest(@NotNull InventoryItemType itemType, @NotNull @Positive Long quantity) {}
