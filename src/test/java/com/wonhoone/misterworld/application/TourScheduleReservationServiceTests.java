package com.wonhoone.misterworld.application;

import com.wonhoone.misterworld.application.port.SmsSender;
import com.wonhoone.misterworld.domain.Customer;
import com.wonhoone.misterworld.domain.Reservation;
import com.wonhoone.misterworld.domain.Theme;
import com.wonhoone.misterworld.domain.TourProduct;
import com.wonhoone.misterworld.domain.TourSchedule;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TourScheduleReservationServiceTests {
    @Test
    void sendsNoSmsBeforeScheduleConfirmation() {
        RecordingSmsSender smsSender = new RecordingSmsSender();
        TourScheduleReservationService service = new TourScheduleReservationService(smsSender);
        TourSchedule schedule = schedule(Theme.GOLF_CHALLENGE);

        assertFalse(service.addReservation(schedule, reservation("A", 2)));
        assertEquals(List.of(), smsSender.contacts);
    }

    @Test
    void sendsSmsToCurrentApplicantCustomersOnFirstConfirmation() {
        RecordingSmsSender smsSender = new RecordingSmsSender();
        TourScheduleReservationService service = new TourScheduleReservationService(smsSender);
        TourSchedule schedule = schedule(Theme.GOLF_CHALLENGE);

        service.addReservation(schedule, reservation("A", 2));
        assertTrue(service.addReservation(schedule, reservation("B", 1)));

        assertEquals(List.of("contact-A", "contact-B"), smsSender.contacts);
    }

    @Test
    void doesNotSendAnotherFirstConfirmationBatchAfterConfirmation() {
        RecordingSmsSender smsSender = new RecordingSmsSender();
        TourScheduleReservationService service = new TourScheduleReservationService(smsSender);
        TourSchedule schedule = schedule(Theme.GOLF_CHALLENGE);

        service.addReservation(schedule, reservation("A", 2));
        service.addReservation(schedule, reservation("B", 1));
        assertFalse(service.addReservation(schedule, reservation("C", 1)));

        assertEquals(List.of("contact-A", "contact-B"), smsSender.contacts);
    }

    private static TourSchedule schedule(Theme theme) {
        return new TourSchedule(new TourProduct(theme));
    }

    private static Reservation reservation(String name, int count) {
        return new Reservation(new Customer(name, "address", "contact-" + name), count);
    }

    private static final class RecordingSmsSender implements SmsSender {
        private final List<String> contacts = new ArrayList<>();

        @Override
        public void sendTourScheduleConfirmed(String contact) {
            contacts.add(contact);
        }
    }
}
