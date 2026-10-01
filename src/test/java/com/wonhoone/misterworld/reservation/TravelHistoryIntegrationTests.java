package com.wonhoone.misterworld.reservation;

import com.wonhoone.misterworld.application.reservation.LoyaltyEligibilityService;
import com.wonhoone.misterworld.domain.*;
import com.wonhoone.misterworld.infrastructure.persistence.entity.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Persisted trips represent reservations made in the past, not new POSTs to expired schedules. */
class TravelHistoryIntegrationTests extends ReservationIntegrationSupport {
    private static final String HISTORY = "/api/v1/customers/me/travel-history";
    private static final LocalDate YESTERDAY = LocalDate.of(2026, 9, 30);
    @Autowired LoyaltyEligibilityService loyalty;

    @Test void customerWithNoCompletedTripsGetsEmptyHistory() throws Exception {
        history().andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test void customerCanReadOwnTravelHistory() throws Exception {
        var trip = historicalTrip(customer, YESTERDAY, true);
        history().andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].reservationId").value(trip.getId()));
    }

    @Test void confirmedTripEndingYesterdayAppearsInHistory() throws Exception {
        historicalTrip(customer, YESTERDAY, true);
        history().andExpect(jsonPath("$[0].endDate").value("2026-09-30"));
    }

    @Test void confirmedTripEndingOnBusinessDateDoesNotAppear() throws Exception {
        historicalTrip(customer, YESTERDAY.plusDays(1), true);
        history().andExpect(content().json("[]"));
    }

    @Test void confirmedFutureTripDoesNotAppear() throws Exception {
        historicalTrip(customer, YESTERDAY.plusDays(2), true);
        history().andExpect(content().json("[]"));
    }

    @Test void unconfirmedPastTripDoesNotAppear() throws Exception {
        historicalTrip(customer, YESTERDAY, false);
        history().andExpect(content().json("[]"));
    }

    @Test void laterConfirmationMakesHistoricalTripVisible() throws Exception {
        var trip = historicalTrip(customer, YESTERDAY, false);
        history().andExpect(content().json("[]"));
        jdbc.update("UPDATE tour_schedule SET confirmed = TRUE WHERE id = ?", trip.getTourSchedule().getId());
        history().andExpect(jsonPath("$[0].reservationId").value(trip.getId()));
    }

