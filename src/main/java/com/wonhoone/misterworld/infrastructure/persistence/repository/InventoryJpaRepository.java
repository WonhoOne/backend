package com.wonhoone.misterworld.infrastructure.persistence.repository;

import com.wonhoone.misterworld.domain.InventoryItemType;
import com.wonhoone.misterworld.infrastructure.persistence.entity.InventoryJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InventoryJpaRepository extends JpaRepository<InventoryJpaEntity, Long> {
    Optional<InventoryJpaEntity> findByItemType(InventoryItemType itemType);
}
