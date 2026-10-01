package com.wonhoone.misterworld.application.reservation;

import com.wonhoone.misterworld.api.dto.ReservationResponse;
import com.wonhoone.misterworld.api.error.*;
import com.wonhoone.misterworld.application.tour.ScheduleRecruitmentProjection;
import com.wonhoone.misterworld.domain.TourScheduleRecruitmentPolicy;
import com.wonhoone.misterworld.infrastructure.persistence.repository.ReservationJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReservationQueryService {
    private final ReservationJpaRepository reservations;
    private final ReservationResponseFactory responses;

    public ReservationQueryService(ReservationJpaRepository reservations, ReservationResponseFactory responses) {
        this.reservations = reservations;
        this.responses = responses;
    }

    public ReservationResponse detail(long customerId, long reservationId) {
        InvalidRequestParameterException.requirePositive(reservationId);
        var reservation = reservations.findOwnedReservation(reservationId, customerId).orElseThrow(() ->
                new ResourceNotFoundException("RESERVATION_NOT_FOUND", "Reservation was not found."));
        var schedule = reservation.getTourSchedule();
        var theme = schedule.getTourProduct().getTheme();
        long participantTotal = reservations.totalParticipantsForSchedule(schedule.getId());
        long currentCount = TourScheduleRecruitmentPolicy.currentCountFromParticipants(theme, participantTotal);
        var recruitment = ScheduleRecruitmentProjection.create(theme, currentCount, schedule.isConfirmed());
        return responses.create(reservation, recruitment);
    }
}