    @Test void otherCustomersCompletedTripDoesNotAppear() throws Exception {
        historicalTrip(account("other"), YESTERDAY, true);
        history().andExpect(content().json("[]"));
        var own = historicalTrip(customer, YESTERDAY, true);
        history().andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].reservationId").value(own.getId()));
    }

    @Test void historyIsOrderedByEndDateDescendingThenReservationIdDescending() throws Exception {
        var firstRecent = historicalTrip(customer, YESTERDAY, true);
        var older = historicalTrip(customer, YESTERDAY.minusDays(10), true);
        var secondRecent = historicalTrip(customer, YESTERDAY, true);
        history().andExpect(jsonPath("$[*].reservationId").value(contains(
                secondRecent.getId().intValue(), firstRecent.getId().intValue(), older.getId().intValue())));
    }

    @Test void historyUsesExactContractRepresentation() throws Exception {
        var trip = historicalTrip(customer, YESTERDAY, true);
        history().andExpect(status().isOk()).andExpect(content().json("""
                [{"reservationId": %d,
                  "tourProduct": {"id": %d, "theme": "GOLF_CHALLENGE", "name": "Original trip"},
                  "startDate": "2026-09-26", "endDate": "2026-09-30", "style": "GRAND",
                  "price": {"amount": 402, "currency": "KRW"}}]
                """.formatted(trip.getId(), trip.getTourProductIdSnapshot())))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].length()").value(6))
                .andExpect(jsonPath("$[0].tourProduct.length()").value(3))
                .andExpect(jsonPath("$[0].price.length()").value(2));
    }

    @Test void historyKeepsHistoricalProductIdentityAndNameAfterProductEdit() throws Exception {
        var trip = historicalTrip(customer, YESTERDAY, true);
        editProduct(trip.getTourProductIdSnapshot());
        history().andExpect(jsonPath("$[0].tourProduct.id").value(trip.getTourProductIdSnapshot()))
                .andExpect(jsonPath("$[0].tourProduct.theme").value("GOLF_CHALLENGE"))
                .andExpect(jsonPath("$[0].tourProduct.name").value("Original trip"));
    }

    @Test void historyKeepsHistoricalPriceAfterProductPriceEdit() throws Exception {
        var trip = historicalTrip(customer, YESTERDAY, true);
        editProduct(trip.getTourProductIdSnapshot());
        history().andExpect(jsonPath("$[0].price.amount").value(402))
                .andExpect(jsonPath("$[0].price.currency").value("KRW"));
    }

    @Test void historyKeepsHistoricalSchedulePeriodAndEligibility() throws Exception {
        var trip = historicalTrip(customer, YESTERDAY, true);
        jdbc.update("UPDATE tour_schedule SET start_date = '2026-11-01', end_date = '2026-11-05' WHERE id = ?",
                trip.getTourSchedule().getId());
        history().andExpect(jsonPath("$[0].startDate").value("2026-09-26"))
                .andExpect(jsonPath("$[0].endDate").value("2026-09-30"));
    }

    @Test void currentPastScheduleDatesCannotCompleteSameDaySnapshot() throws Exception {
        var trip = historicalTrip(customer, YESTERDAY.plusDays(1), true);
        jdbc.update("UPDATE tour_schedule SET start_date = '2026-09-26', end_date = '2026-09-30' WHERE id = ?",
                trip.getTourSchedule().getId());
        history().andExpect(content().json("[]"));
    }

    @Test void historyStyleComesFromReservationConfigurationSnapshot() throws Exception {
        var trip = selectedHistoricalTrip(TourStyle.PREMIUM, false);
        editProduct(trip.getTourProductIdSnapshot());
        history().andExpect(jsonPath("$[0].style").value("PREMIUM"));
    }

    @Test void historyPriceUsesTotalRatherThanUnitPriceOrSubtotal() throws Exception {
        selectedHistoricalTrip(TourStyle.GRAND, true);
        history().andExpect(jsonPath("$[0].price.amount").value(1900))
                .andExpect(jsonPath("$[0].price.currency").value("KRW"));
    }

    @Test void anonymousCannotReadTravelHistory() throws Exception {
        http.perform(get(HISTORY)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test void employeeCannotReadTravelHistory() throws Exception {
        http.perform(get(HISTORY).header("Authorization", token(customer.getId(), UserRole.EMPLOYEE)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test void historyDoesNotExposePrivateOrUnsupportedFields() throws Exception {
        selectedHistoricalTrip(TourStyle.PREMIUM, true);
        String body = history().andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("customerId", "customer", "loginId", "password", "address", "contact",
                "Private", "configuration", "hotelOption", "transportOption", "mealOption", "extraOptions",
                "participantCount", "scheduleId", "unitPrice", "subtotal", "discount", "image", "confirmed",
                "reservable", "status", "coupleCount");
    }

    @Test void historyAndLoyaltyShareCompletedTripBoundary() throws Exception {
        historicalTrip(customer, YESTERDAY.plusDays(1), true);
        historicalTrip(customer, YESTERDAY, false);
        assertThat(loyalty.isEligible(customer.getId())).isFalse();
        history().andExpect(content().json("[]"));
        var completed = historicalTrip(customer, YESTERDAY, true);
        assertThat(loyalty.isEligible(customer.getId())).isTrue();
        history().andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].reservationId").value(completed.getId()));
    }

    private ResultActions history() throws Exception {
        return http.perform(get(HISTORY).header("Authorization", bearer));
    }

    private ReservationJpaEntity selectedHistoricalTrip(TourStyle style, boolean discounted) {
        var past = schedule(Theme.GOLF_CHALLENGE, YESTERDAY.minusDays(4), true);
        var selected = TourConfiguration.create(style, HotelOption.HOTEL_5_STAR,
                TransportOption.PREMIUM_VAN_10, MealOption.PREMIUM_RESTAURANT,
                List.of(ExtraOption.CHAMPAGNE, ExtraOption.COFFEE));
        return reservations.saveAndFlush(ReservationJpaEntity.capture(customer, past, 2, selected,
                ReservationPriceCalculator.calculate(1000, 2, discounted)));
    }

    private void editProduct(long productId) throws Exception {
        var body = Map.of("theme", "GOLF_CHALLENGE", "name", "Changed trip", "description", "Changed description",
                "stylePrices", List.of(Map.of("style", "CLASSIC", "amount", 500, "currency", "KRW"),
                        Map.of("style", "GRAND", "amount", 600, "currency", "KRW"),
                        Map.of("style", "PREMIUM", "amount", 700, "currency", "KRW")));
        http.perform(put("/api/v1/employee/tours/{id}", productId)
                .header("Authorization", token(customer.getId(), UserRole.EMPLOYEE))
                .contentType("application/json").content(json.writeValueAsString(body)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Changed trip"))
                .andExpect(jsonPath("$.stylePrices[1].amount").value(600));
    }
}
