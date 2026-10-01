package com.wonhoone.misterworld.domain;

import java.util.Objects;

public final class ReservationConfigurationPolicy {
    private ReservationConfigurationPolicy() {}

    public static void validate(Theme theme, int participantCount, TourConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration must not be null");
        ReservationPartyPolicy.validate(theme, participantCount);
        TourStylePolicy.validate(theme, configuration.style());
        if (configuration.transportOption().capacity() < participantCount) {
            throw new IllegalArgumentException("Transport capacity must cover all participants");
        }
        // Style defaults are editable selections, not constraints on hotel, meal or extras.
    }
}
