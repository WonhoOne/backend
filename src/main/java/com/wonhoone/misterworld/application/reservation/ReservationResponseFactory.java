package com.wonhoone.misterworld.application.reservation;

import com.wonhoone.misterworld.api.dto.ReservationResponse;
import com.wonhoone.misterworld.api.dto.TourScheduleResponse.Recruitment;
import com.wonhoone.misterworld.infrastructure.persistence.entity.ReservationJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class ReservationResponseFactory {
    public ReservationResponse create(ReservationJpaEntity reservation, Recruitment recruitment) {
        var selected = reservation.getConfiguration();
        var price = reservation.getPrice();
        var discount = price.discount() == null ? null : new ReservationResponse.Discount(
                price.discount().type(), price.discount().ratePercent(), price.discount().amount());
        return new ReservationResponse(reservation.getId(), reservation.getParticipantCount(),
                new ReservationResponse.TourProductSummary(reservation.getTourProductIdSnapshot(),
                        reservation.getTourProductThemeSnapshot(), reservation.getTourProductNameSnapshot()),
                new ReservationResponse.ScheduleSummary(reservation.getTourSchedule().getId(),
                        reservation.getScheduleStartDateSnapshot(), reservation.getScheduleEndDateSnapshot(), recruitment),
                new ReservationResponse.Configuration(selected.style(), selected.hotelOption(), selected.transportOption(),
                        selected.mealOption(), selected.extraOptions().stream().sorted().toList()),
                new ReservationResponse.Price(price.unitPrice(), price.subtotal(), discount, price.total(), price.currency()));
    }
}
