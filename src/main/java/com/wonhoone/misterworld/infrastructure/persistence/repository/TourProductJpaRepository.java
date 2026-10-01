package com.wonhoone.misterworld.infrastructure.persistence.repository;

import com.wonhoone.misterworld.infrastructure.persistence.entity.TourProductJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TourProductJpaRepository extends JpaRepository<TourProductJpaEntity, Long> {
    java.util.List<TourProductJpaEntity> findAllByOrderByIdAsc();
}
