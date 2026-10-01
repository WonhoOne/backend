package com.wonhoone.misterworld.application.inventory;

import com.wonhoone.misterworld.api.dto.InventoryAddRequest;
import com.wonhoone.misterworld.api.dto.InventoryResponse;
import com.wonhoone.misterworld.infrastructure.persistence.repository.InventoryJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class InventoryCommandService {
    private final InventoryJpaRepository inventory;

    public InventoryCommandService(InventoryJpaRepository inventory) {
        this.inventory = inventory;
    }

    public InventoryResponse add(InventoryAddRequest request) {
        // The existing catalog row is the serialization boundary until commit/rollback.
        var item = inventory.findByItemTypeForUpdate(request.itemType())
                .orElseThrow(() -> new IllegalStateException("Inventory catalog row is missing"));
        item.addQuantity(request.quantity());
        inventory.flush();
        return InventoryResponse.from(item);
    }
}
