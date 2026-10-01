package com.wonhoone.misterworld.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReservationPriceCalculatorTests {
    @Test void priceUsesPerParticipantUnitPrice() {
        var price = ReservationPriceCalculator.calculate(1_000_000, 3, false);
        assertEquals(1_000_000, price.unitPrice());
        assertEquals(3_000_000, price.subtotal());
        assertEquals(3_000_000, price.total());
        assertEquals("KRW", price.currency());
    }
    @Test void honeymoonPricingStillUsesParticipantCount() {
        ReservationPartyPolicy.validate(Theme.HONEYMOON_ROMANCE, 4);
        assertEquals(4_000_000, ReservationPriceCalculator.calculate(1_000_000, 4, false).subtotal());
    }
    @Test void returningCustomerReceivesFivePercentLoyaltyDiscount() {
        var price = ReservationPriceCalculator.calculate(1_000_000, 2, true);
        assertEquals(new DiscountSnapshot(DiscountType.LOYALTY, 5, 100_000), price.discount());
        assertEquals(1_900_000, price.total());
    }
    @Test void newCustomerHasNullDiscount() {
        var price = ReservationPriceCalculator.calculate(101, 1, false);
        assertNull(price.discount());
        assertEquals(price.subtotal(), price.total());
    }
    @Test void loyaltyDiscountRoundsDownToWholeKrw() {
        var price = ReservationPriceCalculator.calculate(101, 1, true);
        assertEquals(5, price.discount().amount());
        assertEquals(96, price.total());
    }
    @Test void eligibleSmallPriceRetainsZeroAmountLoyaltySnapshot() {
        var price = ReservationPriceCalculator.calculate(1, 1, true);
        assertEquals(new DiscountSnapshot(DiscountType.LOYALTY, 5, 0), price.discount());
        assertEquals(1, price.total());
    }
    @Test void priceSnapshotTotalEqualsSubtotalMinusDiscount() {
        var price = ReservationPriceCalculator.calculate(999_999, 10, true);
        assertEquals(price.subtotal() - price.discount().amount(), price.total());
    }
    @Test void priceCalculationRejectsOverflowInsteadOfWrapping() {
        assertThrows(ArithmeticException.class, () -> ReservationPriceCalculator.calculate(Long.MAX_VALUE, 2, false));
    }
    @Test void loyaltyIntermediateDoesNotOverflowForLargestStoredSubtotal() {
        var price = ReservationPriceCalculator.calculate(Long.MAX_VALUE, 1, true);
        assertEquals(461_168_601_842_738_790L, price.discount().amount());
        assertEquals(8_762_203_435_012_037_017L, price.total());
    }
    @Test void nonpositivePriceAndInvalidPartyCannotBePriced() {
        for (long unit : new long[]{0, -1}) assertThrows(IllegalArgumentException.class,
                () -> ReservationPriceCalculator.calculate(unit, 1, false));
        for (int count : new int[]{0, 11}) assertThrows(IllegalArgumentException.class,
                () -> ReservationPriceCalculator.calculate(100, count, false));
    }
    @Test void inconsistentSnapshotsCannotBeConstructed() {
        assertThrows(IllegalArgumentException.class, () -> new ReservationPriceSnapshot(100, 100, null, 90, "KRW"));
        assertThrows(IllegalArgumentException.class, () -> new ReservationPriceSnapshot(100, 100, null, 100, "USD"));
        assertThrows(IllegalArgumentException.class, () -> new DiscountSnapshot(DiscountType.LOYALTY, 10, 10));
        assertThrows(IllegalArgumentException.class, () -> new DiscountSnapshot(DiscountType.LOYALTY, 5, -1));
        assertThrows(IllegalArgumentException.class, () -> new ReservationPriceSnapshot(101, 101,
                new DiscountSnapshot(DiscountType.LOYALTY, 5, 6), 95, "KRW"));
    }
}
