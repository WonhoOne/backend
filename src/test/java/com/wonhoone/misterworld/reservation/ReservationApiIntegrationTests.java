package com.wonhoone.misterworld.reservation;

import com.wonhoone.misterworld.api.dto.ReservationCreateRequest;
import com.wonhoone.misterworld.domain.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReservationApiIntegrationTests extends ReservationIntegrationSupport {
    @Test void customerCanCreateReservation() throws Exception {
        postReservation(request(schedule, 2)).andExpect(status().isCreated());
        assertThat(reservations.count()).isEqualTo(1);
    }
    @Test void postReturns201WithContractRepresentation() throws Exception {
        postReservation(request(schedule, 2)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(6)).andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.participantCount").value(2))
                .andExpect(jsonPath("$.tourProduct.length()").value(3))
                .andExpect(jsonPath("$.tourProduct.id").value(schedule.getTourProduct().getId()))
                .andExpect(jsonPath("$.tourProduct.theme").value("GOLF_CHALLENGE"))
                .andExpect(jsonPath("$.tourProduct.name").value("Original trip"))
                .andExpect(jsonPath("$.schedule.length()").value(4))
                .andExpect(jsonPath("$.schedule.startDate").value("2026-11-01"))
                .andExpect(jsonPath("$.schedule.endDate").value("2026-11-05"))
                .andExpect(jsonPath("$.schedule.recruitment.length()").value(4))
                .andExpect(jsonPath("$.configuration.length()").value(5))
                .andExpect(jsonPath("$.price.length()").value(5))
                .andExpect(jsonPath("$.price.unitPrice").value(201))
                .andExpect(jsonPath("$.price.subtotal").value(402))
                .andExpect(jsonPath("$.price.total").value(402))
                .andExpect(jsonPath("$.price.currency").value("KRW"));
    }
    @Test void reservationUsesAuthenticatedCustomerAsOwner() throws Exception {
        long id = postId(request(schedule, 2));
        assertThat(reservations.findById(id).orElseThrow().getCustomer().getId()).isEqualTo(customer.getId());
    }
    @Test void postResponseRecruitmentIncludesNewReservation() throws Exception {
        postReservation(request(schedule, 3)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.schedule.recruitment.currentCount").value(3))
                .andExpect(jsonPath("$.schedule.recruitment.confirmed").value(true));
    }
    @Test void honeymoonPostUsesCoupleTeamRecruitment() throws Exception {
        var honeymoon = schedule(Theme.HONEYMOON_ROMANCE, LocalDate.of(2026, 11, 1), false);
        postReservation(request(honeymoon, 2)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.schedule.recruitment.unit").value("COUPLE_TEAM"))
                .andExpect(jsonPath("$.schedule.recruitment.currentCount").value(1))
                .andExpect(jsonPath("$.schedule.recruitment.requiredCount").value(2))
                .andExpect(jsonPath("$.schedule.recruitment.confirmed").value(false))
                .andExpect(jsonPath("$.price.subtotal").value(402));
    }
    @Test void reservationCapturesCurrentSelectedStylePrice() throws Exception {
        jdbc.update("UPDATE tour_product_style_price SET amount = 999 WHERE tour_product_id = ? AND style = 'PREMIUM'",
                schedule.getTourProduct().getId());
        var body = new ReservationCreateRequest(schedule.getId(), 2,
                configuration(TourStyle.PREMIUM, TransportOption.PREMIUM_VAN_10, List.of()));
        postReservation(body).andExpect(status().isCreated()).andExpect(jsonPath("$.price.unitPrice").value(999))
                .andExpect(jsonPath("$.price.subtotal").value(1998));
    }
    @Test void newCustomerHasExplicitNullDiscount() throws Exception {
        var body = postReservation(request(schedule, 2)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(body).get("price").has("discount")).isTrue();
        assertThat(json.readTree(body).get("price").get("discount").isNull()).isTrue();
    }
    @Test void loyaltyCustomerReceivesFivePercentRoundedDownDiscount() throws Exception {
        historicalTrip(customer, LocalDate.of(2026, 9, 30), true);
        postReservation(request(schedule, 2)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.price.discount.length()").value(3))
                .andExpect(jsonPath("$.price.discount.type").value("LOYALTY"))
                .andExpect(jsonPath("$.price.discount.ratePercent").value(5))
                .andExpect(jsonPath("$.price.discount.amount").value(20))
                .andExpect(jsonPath("$.price.total").value(382));
    }
    @Test void sameDayTripDoesNotQualifyForLoyalty() throws Exception {
        historicalTrip(customer, LocalDate.of(2026, 10, 1), true);
        postReservation(request(schedule, 2)).andExpect(jsonPath("$.price.total").value(402));
    }
    @Test void unconfirmedPastTripDoesNotQualifyForLoyalty() throws Exception {
        historicalTrip(customer, LocalDate.of(2026, 9, 30), false);
        postReservation(request(schedule, 2)).andExpect(jsonPath("$.price.total").value(402));
    }
    @Test void anotherCustomersCompletedTripDoesNotQualifyOwnerForLoyalty() throws Exception {
        historicalTrip(account("other"), LocalDate.of(2026, 9, 30), true);
        postReservation(request(schedule, 2)).andExpect(jsonPath("$.price.total").value(402));
    }
    @Test void repeatedFutureReservationsCannotQualifyThemselvesForLoyalty() throws Exception {
        postReservation(request(schedule, 3)).andExpect(jsonPath("$.price.total").value(603));
        postReservation(request(schedule, 3)).andExpect(jsonPath("$.price.total").value(603));
    }
    @Test void confirmedFutureScheduleStillAcceptsReservation() throws Exception {
        var confirmed = schedule(Theme.GOLF_CHALLENGE, LocalDate.of(2026, 11, 1), true);
        postReservation(request(confirmed, 1)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.schedule.recruitment.confirmed").value(true));
    }
    @Test void customerCanReadOwnReservation() throws Exception {
        long id = postId(request(schedule, 2));
        detail(id).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
    }
    @Test void postAndDetailUseSameRepresentation() throws Exception {
        String body = postReservation(request(schedule, 2)).andReturn().getResponse().getContentAsString();
        detail(json.readTree(body).get("id").asLong()).andExpect(content().json(body));
    }
    @Test void otherCustomerGetsSameNotFoundAsMissingReservation() throws Exception {
        long id = postId(request(schedule, 2));
        String hidden = http.perform(get("/api/v1/reservations/{id}", id)
                .header("Authorization", token(account("other").getId(), UserRole.CUSTOMER)))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String missing = detail(Long.MAX_VALUE).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty()).andReturn().getResponse().getContentAsString();
        assertThat(hidden).isEqualTo(missing);
    }
    @Test void invalidReservationPathIdReturnsInvalidQueryParameter() throws Exception {
        for (String id : List.of("0", "-1", "abc", "9223372036854775808")) {
            http.perform(get("/api/v1/reservations/" + id).header("Authorization", bearer))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_QUERY_PARAMETER"));
        }
    }
    @Test void detailUsesHistoricalProductAndPriceSnapshotAfterEmployeeEdit() throws Exception {
        long id = postId(request(schedule, 2));
        var body = Map.of("theme", "GOLF_CHALLENGE", "name", "Changed trip", "description", "Changed description",
                "stylePrices", List.of(Map.of("style", "CLASSIC", "amount", 500, "currency", "KRW"),
                        Map.of("style", "GRAND", "amount", 600, "currency", "KRW"),
                        Map.of("style", "PREMIUM", "amount", 700, "currency", "KRW")));
        http.perform(put("/api/v1/employee/tours/{id}", schedule.getTourProduct().getId())
                .header("Authorization", token(1, UserRole.EMPLOYEE)).contentType("application/json")
                .content(json.writeValueAsString(body))).andExpect(status().isOk());
        detail(id).andExpect(jsonPath("$.tourProduct.name").value("Original trip"))
                .andExpect(jsonPath("$.tourProduct.theme").value("GOLF_CHALLENGE"))
                .andExpect(jsonPath("$.price.unitPrice").value(201)).andExpect(jsonPath("$.price.total").value(402));
    }
    @Test void detailUsesHistoricalSchedulePeriod() throws Exception {
        long id = postId(request(schedule, 2));
        jdbc.update("UPDATE tour_schedule SET start_date = '2026-12-01', end_date = '2026-12-05' WHERE id = ?", schedule.getId());
        detail(id).andExpect(jsonPath("$.schedule.startDate").value("2026-11-01"))
                .andExpect(jsonPath("$.schedule.endDate").value("2026-11-05"));
    }
    @Test void detailUsesCurrentRecruitmentAfterLaterReservations() throws Exception {
        long id = postId(request(schedule, 2));
        postId(request(schedule, 1));
        detail(id).andExpect(jsonPath("$.schedule.recruitment.currentCount").value(3))
                .andExpect(jsonPath("$.schedule.recruitment.confirmed").value(true));
    }
    @Test void detailDoesNotExposeCustomerProfileOrPersistenceFields() throws Exception {
        String body = detail(postId(request(schedule, 2))).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("customerId", "customer", "loginId", "password", "address", "contact",
                "Private", "Snapshot", "scheduleJustConfirmed", "coupleCount", "status", "reservable");
    }
    @Test void extraOptionsHaveStableCanonicalResponseOrder() throws Exception {
        var body = new ReservationCreateRequest(schedule.getId(), 2, configuration(TourStyle.GRAND,
                TransportOption.PREMIUM_VAN_10, List.of(ExtraOption.COFFEE, ExtraOption.CHAMPAGNE)));
        long id = postId(body);
        detail(id).andExpect(jsonPath("$.configuration.extraOptions").value(contains("CHAMPAGNE", "COFFEE")));
    }
    @Test void sameValidPostCanCreateTwoReservationsWithoutInventedDeduplication() throws Exception {
        long first = postId(request(schedule, 2));
        long second = postId(request(schedule, 2));
        assertThat(first).isNotEqualTo(second);
        assertThat(reservations.count()).isEqualTo(2);
    }
    @Test void anonymousCannotCreateReservation() throws Exception {
        postReservation(request(schedule, 2), null).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        assertThat(reservations.count()).isZero();
    }
    @Test void employeeCannotCreateCustomerReservation() throws Exception {
        postReservation(request(schedule, 2), token(1, UserRole.EMPLOYEE)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        assertThat(reservations.count()).isZero();
    }
    @Test void anonymousCannotReadReservation() throws Exception {
        http.perform(get("/api/v1/reservations/1")).andExpect(status().isUnauthorized());
    }
    @Test void employeeCannotReadCustomerReservation() throws Exception {
        http.perform(get("/api/v1/reservations/1").header("Authorization", token(1, UserRole.EMPLOYEE)))
                .andExpect(status().isForbidden());
    }
    @Test void missingAuthenticatedAccountFailsSafelyWithoutMutation() throws Exception {
        postReservation(request(schedule, 2), token(Long.MAX_VALUE, UserRole.CUSTOMER))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
        assertThat(reservations.count()).isZero();
    }
}
