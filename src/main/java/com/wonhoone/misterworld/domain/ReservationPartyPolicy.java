package com.wonhoone.misterworld.domain;

import java.util.Objects;

public final class ReservationPartyPolicy {
    private ReservationPartyPolicy() {}

    public static void validateBaseRange(int participantCount) {
        if (participantCount < 1 || participantCount > 10) {
            throw new IllegalArgumentException("participantCount must be between 1 and 10");
        }
    }

    public static void validate(Theme theme, int participantCount) {
        Objects.requireNonNull(theme, "theme must not be null");
        validateBaseRange(participantCount);
        if (theme == Theme.HONEYMOON_ROMANCE && participantCount % 2 != 0) {
            throw new IllegalArgumentException("Honeymoon participantCount must be 2, 4, 6, 8 or 10");
        }
    }

    public static int honeymoonCoupleCount(int participantCount) {
        validate(Theme.HONEYMOON_ROMANCE, participantCount);
        return participantCount / 2;
    }
}
