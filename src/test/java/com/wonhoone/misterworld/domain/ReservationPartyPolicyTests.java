package com.wonhoone.misterworld.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReservationPartyPolicyTests {
    @Test void generalReservationAllowsOneParticipant() { validateGeneral(1); }
    @Test void generalReservationAllowsTenParticipants() { validateGeneral(10); }
    @Test void generalReservationRejectsZero() { rejectsGeneral(0); }
    @Test void generalReservationRejectsEleven() { rejectsGeneral(11); }
    @Test void honeymoonAllowsTwoFourSixEightTen() {
        for (int count : new int[]{2, 4, 6, 8, 10}) ReservationPartyPolicy.validate(Theme.HONEYMOON_ROMANCE, count);
    }
    @Test void honeymoonRejectsOddParticipantCount() {
        for (int count : new int[]{3, 5, 7, 9}) rejectsHoneymoon(count);
    }
    @Test void honeymoonRejectsOne() { rejectsHoneymoon(1); }
    @Test void honeymoonRejectsMoreThanTen() { rejectsHoneymoon(12); }
    @Test void coupleCountIsDerivedFromParticipantCount() {
        for (int count : new int[]{2, 4, 6, 8, 10}) {
            assertEquals(count / 2, ReservationPartyPolicy.honeymoonCoupleCount(count));
        }
        assertThrows(IllegalArgumentException.class, () -> ReservationPartyPolicy.honeymoonCoupleCount(3));
    }
    @Test void legacyReservationAlsoRejectsMoreThanTen() {
        assertThrows(IllegalArgumentException.class, () -> new Reservation(new Customer("A", "B", "C"), 11));
    }
    @Test void missingThemeCannotBypassPartyValidation() {
        assertThrows(NullPointerException.class, () -> ReservationPartyPolicy.validate(null, 2));
    }

    private void validateGeneral(int count) {
        for (var theme : new Theme[]{Theme.PARENTS_HEALING, Theme.GOLF_CHALLENGE, Theme.OUTDOOR_TREKKING}) {
            ReservationPartyPolicy.validate(theme, count);
        }
    }
    private void rejectsGeneral(int count) {
        for (var theme : new Theme[]{Theme.PARENTS_HEALING, Theme.GOLF_CHALLENGE, Theme.OUTDOOR_TREKKING}) {
            assertThrows(IllegalArgumentException.class, () -> ReservationPartyPolicy.validate(theme, count));
        }
    }
    private void rejectsHoneymoon(int count) {
        assertThrows(IllegalArgumentException.class, () -> ReservationPartyPolicy.validate(Theme.HONEYMOON_ROMANCE, count));
    }
}
