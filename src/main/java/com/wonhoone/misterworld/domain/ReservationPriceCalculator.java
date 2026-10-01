package com.wonhoone.misterworld.domain;

import java.math.BigInteger;

public final class ReservationPriceCalculator {
    public static final int LOYALTY_RATE_PERCENT = 5;

    private ReservationPriceCalculator() {}

    public static ReservationPriceSnapshot calculate(long unitPrice, int participantCount, boolean loyaltyEligible) {
        ReservationPartyPolicy.validateBaseRange(participantCount);
        if (unitPrice <= 0) throw new IllegalArgumentException("unitPrice must be positive whole KRW");
        long subtotal = Math.multiplyExact(unitPrice, participantCount);
        DiscountSnapshot discount = loyaltyEligible
                ? new DiscountSnapshot(DiscountType.LOYALTY, LOYALTY_RATE_PERCENT, loyaltyAmount(subtotal)) : null;
        long total = Math.subtractExact(subtotal, discount == null ? 0 : discount.amount());
        return new ReservationPriceSnapshot(unitPrice, subtotal, discount, total, ReservationPriceSnapshot.CURRENCY);
    }

    static long loyaltyAmount(long subtotal) {
        // Positive integer division explicitly floors fractional KRW; the intermediate cannot overflow BIGINT.
        return BigInteger.valueOf(subtotal).multiply(BigInteger.valueOf(LOYALTY_RATE_PERCENT))
                .divide(BigInteger.valueOf(100)).longValueExact();
    }
}
