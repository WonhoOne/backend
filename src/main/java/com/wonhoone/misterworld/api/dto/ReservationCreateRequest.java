package com.wonhoone.misterworld.api.dto;

import com.wonhoone.misterworld.domain.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record ReservationCreateRequest(
        @NotNull @Positive Long scheduleId,
        @NotNull @Min(1) @Max(10) Integer participantCount,
        @NotNull @Valid Configuration configuration) {
    public record Configuration(
            @NotNull TourStyle style,
            @NotNull HotelOption hotelOption,
            @NotNull TransportOption transportOption,
            @NotNull MealOption mealOption,
            @NotNull List<@NotNull ExtraOption> extraOptions) {}
}
