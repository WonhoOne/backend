package com.wonhoone.misterworld.domain;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public record TourConfiguration(TourStyle style, HotelOption hotelOption,
                                TransportOption transportOption, MealOption mealOption,
                                Set<ExtraOption> extraOptions) {
    public TourConfiguration {
        Objects.requireNonNull(style, "style must not be null");
        Objects.requireNonNull(hotelOption, "hotelOption must not be null");
        Objects.requireNonNull(transportOption, "transportOption must not be null");
        Objects.requireNonNull(mealOption, "mealOption must not be null");
        extraOptions = Set.copyOf(extraOptions);
    }

    // Accept the original request collection so duplicates cannot disappear during conversion to a set.
    public static TourConfiguration create(TourStyle style, HotelOption hotel,
                                            TransportOption transport, MealOption meal,
                                            Collection<ExtraOption> extras) {
        Objects.requireNonNull(extras, "extraOptions must not be null");
        var unique = EnumSet.noneOf(ExtraOption.class);
        for (var extra : extras) {
            if (!unique.add(Objects.requireNonNull(extra, "extraOption must not be null"))) {
                throw new IllegalArgumentException("Extra options must be unique");
            }
        }
        return new TourConfiguration(style, hotel, transport, meal, unique);
    }
}
