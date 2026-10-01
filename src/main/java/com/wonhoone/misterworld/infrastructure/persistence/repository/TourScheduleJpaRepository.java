package com.wonhoone.misterworld.infrastructure.persistence.repository;

import com.wonhoone.misterworld.infrastructure.persistence.entity.TourScheduleJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TourScheduleJpaRepository extends JpaRepository<TourScheduleJpaEntity, Long> {
}
