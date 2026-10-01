package com.wonhoone.misterworld.domain;

import java.util.Objects;

public record Reservation(Customer customer, int participantCount) {
    public Reservation {
        Objects.requireNonNull(customer, "customer must not be null");
        ReservationPartyPolicy.validateBaseRange(participantCount);
    }
}
