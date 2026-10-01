package com.wonhoone.misterworld.tour;

import com.wonhoone.misterworld.api.dto.TourProductWriteRequest;
import com.wonhoone.misterworld.domain.*;
import com.wonhoone.misterworld.infrastructure.persistence.entity.*;
import com.wonhoone.misterworld.infrastructure.persistence.repository.*;
import com.wonhoone.misterworld.security.JwtTokenService;
import jakarta.servlet.Filter;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:tour-api;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@ActiveProfiles("test")
class TourApiIntegrationTests {
    @TestBean(name = "businessClock", methodName = "fixedBusinessClock") Clock clock;
    static Clock fixedBusinessClock() {
        return Clock.fixed(Instant.parse("2026-09-30T15:00:00Z"), ZoneId.of("Asia/Seoul"));
    }
    @Autowired WebApplicationContext context;
    @Autowired @Qualifier("springSecurityFilterChain") Filter securityFilter;
    @Autowired TourProductJpaRepository products;
    @Autowired TourProductStylePriceJpaRepository prices;
    @Autowired TourScheduleJpaRepository schedules;
    @Autowired JwtTokenService tokens;
    @Autowired ObjectMapper json;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    MockMvc http;
    String employee;
    String customer;

    @BeforeEach void prepare() {
        schedules.deleteAll();
        prices.deleteAll();
        products.deleteAll();
        http = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilter).build();
        employee = "Bearer " + tokens.issue(1, UserRole.EMPLOYEE).value();
        customer = "Bearer " + tokens.issue(2, UserRole.CUSTOMER).value();
    }

    @Test void emptyPublicCollectionsReturnPlainArraysWithoutToken() throws Exception {
        http.perform(get("/api/v1/tours")).andExpect(status().isOk()).andExpect(content().json("[]"));
        http.perform(get("/api/v1/tour-schedules")).andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test void publicTourListIsOrderedById() throws Exception {
        var first = product(Theme.GOLF_CHALLENGE, "Z trip");
        var second = product(Theme.PARENTS_HEALING, "A trip");
        http.perform(get("/api/v1/tours")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(first.getId()))
                .andExpect(jsonPath("$[1].id").value(second.getId()));
    }

    @Test void publicTourDetailReturnsContractRepresentation() throws Exception {
        var tour = product(Theme.GOLF_CHALLENGE, "Golf trip");
        http.perform(get("/api/v1/tours/{id}", tour.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.theme").value("GOLF_CHALLENGE"))
                .andExpect(jsonPath("$.name").value("Golf trip"))
                .andExpect(jsonPath("$.description").value("Travel description"))
                .andExpect(jsonPath("$.availableStyles").value(contains("CLASSIC", "GRAND", "PREMIUM")))
                .andExpect(jsonPath("$.stylePrices[0].style").value("CLASSIC"))
                .andExpect(jsonPath("$.stylePrices[0].amount").value(3_000_000_000L))
                .andExpect(jsonPath("$.stylePrices[*].currency").value(contains("KRW", "KRW", "KRW")))
                .andExpect(jsonPath("$.length()").value(6));
    }

    @Test void missingTourReturnsTourProductNotFound() throws Exception {
        http.perform(get("/api/v1/tours/{id}", Long.MAX_VALUE)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TOUR_PRODUCT_NOT_FOUND"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test void invalidTourIdReturnsInvalidQueryParameter() throws Exception {
        for (String id : List.of("0", "-1", "abc", "9223372036854775808")) {
            http.perform(get("/api/v1/tours/" + id)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_QUERY_PARAMETER"))
                    .andExpect(jsonPath("$.fieldErrors").isEmpty());
        }
    }

    @Test void employeeCanCreateTourProductWithAllAllowedStylePrices() throws Exception {
        for (Theme theme : Theme.values()) {
            write(request(theme), employee).andExpect(status().isCreated())
                    .andExpect(jsonPath("$.theme").value(theme.name()))
                    .andExpect(jsonPath("$.stylePrices.length()").value(TourStylePolicy.allowedStyles(theme).size()));
        }
        assertThat(products.count()).isEqualTo(4);
        assertThat(prices.count()).isEqualTo(10);
    }

    @Test void createdTourProductDerivesAvailableStylesFromTheme() throws Exception {
        write(request(Theme.HONEYMOON_ROMANCE), employee).andExpect(status().isCreated())
                .andExpect(jsonPath("$.availableStyles").value(contains("GRAND", "PREMIUM")))
                .andExpect(jsonPath("$.stylePrices[*].style").value(contains("GRAND", "PREMIUM")));
    }

    @Test void employeeWriteRequiresToken() throws Exception {
        http.perform(post("/api/v1/employee/tours").contentType("application/json")
                .content(json.writeValueAsString(request(Theme.GOLF_CHALLENGE))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test void customerCannotCreateEmployeeTourProduct() throws Exception {
        write(request(Theme.GOLF_CHALLENGE), customer).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        assertThat(products.count()).isZero();
    }

    @Test void employeeListAndUpdateRequireEmployeeRole() throws Exception {
        http.perform(get("/api/v1/employee/tours")).andExpect(status().isUnauthorized());
        http.perform(get("/api/v1/employee/tours").header("Authorization", customer)).andExpect(status().isForbidden());
        http.perform(put("/api/v1/employee/tours/1").contentType("application/json")
                .content(json.writeValueAsString(request(Theme.GOLF_CHALLENGE)))).andExpect(status().isUnauthorized());
        update(1, request(Theme.GOLF_CHALLENGE), customer).andExpect(status().isForbidden());
    }

    @Test void honeymoonProductRejectsClassicStyle() throws Exception {
        var body = request(Theme.GOLF_CHALLENGE);
        rejects(new TourProductWriteRequest(Theme.HONEYMOON_ROMANCE, body.name(), body.description(), body.stylePrices()),
                "stylePrices[2].style", "NOT_ALLOWED");
    }

    @Test void honeymoonProductRequiresGrandAndPremiumPrices() throws Exception {
        rejects(withPrices(Theme.HONEYMOON_ROMANCE, List.of(price(TourStyle.GRAND, 100L, "KRW"))),
                "stylePrices", "REQUIRED");
    }

    @Test void golfProductRequiresAllThreePrices() throws Exception {
        rejects(withPrices(Theme.GOLF_CHALLENGE, request(Theme.HONEYMOON_ROMANCE).stylePrices()), "stylePrices", "REQUIRED");
    }

    @Test void duplicateStylePriceIsRejected() throws Exception {
        rejects(withPrices(Theme.HONEYMOON_ROMANCE, List.of(price(TourStyle.GRAND, 100L, "KRW"),
                price(TourStyle.GRAND, 200L, "KRW"), price(TourStyle.PREMIUM, 300L, "KRW"))),
                "stylePrices[1].style", "DUPLICATE_VALUE");
    }

    @Test void nonKrwCurrencyIsRejected() throws Exception {
        rejects(withPrices(Theme.HONEYMOON_ROMANCE, List.of(price(TourStyle.GRAND, 100L, "USD"),
                price(TourStyle.PREMIUM, 200L, "KRW"))), "stylePrices[0].currency", "NOT_ALLOWED");
    }

    @Test void nonpositivePriceIsRejected() throws Exception {
        for (long amount : List.of(0L, -1L)) {
            rejects(withPrices(Theme.HONEYMOON_ROMANCE, List.of(price(TourStyle.GRAND, amount, "KRW"),
                    price(TourStyle.PREMIUM, 200L, "KRW"))), "stylePrices[0].amount", "OUT_OF_RANGE");
        }
    }

    @Test void nullNestedFieldsAreRequiredAndKeepTheirFieldPaths() throws Exception {
        for (var entry : List.of(price(null, 100L, "KRW"), price(TourStyle.GRAND, null, "KRW"),
                price(TourStyle.GRAND, 100L, null))) {
            String field = entry.style() == null ? "style" : entry.amount() == null ? "amount" : "currency";
            rejects(withPrices(Theme.HONEYMOON_ROMANCE, List.of(entry, price(TourStyle.PREMIUM, 200L, "KRW"))),
                    "stylePrices[0]." + field, "REQUIRED");
        }
    }

    @Test void nullPriceEntryIsRejectedBeforeSemanticValidation() throws Exception {
        rejects(withPrices(Theme.HONEYMOON_ROMANCE, Arrays.asList(null, price(TourStyle.PREMIUM, 100L, "KRW"))),
                "stylePrices[0]", "REQUIRED");
    }

    @Test void requiredProductFieldsCannotBeMissingOrBlank() throws Exception {
        write(Map.of(), employee).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.fieldErrors.length()").value(4))
                .andExpect(jsonPath("$.fieldErrors[*].code").value(contains("REQUIRED", "REQUIRED", "REQUIRED", "REQUIRED")));
        rejects(new TourProductWriteRequest(Theme.GOLF_CHALLENGE, " ", " ", request(Theme.GOLF_CHALLENGE).stylePrices()),
                "name", "REQUIRED");
    }

    @Test void emptyPriceSetReportsMissingRequiredStyles() throws Exception {
        rejects(withPrices(Theme.GOLF_CHALLENGE, List.of()), "stylePrices", "REQUIRED")
                .andExpect(jsonPath("$.fieldErrors.length()").value(3));
    }

    @Test void productTextCannotExceedExistingStorageBounds() throws Exception {
        var body = request(Theme.GOLF_CHALLENGE);
        rejects(new TourProductWriteRequest(body.theme(), "x".repeat(256), body.description(), body.stylePrices()),
                "name", "OUT_OF_RANGE");
        rejects(new TourProductWriteRequest(body.theme(), body.name(), "x".repeat(2001), body.stylePrices()),
                "description", "OUT_OF_RANGE");
    }

    @Test void employeeCanUpdateThemeNameDescriptionAndCompleteStylePrices() throws Exception {
        var tour = product(Theme.GOLF_CHALLENGE, "Original");
        update(tour.getId(), request(Theme.HONEYMOON_ROMANCE), employee).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(tour.getId()))
                .andExpect(jsonPath("$.theme").value("HONEYMOON_ROMANCE"))
                .andExpect(jsonPath("$.name").value("Updated trip"))
                .andExpect(jsonPath("$.description").value("Updated description"))
                .andExpect(jsonPath("$.stylePrices[*].style").value(contains("GRAND", "PREMIUM")));
        // A second complete replacement exercises delete/insert ordering for identical unique keys.
        update(tour.getId(), request(Theme.HONEYMOON_ROMANCE), employee).andExpect(status().isOk());
        assertThat(prices.findByTourProductId(tour.getId())).hasSize(2);
        http.perform(get("/api/v1/tours/{id}", tour.getId())).andExpect(jsonPath("$.stylePrices[0].amount").value(100));
    }

    @Test void invalidUpdatePreservesOriginalProductAndPrices() throws Exception {
        var tour = product(Theme.GOLF_CHALLENGE, "Original");
        var before = http.perform(get("/api/v1/tours/{id}", tour.getId())).andReturn().getResponse().getContentAsString();
        update(tour.getId(), withPrices(Theme.HONEYMOON_ROMANCE, List.of()), employee)
                .andExpect(status().isUnprocessableContent());
        http.perform(get("/api/v1/tours/{id}", tour.getId())).andExpect(content().json(before));
    }

    @Test void priceInsertFailureRollsBackCreatedProductAndEarlierPriceRows() throws Exception {
        failPremiumInsert();
        try {
            write(withPrices(Theme.HONEYMOON_ROMANCE, List.of(price(TourStyle.GRAND, 100L, "KRW"),
                    price(TourStyle.PREMIUM, 200L, "KRW"))), employee).andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
            assertThat(products.count()).isZero();
            assertThat(prices.count()).isZero();
        } finally { jdbc.execute("DROP TRIGGER fail_premium_insert"); }
    }

    @Test void priceInsertFailureRestoresOriginalDetailsAndDeletedPriceSet() throws Exception {
        var tour = product(Theme.GOLF_CHALLENGE, "Original");
        var before = http.perform(get("/api/v1/tours/{id}", tour.getId())).andReturn().getResponse().getContentAsString();
        failPremiumInsert();
        try {
            update(tour.getId(), withPrices(Theme.HONEYMOON_ROMANCE, List.of(price(TourStyle.GRAND, 100L, "KRW"),
                    price(TourStyle.PREMIUM, 200L, "KRW"))), employee).andExpect(status().isInternalServerError());
            http.perform(get("/api/v1/tours/{id}", tour.getId())).andExpect(status().isOk()).andExpect(content().json(before));
        } finally { jdbc.execute("DROP TRIGGER fail_premium_insert"); }
    }

    private void failPremiumInsert() {
        jdbc.execute("CREATE TRIGGER fail_premium_insert BEFORE INSERT ON tour_product_style_price "
                + "FOR EACH ROW CALL 'com.wonhoone.misterworld.tour.TourApiIntegrationTests$FailPremiumInsert'");
    }

    public static class FailPremiumInsert implements org.h2.api.Trigger {
        @Override public void fire(java.sql.Connection connection, Object[] oldRow, Object[] newRow)
                throws java.sql.SQLException {
            if (Arrays.asList(newRow).contains("PREMIUM")) {
                throw new java.sql.SQLException("Test-only failure after an earlier price insert.");
            }
        }
    }

    @Test void updateMissingTourReturnsTourProductNotFound() throws Exception {
        update(Long.MAX_VALUE, request(Theme.GOLF_CHALLENGE), employee).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TOUR_PRODUCT_NOT_FOUND"));
    }

    @Test void invalidUpdateIdReturnsInvalidQueryParameter() throws Exception {
        for (String id : List.of("0", "-1", "bad")) {
            http.perform(put("/api/v1/employee/tours/" + id).header("Authorization", employee)
                    .contentType("application/json").content(json.writeValueAsString(request(Theme.GOLF_CHALLENGE))))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_QUERY_PARAMETER"));
        }
    }

    @Test void employeeTourListUsesSameRepresentationAndIdOrdering() throws Exception {
        product(Theme.HONEYMOON_ROMANCE, "First");
        product(Theme.OUTDOOR_TREKKING, "Second");
        String publicBody = http.perform(get("/api/v1/tours")).andReturn().getResponse().getContentAsString();
        http.perform(get("/api/v1/employee/tours").header("Authorization", employee)).andExpect(status().isOk())
                .andExpect(content().json(publicBody));
    }

    @Test void unknownWritePropertyIsRejectedAsMalformedRequest() throws Exception {
        var body = new HashMap<String, Object>(Map.of("theme", "GOLF_CHALLENGE", "name", "Tour",
                "description", "Description", "stylePrices", request(Theme.GOLF_CHALLENGE).stylePrices()));
        for (String field : List.of("availableStyles", "id")) {
            body.put(field, 1);
            write(body, employee).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
            body.remove(field);
        }
    }

    @Test void malformedEnumAndNonintegerPriceCannotBeCoercedIntoValidInput() throws Exception {
        String valid = json.writeValueAsString(request(Theme.GOLF_CHALLENGE));
        for (String body : List.of(valid.replace("GOLF_CHALLENGE", "UNKNOWN"), valid.replace("GRAND", "UNKNOWN"),
                valid.replace("100", "100.5"), valid.replace("100", "\"100\""), valid.replace("100", "9223372036854775808"))) {
            http.perform(post("/api/v1/employee/tours").header("Authorization", employee)
                    .contentType("application/json").content(body)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        }
    }

    @Test void scheduleListIsOrderedByStartDateThenId() throws Exception {
        var tour = product(Theme.GOLF_CHALLENGE, "Trip");
        var late = schedule(tour, "2026-11-10", false);
        var first = schedule(tour, "2026-10-02", false);
        var tie = schedule(tour, "2026-10-02", false);
        http.perform(get("/api/v1/tour-schedules")).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(contains(first.getId().intValue(), tie.getId().intValue(), late.getId().intValue())));
    }

    @Test void scheduleListCanFilterByTourId() throws Exception {
        var target = product(Theme.GOLF_CHALLENGE, "Target");
        var other = product(Theme.HONEYMOON_ROMANCE, "Other");
        var late = schedule(target, "2026-11-10", false);
        var early = schedule(target, "2026-10-02", false);
        schedule(other, "2026-10-01", false);
        http.perform(get("/api/v1/tour-schedules").param("tourId", target.getId().toString())).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(contains(early.getId().intValue(), late.getId().intValue())));
    }

    @Test void unknownPositiveTourIdFilterReturnsEmptyArray() throws Exception {
        http.perform(get("/api/v1/tour-schedules").param("tourId", Long.toString(Long.MAX_VALUE)))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test void existingTourWithoutSchedulesReturnsEmptyArray() throws Exception {
        var tour = product(Theme.GOLF_CHALLENGE, "Trip");
        http.perform(get("/api/v1/tour-schedules").param("tourId", tour.getId().toString()))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test void invalidTourIdQueryReturnsInvalidQueryParameter() throws Exception {
        for (String id : List.of("abc", "0", "-1", "", "1.5", "9223372036854775808")) {
            http.perform(get("/api/v1/tour-schedules").param("tourId", id)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_QUERY_PARAMETER"))
                    .andExpect(jsonPath("$.fieldErrors").isEmpty());
        }
    }

    @Test void scheduleDetailReturnsContractRepresentation() throws Exception {
        var tour = product(Theme.GOLF_CHALLENGE, "Trip");
        var departure = schedule(tour, "2026-11-10", false);
        http.perform(get("/api/v1/tour-schedules/{id}", departure.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(departure.getId())).andExpect(jsonPath("$.tourId").value(tour.getId()))
                .andExpect(jsonPath("$.startDate").value("2026-11-10"))
                .andExpect(jsonPath("$.endDate").value("2026-11-14"))
                .andExpect(jsonPath("$.reservable").value(true))
                .andExpect(jsonPath("$.recruitment.confirmed").value(false))
                .andExpect(jsonPath("$.length()").value(6)).andExpect(jsonPath("$.recruitment.length()").value(4));
    }

    @Test void missingScheduleReturnsTourScheduleNotFound() throws Exception {
        http.perform(get("/api/v1/tour-schedules/{id}", Long.MAX_VALUE)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TOUR_SCHEDULE_NOT_FOUND")).andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test void invalidScheduleIdReturnsInvalidQueryParameter() throws Exception {
        for (String id : List.of("0", "-1", "abc")) {
            http.perform(get("/api/v1/tour-schedules/" + id)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_QUERY_PARAMETER"));
        }
    }

    @Test void futureScheduleIsReservable() throws Exception { assertReservable("2026-10-02", false, true); }
    @Test void sameDayScheduleIsNotReservable() throws Exception { assertReservable("2026-10-01", false, false); }
    @Test void pastScheduleIsNotReservable() throws Exception { assertReservable("2026-09-30", false, false); }
    @Test void confirmationDoesNotCloseFutureSchedule() throws Exception { assertReservable("2026-10-02", true, true); }
    @Test void confirmationDoesNotReopenSameDaySchedule() throws Exception { assertReservable("2026-10-01", true, false); }

    @Test void honeymoonRecruitmentUsesCoupleTeamAndRequiredCountTwo() throws Exception {
        recruitment(Theme.HONEYMOON_ROMANCE, "COUPLE_TEAM", 2);
    }

    @Test void generalRecruitmentUsesParticipantAndRequiredCountThree() throws Exception {
        for (Theme theme : List.of(Theme.PARENTS_HEALING, Theme.GOLF_CHALLENGE, Theme.OUTDOOR_TREKKING)) {
            recruitment(theme, "PARTICIPANT", 3);
        }
    }

    @Test void recruitmentCountIsZeroWhenScheduleHasNoReservations() throws Exception {
        var tour = product(Theme.HONEYMOON_ROMANCE, "Trip");
        schedule(tour, "2026-10-02", false);
        http.perform(get("/api/v1/tour-schedules")).andExpect(jsonPath("$[0].recruitment.currentCount").value(0));
    }

    @Test void publicEndpointWithMalformedBearerStillReturnsUnauthorized() throws Exception {
        http.perform(get("/api/v1/tours").header("Authorization", "Bearer malformed"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
    }

    private void assertReservable(String start, boolean confirmed, boolean expected) throws Exception {
        var departure = schedule(product(Theme.GOLF_CHALLENGE, "Trip"), start, confirmed);
        http.perform(get("/api/v1/tour-schedules/{id}", departure.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.reservable").value(expected))
                .andExpect(jsonPath("$.recruitment.confirmed").value(confirmed));
    }
    private void recruitment(Theme theme, String unit, int required) throws Exception {
        var departure = schedule(product(theme, "Trip"), "2026-10-02", false);
        http.perform(get("/api/v1/tour-schedules/{id}", departure.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.recruitment.unit").value(unit))
                .andExpect(jsonPath("$.recruitment.requiredCount").value(required));
    }
    private TourProductJpaEntity product(Theme theme, String name) {
        var tour = products.saveAndFlush(new TourProductJpaEntity(theme, name, "Travel description"));
        // Insert in reverse order to prove response order comes from the policy.
        var allowed = new ArrayList<>(TourStylePolicy.allowedStyles(theme));
        Collections.reverse(allowed);
        for (var style : allowed) prices.saveAndFlush(new TourProductStylePriceJpaEntity(tour, style, 3_000_000_000L));
        return tour;
    }
    private TourScheduleJpaEntity schedule(TourProductJpaEntity tour, String start, boolean confirmed) {
        LocalDate date = LocalDate.parse(start);
        return schedules.saveAndFlush(new TourScheduleJpaEntity(tour, date, date.plusDays(4), confirmed));
    }
    private TourProductWriteRequest request(Theme theme) {
        var allowed = new ArrayList<>(TourStylePolicy.allowedStyles(theme));
        Collections.reverse(allowed);
        return withPrices(theme, allowed.stream().map(style -> price(style, 100L, "KRW")).toList());
    }
    private TourProductWriteRequest withPrices(Theme theme, List<TourProductWriteRequest.StylePrice> entries) {
        return new TourProductWriteRequest(theme, "Updated trip", "Updated description", entries);
    }
    private TourProductWriteRequest.StylePrice price(TourStyle style, Long amount, String currency) {
        return new TourProductWriteRequest.StylePrice(style, amount, currency);
    }
    private ResultActions write(Object body, String bearer) throws Exception {
        return http.perform(post("/api/v1/employee/tours").header("Authorization", bearer)
                .contentType("application/json").content(json.writeValueAsString(body)));
    }
    private ResultActions update(long id, Object body, String bearer) throws Exception {
        return http.perform(put("/api/v1/employee/tours/{id}", id).header("Authorization", bearer)
                .contentType("application/json").content(json.writeValueAsString(body)));
    }
    private ResultActions rejects(Object body, String field, String code) throws Exception {
        return write(body, employee).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem(field)))
                .andExpect(jsonPath("$.fieldErrors[*].code", hasItem(code)));
    }
}
