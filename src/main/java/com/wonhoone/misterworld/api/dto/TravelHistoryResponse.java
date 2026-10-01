package com.wonhoone.misterworld.api.dto;

import com.wonhoone.misterworld.domain.Theme;
import com.wonhoone.misterworld.domain.TourStyle;
import java.time.LocalDate;

public record TravelHistoryResponse(long reservationId, TourProductSummary tourProduct,
                                    LocalDate startDate, LocalDate endDate, TourStyle style, Price price) {
    public record TourProductSummary(long id, Theme theme, String name) {}
    public record Price(long amount, String currency) {}
}
