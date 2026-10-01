package com.wonhoone.misterworld.domain;

public record ReservationPriceSnapshot(long unitPrice, long subtotal, DiscountSnapshot discount,
                                       long total, String currency) {
    public static final String CURRENCY = "KRW";

    public ReservationPriceSnapshot {
        if (unitPrice <= 0 || subtotal <= 0 || total < 0 || !CURRENCY.equals(currency)) {
            throw new IllegalArgumentException("Price requires positive whole KRW unitPrice/subtotal and nonnegative total");
        }
        long discountAmount = discount == null ? 0 : discount.amount();
        if (discount != null && discountAmount != ReservationPriceCalculator.loyaltyAmount(subtotal)) {
            throw new IllegalArgumentException("Loyalty amount must be five percent rounded down to whole KRW");
        }
        if (total != Math.subtractExact(subtotal, discountAmount)) {
            throw new IllegalArgumentException("Total must equal subtotal minus discount");
        }
    }
}
