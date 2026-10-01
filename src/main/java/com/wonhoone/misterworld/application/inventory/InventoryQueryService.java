package com.wonhoone.misterworld.application.inventory;

import com.wonhoone.misterworld.api.dto.InventoryResponse;
import com.wonhoone.misterworld.infrastructure.persistence.repository.InventoryJpaRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class InventoryQueryService {
    private final InventoryJpaRepository inventory;

    public InventoryQueryService(InventoryJpaRepository inventory) {
        this.inventory = inventory;
    }

    public List<InventoryResponse> list() {
        return inventory.findAllByOrderByIdAsc().stream().map(InventoryResponse::from).toList();
    }
}
