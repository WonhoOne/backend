package com.wonhoone.misterworld.infrastructure.persistence.repository;

import com.wonhoone.misterworld.infrastructure.persistence.entity.ReservationJpaEntity;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationJpaRepository extends JpaRepository<ReservationJpaEntity, Long> {
    @Query("""
            select count(reservation) from ReservationJpaEntity reservation
            where reservation.customer.id = :customerId
              and reservation.tourSchedule.confirmed = true
              and reservation.scheduleEndDateSnapshot < :businessDate
            """)
    long countCompletedTrips(@Param("customerId") long customerId, @Param("businessDate") LocalDate businessDate);
}
