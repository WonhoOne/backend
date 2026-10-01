package com.wonhoone.misterworld.reservation;

import com.wonhoone.misterworld.api.dto.ReservationCreateRequest;
import com.wonhoone.misterworld.application.reservation.*;
import com.wonhoone.misterworld.domain.*;
import com.wonhoone.misterworld.infrastructure.persistence.entity.*;
import com.wonhoone.misterworld.infrastructure.persistence.repository.*;
import com.wonhoone.misterworld.security.JwtTokenService;
import jakarta.servlet.Filter;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:reservation-api;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;LOCK_TIMEOUT=5000")
@ActiveProfiles("test")
abstract class ReservationIntegrationSupport {
    @TestBean(name = "businessClock", methodName = "fixedBusinessClock") Clock clock;
    static Clock fixedBusinessClock() {
        return Clock.fixed(Instant.parse("2026-09-30T15:00:00Z"), ZoneId.of("Asia/Seoul"));
    }
    @Autowired WebApplicationContext context;
    @Autowired @Qualifier("springSecurityFilterChain") Filter securityFilter;
    @Autowired UserAccountJpaRepository accounts;
    @Autowired TourProductJpaRepository products;
    @Autowired TourProductStylePriceJpaRepository prices;
    @Autowired TourScheduleJpaRepository schedules;
    @Autowired ReservationJpaRepository reservations;
    @Autowired ReservationCommandService commands;
    @Autowired ReservationQueryService queries;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JwtTokenService tokens;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    MockMvc http;
    UserAccountJpaEntity customer;
    TourScheduleJpaEntity schedule;
    String bearer;

    @BeforeEach void prepareReservationFixtures() {
        reservations.deleteAll();
        schedules.deleteAll();
        prices.deleteAll();
        products.deleteAll();
        accounts.deleteAll();
        http = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilter).build();
        customer = account("owner");
        bearer = token(customer.getId(), UserRole.CUSTOMER);
        schedule = schedule(Theme.GOLF_CHALLENGE, LocalDate.of(2026, 11, 1), false);
    }

    UserAccountJpaEntity account(String login) {
        return accounts.saveAndFlush(new UserAccountJpaEntity(login, "test-hash", "Private name",
                "Private address", "Private contact", UserRole.CUSTOMER));
    }
    String token(long id, UserRole role) { return "Bearer " + tokens.issue(id, role).value(); }

    TourScheduleJpaEntity schedule(Theme theme, LocalDate start, boolean confirmed) {
        var product = products.saveAndFlush(new TourProductJpaEntity(theme, "Original trip", "Description"));
        for (var style : TourStylePolicy.allowedStyles(theme)) {
            prices.saveAndFlush(new TourProductStylePriceJpaEntity(product, style, 101 + style.ordinal() * 100));
        }
        return schedules.saveAndFlush(new TourScheduleJpaEntity(product, start, start.plusDays(4), confirmed));
    }

    ReservationCreateRequest request(TourScheduleJpaEntity target, int count) {
        return new ReservationCreateRequest(target.getId(), count,
                configuration(TourStyle.GRAND, TransportOption.PREMIUM_VAN_10, List.of()));
    }
    ReservationCreateRequest.Configuration configuration(TourStyle style, TransportOption transport, List<ExtraOption> extras) {
        return new ReservationCreateRequest.Configuration(style, HotelOption.HOTEL_4_STAR, transport,
                MealOption.LOCAL_RESTAURANT, extras);
    }
    ReservationCreationResult create(TourScheduleJpaEntity target, int count) {
        return commands.create(customer.getId(), request(target, count));
    }
    ResultActions postReservation(Object body) throws Exception { return postReservation(body, bearer); }
    ResultActions postReservation(Object body, String authorization) throws Exception {
        var request = post("/api/v1/reservations").contentType("application/json").content(json.writeValueAsString(body));
        if (authorization != null) request.header("Authorization", authorization);
        return http.perform(request);
    }
    ResultActions detail(long id) throws Exception {
        return http.perform(get("/api/v1/reservations/{id}", id).header("Authorization", bearer));
    }
    long postId(ReservationCreateRequest body) throws Exception {
        var response = postReservation(body).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                .isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asLong();
    }
    ReservationJpaEntity historicalTrip(UserAccountJpaEntity owner, LocalDate end, boolean confirmed) {
        var previous = schedule(Theme.GOLF_CHALLENGE, end.minusDays(4), confirmed);
        var selected = TourConfiguration.create(TourStyle.GRAND, HotelOption.HOTEL_4_STAR,
                TransportOption.PREMIUM_VAN_10, MealOption.LOCAL_RESTAURANT, List.of());
        return reservations.saveAndFlush(ReservationJpaEntity.capture(owner, previous, 2, selected,
                ReservationPriceCalculator.calculate(201, 2, false)));
    }
}
