package com.wonhoone.misterworld.api.dto;

import com.wonhoone.misterworld.domain.*;
import java.time.LocalDate;
import java.util.List;

public record ReservationResponse(long id, int participantCount, TourProductSummary tourProduct,
                                  ScheduleSummary schedule, Configuration configuration, Price price) {
    public record TourProductSummary(long id, Theme theme, String name) {}
    public record ScheduleSummary(long id, LocalDate startDate, LocalDate endDate,
                                  TourScheduleResponse.Recruitment recruitment) {}
    public record Configuration(TourStyle style, HotelOption hotelOption, TransportOption transportOption,
                                MealOption mealOption, List<ExtraOption> extraOptions) {}
    public record Price(long unitPrice, long subtotal, Discount discount, long total, String currency) {}
    public record Discount(DiscountType type, int ratePercent, long amount) {}
}
