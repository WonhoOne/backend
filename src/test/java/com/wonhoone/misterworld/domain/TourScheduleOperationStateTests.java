package com.wonhoone.misterworld.domain;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TourScheduleOperationStateTests {
    private static Reservation party(int count) {
        return new Reservation(new Customer("Guest", "address", "contact"), count);
    }

    @Test void confirmedScheduleDepartsThenCompletesAndStopsAcceptingReservations() {
        var schedule = new TourSchedule(new TourProduct(Theme.OUTDOOR_TREKKING));
        assertThat(schedule.operationStatus()).isEqualTo(ScheduleOperationStatus.SCHEDULED);
        assertThatThrownBy(schedule::depart).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(schedule::complete).isInstanceOf(IllegalStateException.class);

        assertThat(schedule.addReservation(party(3))).isTrue();
        schedule.depart();
        assertThat(schedule.operationStatus()).isEqualTo(ScheduleOperationStatus.DEPARTED);
        assertThatThrownBy(schedule::depart).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> schedule.addReservation(party(1))).isInstanceOf(IllegalStateException.class);

        schedule.complete();
        assertThat(schedule.operationStatus()).isEqualTo(ScheduleOperationStatus.COMPLETED);
        assertThatThrownBy(schedule::complete).isInstanceOf(IllegalStateException.class);
        assertThat(schedule.isConfirmed()).isTrue();
    }
}
