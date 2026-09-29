package com.wonhoone.misterworld.domain;

import java.util.Objects;

public record Reservation(Customer customer, int participantCount) {
    public Reservation {
        Objects.requireNonNull(customer, "customer must not be null");
        if (participantCount < 1) {
            throw new IllegalArgumentException("participantCount must be at least 1");
        }
    }
}
