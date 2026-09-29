package com.wonhoone.misterworld.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainFoundationTests {
    @Test
    void sharedCatalogsContainOnlyApprovedValues() {
        assertEquals(List.of(Theme.HONEYMOON_ROMANCE, Theme.PARENTS_HEALING,
                        Theme.GOLF_CHALLENGE, Theme.OUTDOOR_TREKKING), List.of(Theme.values()));
        assertEquals(List.of(TourStyle.CLASSIC, TourStyle.GRAND, TourStyle.PREMIUM),
                List.of(TourStyle.values()));
    }

    @Test
    void reservationAcceptsOneOrMoreParticipants() {
        assertEquals(1, reservation(1, "A").participantCount());
        assertEquals(2, reservation(2, "A").participantCount());
    }

    @Test
    void reservationRejectsZeroAndNegativeParticipants() {
        assertThrows(IllegalArgumentException.class, () -> reservation(0, "A"));
        assertThrows(IllegalArgumentException.class, () -> reservation(-1, "A"));
    }

    @Test
    void generalScheduleConfirmsAtThreeParticipants() {
        TourSchedule schedule = schedule(Theme.GOLF_CHALLENGE);
        assertFalse(schedule.addReservation(reservation(2, "A")));
        assertFalse(schedule.isConfirmed());
        assertTrue(schedule.addReservation(reservation(1, "B")));
        assertTrue(schedule.isConfirmed());
    }

    @Test
    void honeymoonScheduleConfirmsAtFourParticipants() {
        TourSchedule schedule = schedule(Theme.HONEYMOON_ROMANCE);
        assertFalse(schedule.addReservation(reservation(3, "A")));
        assertFalse(schedule.isConfirmed());
        assertTrue(schedule.addReservation(reservation(1, "B")));
        assertTrue(schedule.isConfirmed());
    }

    @Test
    void sumsMultipleReservationsForGeneralSchedule() {
        TourSchedule schedule = schedule(Theme.OUTDOOR_TREKKING);
        schedule.addReservation(reservation(2, "A"));
        assertTrue(schedule.addReservation(reservation(1, "B")));
        assertEquals(3, schedule.totalParticipantCount());
    }

    @Test
    void sumsMultipleReservationsForHoneymoonSchedule() {
        TourSchedule schedule = schedule(Theme.HONEYMOON_ROMANCE);
        schedule.addReservation(reservation(2, "A"));
        assertTrue(schedule.addReservation(reservation(2, "B")));
        assertEquals(4, schedule.totalParticipantCount());
    }

    @Test
    void confirmsOnlyOnTheFirstThresholdCrossing() {
        TourSchedule schedule = schedule(Theme.GOLF_CHALLENGE);
        assertFalse(schedule.addReservation(reservation(2, "A")));
        assertTrue(schedule.addReservation(reservation(1, "B")));
        assertFalse(schedule.addReservation(reservation(1, "C")));
        assertTrue(schedule.isConfirmed());
    }

    @Test
    void stylePolicyRejectsClassicForHoneymoonAndParentsHealing() {
        assertFalse(TourStylePolicy.isAllowed(Theme.HONEYMOON_ROMANCE, TourStyle.CLASSIC));
        assertFalse(TourStylePolicy.isAllowed(Theme.PARENTS_HEALING, TourStyle.CLASSIC));
        assertThrows(IllegalArgumentException.class,
                () -> TourStylePolicy.validate(Theme.HONEYMOON_ROMANCE, TourStyle.CLASSIC));
    }

    @Test
    void stylePolicyAllowsGrandAndPremiumForRestrictedThemes() {
        for (Theme theme : List.of(Theme.HONEYMOON_ROMANCE, Theme.PARENTS_HEALING)) {
            assertTrue(TourStylePolicy.isAllowed(theme, TourStyle.GRAND));
            assertTrue(TourStylePolicy.isAllowed(theme, TourStyle.PREMIUM));
            TourStylePolicy.validate(theme, TourStyle.GRAND);
            TourStylePolicy.validate(theme, TourStyle.PREMIUM);
        }
    }

    @Test
    void stylePolicyAllowsEveryStyleForGolfAndTrekking() {
        for (Theme theme : List.of(Theme.GOLF_CHALLENGE, Theme.OUTDOOR_TREKKING)) {
            for (TourStyle style : TourStyle.values()) {
                assertTrue(TourStylePolicy.isAllowed(theme, style));
                TourStylePolicy.validate(theme, style);
            }
        }
    }

    @Test
    void reservationsCannotBeChangedThroughReturnedCollection() {
        TourSchedule schedule = schedule(Theme.GOLF_CHALLENGE);
        schedule.addReservation(reservation(1, "A"));
        assertThrows(UnsupportedOperationException.class,
                () -> schedule.reservations().add(reservation(1, "B")));
    }

    private static TourSchedule schedule(Theme theme) {
        return new TourSchedule(new TourProduct(theme));
    }

    private static Reservation reservation(int count, String name) {
        return new Reservation(new Customer(name, "address", "contact-" + name), count);
    }
}
