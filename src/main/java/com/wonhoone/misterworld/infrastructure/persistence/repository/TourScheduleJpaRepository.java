package com.wonhoone.misterworld.infrastructure.persistence.repository;

import com.wonhoone.misterworld.infrastructure.persistence.entity.TourScheduleJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TourScheduleJpaRepository extends JpaRepository<TourScheduleJpaEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select schedule from TourScheduleJpaEntity schedule
            where schedule.id = :scheduleId
            """)
    Optional<TourScheduleJpaEntity> findByIdForReservationUpdate(@Param("scheduleId") long scheduleId);

    boolean existsByTourProductId(Long tourProductId);

    @EntityGraph(attributePaths = "tourProduct")
    List<TourScheduleJpaEntity> findAllByOrderByStartDateAscIdAsc();

    @EntityGraph(attributePaths = "tourProduct")
    List<TourScheduleJpaEntity> findByTourProductIdOrderByStartDateAscIdAsc(Long tourProductId);

    @Override
    @EntityGraph(attributePaths = "tourProduct")
    Optional<TourScheduleJpaEntity> findById(Long id);
}
