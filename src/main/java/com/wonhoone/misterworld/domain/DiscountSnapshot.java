package com.wonhoone.misterworld.domain;

import java.util.Objects;

public record DiscountSnapshot(DiscountType type, int ratePercent, long amount) {
    public DiscountSnapshot {
        Objects.requireNonNull(type, "type must not be null");
        if (ratePercent != ReservationPriceCalculator.LOYALTY_RATE_PERCENT || amount < 0) {
            throw new IllegalArgumentException("Loyalty requires a 5 percent rate and nonnegative whole KRW amount");
        }
    }
}
