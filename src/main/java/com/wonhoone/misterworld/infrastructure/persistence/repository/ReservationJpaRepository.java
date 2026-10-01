package com.wonhoone.misterworld.infrastructure.persistence.repository;

import com.wonhoone.misterworld.infrastructure.persistence.entity.ReservationJpaEntity;
import com.wonhoone.misterworld.domain.Theme;
import com.wonhoone.misterworld.domain.TourStyle;
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

    /** Same completed predicate as Loyalty: current confirmation, historical period. No option collections are loaded. */
    @Query("""
            select reservation.id as reservationId,
                   reservation.tourProductIdSnapshot as tourProductId,
                   reservation.tourProductThemeSnapshot as tourProductTheme,
                   reservation.tourProductNameSnapshot as tourProductName,
                   reservation.scheduleStartDateSnapshot as startDate,
                   reservation.scheduleEndDateSnapshot as endDate,
                   reservation.configuration.style as style,
                   reservation.price.total as priceAmount,
                   reservation.price.currency as currency
            from ReservationJpaEntity reservation
            where reservation.customer.id = :customerId
              and reservation.tourSchedule.confirmed = true
              and reservation.scheduleEndDateSnapshot < :businessDate
            order by reservation.scheduleEndDateSnapshot desc, reservation.id desc
            """)
    List<TravelHistoryRow> findTravelHistory(@Param("customerId") long customerId,
                                            @Param("businessDate") LocalDate businessDate);

    interface TravelHistoryRow {
        long getReservationId();
        long getTourProductId();
        Theme getTourProductTheme();
        String getTourProductName();
        LocalDate getStartDate();
        LocalDate getEndDate();
        TourStyle getStyle();
        long getPriceAmount();
        String getCurrency();
    }
}
