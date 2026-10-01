package com.wonhoone.misterworld.api.dto;

import com.wonhoone.misterworld.domain.Theme;
import com.wonhoone.misterworld.domain.TourStyle;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record TourProductWriteRequest(
        @NotNull Theme theme,
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 2000) String description,
        @NotNull List<@NotNull @Valid StylePrice> stylePrices) {
    public record StylePrice(@NotNull TourStyle style, @NotNull @Positive Long amount,
                             @NotBlank String currency) {}
}
