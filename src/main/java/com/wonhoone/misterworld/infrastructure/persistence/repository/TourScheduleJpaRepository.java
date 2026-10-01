package com.wonhoone.misterworld.infrastructure.persistence.repository;

import com.wonhoone.misterworld.infrastructure.persistence.entity.TourScheduleJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.List;
import java.util.Optional;

public interface TourScheduleJpaRepository extends JpaRepository<TourScheduleJpaEntity, Long> {
    boolean existsByTourProductId(Long tourProductId);

    @EntityGraph(attributePaths = "tourProduct")
    List<TourScheduleJpaEntity> findAllByOrderByStartDateAscIdAsc();

    @EntityGraph(attributePaths = "tourProduct")
    List<TourScheduleJpaEntity> findByTourProductIdOrderByStartDateAscIdAsc(Long tourProductId);

    @Override
    @EntityGraph(attributePaths = "tourProduct")
    Optional<TourScheduleJpaEntity> findById(Long id);
}
