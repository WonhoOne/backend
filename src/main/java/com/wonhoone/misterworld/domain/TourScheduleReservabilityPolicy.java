package com.wonhoone.misterworld.domain;

import java.time.LocalDate;

public final class TourScheduleReservabilityPolicy {
    private TourScheduleReservabilityPolicy() {}

    // BR-30: departure confirmation does not close intake before the departure date.
    public static boolean isReservable(LocalDate startDate, LocalDate businessDate) {
        return startDate.isAfter(businessDate);
    }
}
