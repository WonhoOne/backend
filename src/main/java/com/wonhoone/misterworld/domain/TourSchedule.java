package com.wonhoone.misterworld.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class TourSchedule {
    private final TourProduct tourProduct;
    private final List<Reservation> reservations = new ArrayList<>();
    private long totalParticipantCount;
    private boolean confirmed;

    public TourSchedule(TourProduct tourProduct) {
        this.tourProduct = Objects.requireNonNull(tourProduct, "tourProduct must not be null");
    }

    /** Adds a reservation and returns true only when this call first confirms the schedule. */
    public boolean addReservation(Reservation reservation) {
        Objects.requireNonNull(reservation, "reservation must not be null");

        validateReservationForSchedule(reservation);

        long nextTotalParticipantCount = Math.addExact(totalParticipantCount, reservation.participantCount());
        long nextHoneymoonCoupleCount = isHoneymoonSchedule()
                ? Math.addExact(totalHoneymoonCoupleCount(), reservation.participantCount() / 2L)
                : 0;

        reservations.add(reservation);
        totalParticipantCount = nextTotalParticipantCount;

        if (!confirmed && confirmationThresholdReached(nextTotalParticipantCount, nextHoneymoonCoupleCount)) {
            confirmed = true;
            return true;
        }
        return false;
    }

    public TourProduct tourProduct() {
        return tourProduct;
    }

    public List<Reservation> reservations() {
        return List.copyOf(reservations);
    }

    public long totalParticipantCount() {
        return totalParticipantCount;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    private void validateReservationForSchedule(Reservation reservation) {
        ReservationPartyPolicy.validate(tourProduct.theme(), reservation.participantCount());
    }

    private boolean confirmationThresholdReached(long participantCount, long honeymoonCoupleCount) {
        return isHoneymoonSchedule() ? honeymoonCoupleCount >= 2 : participantCount >= 3;
    }

    private long totalHoneymoonCoupleCount() {
        return reservations.stream()
                .mapToLong(reservation -> reservation.participantCount() / 2L)
                .reduce(0L, Math::addExact);
    }

    private boolean isHoneymoonSchedule() {
        return tourProduct.theme() == Theme.HONEYMOON_ROMANCE;
    }
}
