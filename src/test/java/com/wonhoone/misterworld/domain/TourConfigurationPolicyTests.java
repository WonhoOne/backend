package com.wonhoone.misterworld.domain;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TourConfigurationPolicyTests {
    @Test void honeymoonRejectsClassicStyle() { rejectsClassic(Theme.HONEYMOON_ROMANCE); }
    @Test void parentsHealingRejectsClassicStyle() { rejectsClassic(Theme.PARENTS_HEALING); }
    @Test void golfAllowsClassicGrandPremium() { allowsEveryStyle(Theme.GOLF_CHALLENGE); }
    @Test void outdoorAllowsClassicGrandPremium() { allowsEveryStyle(Theme.OUTDOOR_TREKKING); }
    @Test void twoPersonLuxuryCarAcceptsTwoParticipants() {
        ReservationConfigurationPolicy.validate(Theme.HONEYMOON_ROMANCE, 2,
                configuration(TourStyle.GRAND, TransportOption.PRIVATE_LUXURY_CAR_2));
    }
    @Test void twoPersonLuxuryCarRejectsMoreThanTwoParticipants() {
        assertThrows(IllegalArgumentException.class, () -> ReservationConfigurationPolicy.validate(
                Theme.HONEYMOON_ROMANCE, 4, configuration(TourStyle.GRAND, TransportOption.PRIVATE_LUXURY_CAR_2)));
    }
    @Test void premiumVanAcceptsUpToTenParticipants() {
        for (int count = 1; count <= 10; count++) ReservationConfigurationPolicy.validate(
                Theme.GOLF_CHALLENGE, count, configuration(TourStyle.CLASSIC, TransportOption.PREMIUM_VAN_10));
    }
    @Test void premiumChampagneCanBeRemoved() {
        var configuration = configuration(TourStyle.PREMIUM, TransportOption.PREMIUM_VAN_10);
        ReservationConfigurationPolicy.validate(Theme.PARENTS_HEALING, 2, configuration);
        assertTrue(configuration.extraOptions().isEmpty());
    }
    @Test void hotelCanDifferFromStyleDefault() {
        ReservationConfigurationPolicy.validate(Theme.GOLF_CHALLENGE, 2, TourConfiguration.create(
                TourStyle.PREMIUM, HotelOption.HOTEL_3_STAR, TransportOption.PREMIUM_VAN_10,
                MealOption.PREMIUM_RESTAURANT, List.of()));
    }
    @Test void mealCanDifferFromStyleDefault() {
        ReservationConfigurationPolicy.validate(Theme.GOLF_CHALLENGE, 2, TourConfiguration.create(
                TourStyle.CLASSIC, HotelOption.HOTEL_3_STAR, TransportOption.PREMIUM_VAN_10,
                MealOption.PREMIUM_RESTAURANT, List.of()));
    }
    @Test void duplicateExtraOptionsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> withExtras(List.of(ExtraOption.COFFEE, ExtraOption.COFFEE)));
    }
    @Test void emptyExtraOptionsAreAllowed() { assertEquals(Set.of(), withExtras(List.of()).extraOptions()); }
    @Test void extraOptionsAreAnUnorderedUniqueSelection() {
        assertEquals(withExtras(List.of(ExtraOption.COFFEE, ExtraOption.CHAMPAGNE)),
                withExtras(List.of(ExtraOption.CHAMPAGNE, ExtraOption.COFFEE)));
    }
    @Test void mutableInputCannotChangeFinalConfiguration() {
        var extras = new ArrayList<>(List.of(ExtraOption.COFFEE));
        var configuration = withExtras(extras);
        extras.clear();
        assertEquals(Set.of(ExtraOption.COFFEE), configuration.extraOptions());
        assertThrows(UnsupportedOperationException.class, () -> configuration.extraOptions().add(ExtraOption.CHAMPAGNE));
        var mutableSet = EnumSet.of(ExtraOption.COFFEE);
        var direct = new TourConfiguration(TourStyle.GRAND, HotelOption.HOTEL_4_STAR,
                TransportOption.PREMIUM_VAN_10, MealOption.LOCAL_RESTAURANT, mutableSet);
        mutableSet.clear();
        assertEquals(Set.of(ExtraOption.COFFEE), direct.extraOptions());
    }
    @Test void nullOptionsAndNullExtraEntriesAreRejected() {
        assertThrows(NullPointerException.class, () -> withExtras(null));
        assertThrows(NullPointerException.class, () -> withExtras(Arrays.asList(ExtraOption.COFFEE, null)));
        assertThrows(NullPointerException.class, () -> configuration(null, TransportOption.PREMIUM_VAN_10));
        assertThrows(NullPointerException.class, () -> configuration(TourStyle.GRAND, null));
        assertThrows(NullPointerException.class, () -> TourConfiguration.create(TourStyle.GRAND, null,
                TransportOption.PREMIUM_VAN_10, MealOption.LUNCH_BOX, List.of()));
        assertThrows(NullPointerException.class, () -> TourConfiguration.create(TourStyle.GRAND, HotelOption.HOTEL_3_STAR,
                TransportOption.PREMIUM_VAN_10, null, List.of()));
    }

    private TourConfiguration configuration(TourStyle style, TransportOption transport) {
        return TourConfiguration.create(style, HotelOption.HOTEL_4_STAR, transport, MealOption.LOCAL_RESTAURANT, List.of());
    }
    private TourConfiguration withExtras(Collection<ExtraOption> extras) {
        return TourConfiguration.create(TourStyle.GRAND, HotelOption.HOTEL_4_STAR,
                TransportOption.PREMIUM_VAN_10, MealOption.LOCAL_RESTAURANT, extras);
    }
    private void rejectsClassic(Theme theme) {
        assertThrows(IllegalArgumentException.class, () -> ReservationConfigurationPolicy.validate(
                theme, 2, configuration(TourStyle.CLASSIC, TransportOption.PREMIUM_VAN_10)));
    }
    private void allowsEveryStyle(Theme theme) {
        for (var style : TourStyle.values()) ReservationConfigurationPolicy.validate(
                theme, 2, configuration(style, TransportOption.PREMIUM_VAN_10));
    }
}
