package com.wonhoone.misterworld.application;

import com.wonhoone.misterworld.application.port.SmsSender;
import com.wonhoone.misterworld.domain.Reservation;
import com.wonhoone.misterworld.domain.TourSchedule;

import java.util.Objects;

public final class TourScheduleReservationService {
    private final SmsSender smsSender;

    public TourScheduleReservationService(SmsSender smsSender) {
        this.smsSender = Objects.requireNonNull(smsSender, "smsSender must not be null");
    }

    public boolean addReservation(TourSchedule schedule, Reservation reservation) {
        Objects.requireNonNull(schedule, "schedule must not be null");
        boolean becameConfirmed = schedule.addReservation(reservation);
        if (becameConfirmed) {
            schedule.reservations().forEach(
                    currentReservation -> smsSender.sendTourScheduleConfirmed(
                            currentReservation.customer().contact()));
        }
        return becameConfirmed;
    }
}
