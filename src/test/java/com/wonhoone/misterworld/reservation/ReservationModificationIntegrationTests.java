package com.wonhoone.misterworld.reservation;

import com.wonhoone.misterworld.api.dto.ReservationConfigurationUpdateRequest;
import com.wonhoone.misterworld.api.dto.ReservationCreateRequest;
import com.wonhoone.misterworld.domain.*;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReservationModificationIntegrationTests extends ReservationIntegrationSupport {
    private org.springframework.test.web.servlet.ResultActions modify(long id, Object body, String authorization) throws Exception {
        var request = put("/api/v1/reservations/{id}/configuration", id).contentType("application/json")
                .content(json.writeValueAsString(body));
        if (authorization != null) request.header("Authorization", authorization);
        return http.perform(request);
    }

    private ReservationConfigurationUpdateRequest update(TourStyle style, TransportOption transport, List<ExtraOption> extras) {
        return new ReservationConfigurationUpdateRequest(new ReservationCreateRequest.Configuration(style,
                HotelOption.HOTEL_5_STAR, transport, MealOption.PREMIUM_RESTAURANT, extras));
    }

    @Test void customerChangesStyleAndExtrasAndPriceIsRecalculatedWithoutTouchingRecruitment() throws Exception {
        long id = postId(request(schedule, 2));
        long reservationsBefore = reservations.count();

        modify(id, update(TourStyle.PREMIUM, TransportOption.PREMIUM_VAN_10,
                List.of(ExtraOption.CHAMPAGNE, ExtraOption.COFFEE)), bearer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.participantCount").value(2))
                .andExpect(jsonPath("$.configuration.style").value("PREMIUM"))
                .andExpect(jsonPath("$.configuration.hotelOption").value("HOTEL_5_STAR"))
                .andExpect(jsonPath("$.configuration.mealOption").value("PREMIUM_RESTAURANT"))
                .andExpect(jsonPath("$.configuration.extraOptions", contains("CHAMPAGNE", "COFFEE")))
                .andExpect(jsonPath("$.price.unitPrice").value(301))
                .andExpect(jsonPath("$.price.subtotal").value(602))
                .andExpect(jsonPath("$.price.total").value(602))
                .andExpect(jsonPath("$.schedule.recruitment.currentCount").value(2))
                .andExpect(jsonPath("$.schedule.recruitment.confirmed").value(false));

        detail(id).andExpect(status().isOk()).andExpect(jsonPath("$.configuration.style").value("PREMIUM"))
                .andExpect(jsonPath("$.configuration.extraOptions", contains("CHAMPAGNE", "COFFEE")));
        assertThat(reservations.count()).isEqualTo(reservationsBefore);
        assertThat(smsEvents.count()).isZero();

        // Extras can be removed again; the element collection is replaced, not duplicated.
        modify(id, update(TourStyle.GRAND, TransportOption.PREMIUM_VAN_10, List.of()), bearer)
                .andExpect(status().isOk()).andExpect(jsonPath("$.configuration.extraOptions.length()").value(0))
                .andExpect(jsonPath("$.price.unitPrice").value(201));
        assertThat(jdbc.queryForObject("select count(*) from tour_reservation_extra_option where reservation_id = ?",
                Long.class, id)).isZero();
    }

    @Test void anotherCustomerCannotModifyAndUnauthenticatedIsRejected() throws Exception {
        long id = postId(request(schedule, 2));
        var stranger = account("stranger");
        modify(id, update(TourStyle.PREMIUM, TransportOption.PREMIUM_VAN_10, List.of()),
                token(stranger.getId(), UserRole.CUSTOMER))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
        modify(id, update(TourStyle.PREMIUM, TransportOption.PREMIUM_VAN_10, List.of()), null)
                .andExpect(status().isUnauthorized());
        detail(id).andExpect(jsonPath("$.configuration.style").value("GRAND"));
    }

    @Test void sameValidationAsCreateIsApplied() throws Exception {
        long id = postId(request(schedule, 4));
        modify(id, update(TourStyle.GRAND, TransportOption.PRIVATE_LUXURY_CAR_2, List.of()), bearer)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("configuration.transportOption"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("CAPACITY_EXCEEDED"));

        var honeymoon = schedule(Theme.HONEYMOON_ROMANCE, LocalDate.of(2026, 12, 24), false);
        long honeymoonId = postId(request(honeymoon, 2));
        modify(honeymoonId, update(TourStyle.CLASSIC, TransportOption.PRIVATE_LUXURY_CAR_2, List.of()), bearer)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("configuration.style"));
        detail(id).andExpect(jsonPath("$.configuration.transportOption").value("PREMIUM_VAN_10"));
    }

    @Test void departedOrStartedTripsCannotBeModified() throws Exception {
        long id = postId(request(schedule, 3));
        jdbc.update("update tour_schedule set operation_status = 'DEPARTED' where id = ?", schedule.getId());
        modify(id, update(TourStyle.PREMIUM, TransportOption.PREMIUM_VAN_10, List.of()), bearer)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RESERVATION_NOT_MODIFIABLE"));

        var started = schedule(Theme.GOLF_CHALLENGE, LocalDate.of(2026, 11, 2), false);
        long startedId = postId(request(started, 1));
        jdbc.update("update tour_schedule set start_date = ? where id = ?", LocalDate.of(2026, 10, 1), started.getId());
        modify(startedId, update(TourStyle.PREMIUM, TransportOption.PREMIUM_VAN_10, List.of()), bearer)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RESERVATION_NOT_MODIFIABLE"));
    }
}
