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
        reservations.add(reservation);
        totalParticipantCount = Math.addExact(totalParticipantCount, reservation.participantCount());

        if (!confirmed && totalParticipantCount >= confirmationThreshold()) {
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

    private int confirmationThreshold() {
        return tourProduct.theme() == Theme.HONEYMOON_ROMANCE ? 4 : 3;
    }
}
