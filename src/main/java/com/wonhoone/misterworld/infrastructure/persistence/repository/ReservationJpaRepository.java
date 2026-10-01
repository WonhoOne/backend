package com.wonhoone.misterworld.infrastructure.persistence.repository;

import com.wonhoone.misterworld.infrastructure.persistence.entity.ReservationJpaEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationJpaRepository extends JpaRepository<ReservationJpaEntity, Long> {
    @Query("""
            select reservation from ReservationJpaEntity reservation
            where reservation.id = :reservationId and reservation.customer.id = :customerId
            """)
    Optional<ReservationJpaEntity> findOwnedReservation(@Param("reservationId") long reservationId,
                                                       @Param("customerId") long customerId);

    @Query("""
            select coalesce(sum(reservation.participantCount), 0) from ReservationJpaEntity reservation
            where reservation.tourSchedule.id = :scheduleId
            """)
    long totalParticipantsForSchedule(@Param("scheduleId") long scheduleId);

    @Query("""
            select reservation.tourSchedule.id as scheduleId, sum(reservation.participantCount) as participantTotal
            from ReservationJpaEntity reservation
            where reservation.tourSchedule.id in :scheduleIds
            group by reservation.tourSchedule.id
            """)
    List<ScheduleParticipantTotal> totalParticipantsByScheduleIds(@Param("scheduleIds") List<Long> scheduleIds);

    interface ScheduleParticipantTotal {
        Long getScheduleId();
        long getParticipantTotal();
    }

    @Query("""
            select count(reservation) from ReservationJpaEntity reservation
            where reservation.customer.id = :customerId
              and reservation.tourSchedule.confirmed = true
              and reservation.scheduleEndDateSnapshot < :businessDate
            """)
    long countCompletedTrips(@Param("customerId") long customerId, @Param("businessDate") LocalDate businessDate);
}
