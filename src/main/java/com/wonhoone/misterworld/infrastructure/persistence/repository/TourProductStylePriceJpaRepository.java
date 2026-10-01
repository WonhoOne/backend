package com.wonhoone.misterworld.infrastructure.persistence.repository;

import com.wonhoone.misterworld.infrastructure.persistence.entity.TourProductStylePriceJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TourProductStylePriceJpaRepository extends JpaRepository<TourProductStylePriceJpaEntity, Long> {
    List<TourProductStylePriceJpaEntity> findByTourProductId(Long tourProductId);
    List<TourProductStylePriceJpaEntity> findByTourProductIdIn(List<Long> tourProductIds);
    void deleteByTourProductId(Long tourProductId);
}
