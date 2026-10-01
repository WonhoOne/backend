package com.wonhoone.misterworld.reservation;

import com.wonhoone.misterworld.api.dto.ReservationCreateRequest;
import com.wonhoone.misterworld.domain.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReservationValidationIntegrationTests extends ReservationIntegrationSupport {
    @Test void honeymoonOddParticipantCountReturnsValidationFailed() throws Exception {
        var honeymoon = schedule(Theme.HONEYMOON_ROMANCE, LocalDate.of(2026, 11, 1), false);
        rejects(request(honeymoon, 3), "participantCount", "NOT_ALLOWED");
    }
    @Test void honeymoonClassicStyleReturnsValidationFailed() throws Exception {
        var honeymoon = schedule(Theme.HONEYMOON_ROMANCE, LocalDate.of(2026, 11, 1), false);
        rejects(new ReservationCreateRequest(honeymoon.getId(), 2,
                configuration(TourStyle.CLASSIC, TransportOption.PREMIUM_VAN_10, List.of())),
                "configuration.style", "NOT_ALLOWED");
    }
    @Test void parentsClassicStyleReturnsValidationFailed() throws Exception {
        var parents = schedule(Theme.PARENTS_HEALING, LocalDate.of(2026, 11, 1), false);
        rejects(new ReservationCreateRequest(parents.getId(), 2,
                configuration(TourStyle.CLASSIC, TransportOption.PREMIUM_VAN_10, List.of())),
                "configuration.style", "NOT_ALLOWED");
    }
    @Test void transportCapacityExceededReturnsValidationFailed() throws Exception {
        rejects(new ReservationCreateRequest(schedule.getId(), 4,
                configuration(TourStyle.GRAND, TransportOption.PRIVATE_LUXURY_CAR_2, List.of())),
                "configuration.transportOption", "CAPACITY_EXCEEDED");
    }
    @Test void duplicateExtraOptionsReturnValidationFailed() throws Exception {
        rejects(new ReservationCreateRequest(schedule.getId(), 2,
                configuration(TourStyle.GRAND, TransportOption.PREMIUM_VAN_10, List.of(ExtraOption.COFFEE, ExtraOption.COFFEE))),
                "configuration.extraOptions", "DUPLICATE_VALUE");
    }
    @Test void allSemanticErrorsKeepTheirPublicFieldPaths() throws Exception {
        var honeymoon = schedule(Theme.HONEYMOON_ROMANCE, LocalDate.of(2026, 11, 1), false);
        var body = new ReservationCreateRequest(honeymoon.getId(), 3,
                configuration(TourStyle.CLASSIC, TransportOption.PRIVATE_LUXURY_CAR_2, List.of(ExtraOption.COFFEE, ExtraOption.COFFEE)));
        postReservation(body).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.fieldErrors.length()").value(4));
        assertNoMutation();
    }
    @Test void participantCountOutsideOneToTenReturnsOutOfRange() throws Exception {
        for (int count : List.of(0, -1, 11)) rejects(request(schedule, count), "participantCount", "OUT_OF_RANGE");
    }
    @Test void nonpositiveScheduleIdReturnsOutOfRange() throws Exception {
        rejects(new ReservationCreateRequest(0L, 2, request(schedule, 2).configuration()), "scheduleId", "OUT_OF_RANGE");
    }
    @Test void missingTopLevelFieldsReturnRequired() throws Exception {
        postReservation(Map.of()).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.fieldErrors.length()").value(3));
        rejects(new ReservationCreateRequest(null, 2, request(schedule, 2).configuration()), "scheduleId", "REQUIRED");
        rejects(new ReservationCreateRequest(schedule.getId(), null, request(schedule, 2).configuration()), "participantCount", "REQUIRED");
        rejects(new ReservationCreateRequest(schedule.getId(), 2, null), "configuration", "REQUIRED");
    }
    @Test void missingConfigurationFieldsReturnRequiredWithNestedPaths() throws Exception {
        var original = request(schedule, 2).configuration();
        for (var selected : List.of(
                new ReservationCreateRequest.Configuration(null, original.hotelOption(), original.transportOption(), original.mealOption(), original.extraOptions()),
                new ReservationCreateRequest.Configuration(original.style(), null, original.transportOption(), original.mealOption(), original.extraOptions()),
                new ReservationCreateRequest.Configuration(original.style(), original.hotelOption(), null, original.mealOption(), original.extraOptions()),
                new ReservationCreateRequest.Configuration(original.style(), original.hotelOption(), original.transportOption(), null, original.extraOptions()),
                new ReservationCreateRequest.Configuration(original.style(), original.hotelOption(), original.transportOption(), original.mealOption(), null))) {
            String field = selected.style() == null ? "style" : selected.hotelOption() == null ? "hotelOption"
                    : selected.transportOption() == null ? "transportOption" : selected.mealOption() == null ? "mealOption" : "extraOptions";
            rejects(new ReservationCreateRequest(schedule.getId(), 2, selected), "configuration." + field, "REQUIRED");
        }
    }
    @Test void nullExtraElementReturnsRequiredBeforeSemanticValidation() throws Exception {
        rejects(new ReservationCreateRequest(schedule.getId(), 2,
                configuration(TourStyle.GRAND, TransportOption.PREMIUM_VAN_10, Arrays.asList(ExtraOption.COFFEE, null))),
                "configuration.extraOptions[1]", "REQUIRED");
    }
    @Test void sameDayScheduleReturnsScheduleNotReservable() throws Exception { rejectsDate(LocalDate.of(2026, 10, 1)); }
    @Test void pastScheduleReturnsScheduleNotReservable() throws Exception { rejectsDate(LocalDate.of(2026, 9, 30)); }
    @Test void latestReservableIsRecheckedDespiteEarlierPublicResponse() throws Exception {
        http.perform(get("/api/v1/tour-schedules/{id}", schedule.getId())).andExpect(jsonPath("$.reservable").value(true));
        jdbc.update("UPDATE tour_schedule SET start_date = '2026-10-01' WHERE id = ?", schedule.getId());
        postReservation(request(schedule, 2)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHEDULE_NOT_RESERVABLE"));
        assertNoMutation();
    }
    @Test void missingScheduleReturnsTourScheduleNotFound() throws Exception {
        postReservation(new ReservationCreateRequest(Long.MAX_VALUE, 2, request(schedule, 2).configuration()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TOUR_SCHEDULE_NOT_FOUND"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
        assertNoMutation();
    }
    @Test void malformedEnumReturnsMalformedRequest() throws Exception {
        String valid = json.writeValueAsString(request(schedule, 2));
        for (String value : List.of("GRAND", "HOTEL_4_STAR", "PREMIUM_VAN_10", "LOCAL_RESTAURANT")) {
            malformed(valid.replace(value, "UNKNOWN"));
        }
    }
    @Test void fractionalAndStringPartyOrScheduleIdCannotBeCoerced() throws Exception {
        String valid = json.writeValueAsString(request(schedule, 2));
        malformed(valid.replace("\"participantCount\":2", "\"participantCount\":2.5"));
        malformed(valid.replace("\"participantCount\":2", "\"participantCount\":\"2\""));
        malformed(valid.replace("\"scheduleId\":" + schedule.getId(), "\"scheduleId\":1.5"));
    }
    @Test void clientCannotSupplyDerivedOwnerPriceOrConfirmationFields() throws Exception {
        String valid = json.writeValueAsString(request(schedule, 2));
        for (String field : List.of("customerId", "tourId", "theme", "coupleCount", "unitPrice", "subtotal",
                "discount", "total", "currency", "confirmed", "reservable")) {
            malformed(valid.substring(0, valid.length() - 1) + ",\"" + field + "\":1}");
        }
    }
    @Test void missingValidStylePriceReturnsInternalErrorWithoutCreatingReservation() throws Exception {
        jdbc.update("DELETE FROM tour_product_style_price WHERE tour_product_id = ? AND style = 'GRAND'", schedule.getTourProduct().getId());
        postReservation(request(schedule, 3)).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR")).andExpect(jsonPath("$.fieldErrors").isEmpty());
        assertNoMutation();
    }
    @Test void extremePriceOverflowReturnsInternalErrorWithoutCreatingReservation() throws Exception {
        jdbc.update("UPDATE tour_product_style_price SET amount = ? WHERE tour_product_id = ? AND style = 'GRAND'",
                Long.MAX_VALUE, schedule.getTourProduct().getId());
        String body = postReservation(request(schedule, 3)).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR")).andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("ArithmeticException", "overflow", Long.toString(Long.MAX_VALUE));
        assertNoMutation();
    }
    @Test void confirmationUpdateFailureRollsBackInsertedReservationAndExtraOptions() throws Exception {
        jdbc.execute("CREATE TRIGGER fail_confirmation BEFORE UPDATE ON tour_schedule FOR EACH ROW CALL 'com.wonhoone.misterworld.reservation.ReservationValidationIntegrationTests$FailConfirmation'");
        try {
            var body = new ReservationCreateRequest(schedule.getId(), 3,
                    configuration(TourStyle.GRAND, TransportOption.PREMIUM_VAN_10, List.of(ExtraOption.COFFEE)));
            postReservation(body).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
            assertNoMutation();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tour_reservation_extra_option", Long.class)).isZero();
        } finally { jdbc.execute("DROP TRIGGER fail_confirmation"); }
    }
    public static class FailConfirmation implements org.h2.api.Trigger {
        @Override public void fire(java.sql.Connection connection, Object[] oldRow, Object[] newRow) throws java.sql.SQLException {
            throw new java.sql.SQLException("Test-only confirmation update failure");
        }
    }
    private ResultActions rejects(Object body, String field, String code) throws Exception {
        var result = postReservation(body).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == '" + field + "')].code").value(org.hamcrest.Matchers.hasItem(code)));
        assertNoMutation();
        return result;
    }
    private void malformed(String body) throws Exception {
        http.perform(post("/api/v1/reservations").header("Authorization", bearer).contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        assertNoMutation();
    }
    private void rejectsDate(LocalDate start) throws Exception {
        var target = schedule(Theme.GOLF_CHALLENGE, start, false);
        postReservation(request(target, 3)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHEDULE_NOT_RESERVABLE")).andExpect(jsonPath("$.fieldErrors").isEmpty());
        assertNoMutation();
    }
    private void assertNoMutation() {
        assertThat(reservations.count()).isZero();
        assertThat(schedules.findAll()).allMatch(target -> !target.isConfirmed());
    }
}
