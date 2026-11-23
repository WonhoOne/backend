package com.wonhoone.misterworld.domain;

import java.util.Objects;

/** Which included souvenir leaves stock when a schedule departs, and how many. */
public final class DepartureSouvenirPolicy {
    private DepartureSouvenirPolicy() {}

    public static InventoryItemType itemType(Theme theme) {
        Objects.requireNonNull(theme, "theme must not be null");
        return switch (theme) {
            case HONEYMOON_ROMANCE -> InventoryItemType.COUPLE_TSHIRT;
            case PARENTS_HEALING -> InventoryItemType.GINSENG_GIFT;
            case GOLF_CHALLENGE -> InventoryItemType.GOLF_BALL;
            case OUTDOOR_TREKKING -> InventoryItemType.SCARF;
        };
    }

    /** Honeymoon gives one couple T-shirt set per couple; every other Theme gives one item per participant. */
    public static long quantity(Theme theme, long participantTotal) {
        Objects.requireNonNull(theme, "theme must not be null");
        if (participantTotal < 0) throw new IllegalArgumentException("participantTotal must not be negative");
        return theme == Theme.HONEYMOON_ROMANCE ? participantTotal / 2 : participantTotal;
    }
}
