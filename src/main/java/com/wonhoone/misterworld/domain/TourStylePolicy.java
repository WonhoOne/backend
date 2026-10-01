package com.wonhoone.misterworld.domain;

import java.util.Objects;
import java.util.List;

public final class TourStylePolicy {
    private TourStylePolicy() {
    }

    public static List<TourStyle> allowedStyles(Theme theme) {
        Objects.requireNonNull(theme, "theme must not be null");
        return switch (theme) {
            case HONEYMOON_ROMANCE, PARENTS_HEALING -> List.of(TourStyle.GRAND, TourStyle.PREMIUM);
            case GOLF_CHALLENGE, OUTDOOR_TREKKING -> List.of(TourStyle.CLASSIC, TourStyle.GRAND, TourStyle.PREMIUM);
        };
    }

    public static boolean isAllowed(Theme theme, TourStyle style) {
        Objects.requireNonNull(theme, "theme must not be null");
        Objects.requireNonNull(style, "style must not be null");
        return switch (theme) {
            case HONEYMOON_ROMANCE, PARENTS_HEALING -> style != TourStyle.CLASSIC;
            case GOLF_CHALLENGE, OUTDOOR_TREKKING -> true;
        };
    }

    public static void validate(Theme theme, TourStyle style) {
        if (!isAllowed(theme, style)) {
            throw new IllegalArgumentException("TourStyle is not allowed for the selected Theme");
        }
    }
}
