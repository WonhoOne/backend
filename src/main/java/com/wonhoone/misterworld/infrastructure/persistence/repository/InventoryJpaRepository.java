package com.wonhoone.misterworld.infrastructure.persistence.repository;

import com.wonhoone.misterworld.domain.InventoryItemType;
import com.wonhoone.misterworld.infrastructure.persistence.entity.InventoryJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface InventoryJpaRepository extends JpaRepository<InventoryJpaEntity, Long> {
    Optional<InventoryJpaEntity> findByItemType(InventoryItemType itemType);

    List<InventoryJpaEntity> findAllByOrderByIdAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select inventory from InventoryJpaEntity inventory
            where inventory.itemType = :itemType
            """)
    Optional<InventoryJpaEntity> findByItemTypeForUpdate(@Param("itemType") InventoryItemType itemType);
}
