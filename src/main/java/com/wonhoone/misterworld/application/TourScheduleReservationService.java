package com.wonhoone.misterworld.application;

import com.wonhoone.misterworld.application.port.SmsSender;
import com.wonhoone.misterworld.application.port.SmsMessage;
import com.wonhoone.misterworld.domain.Reservation;
import com.wonhoone.misterworld.domain.TourSchedule;

import java.util.Objects;

/** Legacy in-memory foundation only. Production reservations use the durable B8 outbox. */
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
                    currentReservation -> smsSender.send(new SmsMessage(
                            currentReservation.customer().contact(), "여행의 출발이 확정되었습니다.")));
        }
        return becameConfirmed;
    }
}
