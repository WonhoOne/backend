package com.wonhoone.misterworld.infrastructure.persistence.repository;

import com.wonhoone.misterworld.infrastructure.persistence.entity.TourProductStylePriceJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import com.wonhoone.misterworld.domain.TourStyle;

public interface TourProductStylePriceJpaRepository extends JpaRepository<TourProductStylePriceJpaEntity, Long> {
    Optional<TourProductStylePriceJpaEntity> findByTourProductIdAndStyle(Long tourProductId, TourStyle style);
    List<TourProductStylePriceJpaEntity> findByTourProductId(Long tourProductId);
    List<TourProductStylePriceJpaEntity> findByTourProductIdIn(List<Long> tourProductIds);
    void deleteByTourProductId(Long tourProductId);
}
