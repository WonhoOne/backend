package com.wonhoone.misterworld.tour;

import com.wonhoone.misterworld.api.dto.TourProductWriteRequest;
import com.wonhoone.misterworld.domain.*;
import com.wonhoone.misterworld.infrastructure.persistence.entity.*;
import com.wonhoone.misterworld.infrastructure.persistence.repository.*;
import com.wonhoone.misterworld.security.JwtTokenService;
import jakarta.servlet.Filter;
import java.time.LocalDate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:theme-lock;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@ActiveProfiles("test")
class ThemeLockIntegrationTests {
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
    MockMvc http;
    String employee;

    @BeforeEach void prepare() {
        schedules.deleteAll();
        prices.deleteAll();
        products.deleteAll();
        http = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilter).build();
        employee = "Bearer " + tokens.issue(1, UserRole.EMPLOYEE).value();
    }

    @Test void productThemeCanChangeBeforeAnyScheduleExists() throws Exception {
        var product = product();
        assertFalse(schedules.existsByTourProductId(product.getId()));
        update(product, Theme.HONEYMOON_ROMANCE).andExpect(status().isOk())
                .andExpect(jsonPath("$.theme").value("HONEYMOON_ROMANCE"))
                .andExpect(jsonPath("$.stylePrices.length()").value(2));
    }
    @Test void productThemeCannotChangeAfterScheduleExists() throws Exception {
        var product = product();
        schedule(product, "2026-11-01", false);
        assertTrue(schedules.existsByTourProductId(product.getId()));
        update(product, Theme.HONEYMOON_ROMANCE).andExpect(status().isConflict());
    }
    @Test void sameThemeProductEditRemainsAllowedAfterScheduleExists() throws Exception {
        var product = product();
        schedule(product, "2026-11-01", false);
        update(product, Theme.GOLF_CHALLENGE).andExpect(status().isOk())
                .andExpect(jsonPath("$.theme").value("GOLF_CHALLENGE"));
    }
    @Test void themeLockStillAllowsNameDescriptionAndPriceChanges() throws Exception {
        var product = product();
        schedule(product, "2026-09-01", true);
        update(product, Theme.GOLF_CHALLENGE).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New name"))
                .andExpect(jsonPath("$.description").value("New description"));
        var stored = products.findById(product.getId()).orElseThrow();
        assertEquals("New name", stored.getName());
        assertEquals("New description", stored.getDescription());
        prices.findByTourProductId(product.getId()).forEach(price -> assertEquals(200, price.getAmount()));
    }
    @Test void pastScheduleStillLocksTheme() throws Exception {
        var product = product();
        schedule(product, "2026-09-01", false);
        update(product, Theme.HONEYMOON_ROMANCE).andExpect(status().isConflict());
    }
    @Test void sameDayScheduleStillLocksTheme() throws Exception {
        var product = product();
        schedule(product, "2026-10-01", false);
        update(product, Theme.OUTDOOR_TREKKING).andExpect(status().isConflict());
    }
    @Test void confirmedScheduleStillLocksTheme() throws Exception {
        var product = product();
        schedule(product, "2026-11-01", true);
        update(product, Theme.HONEYMOON_ROMANCE).andExpect(status().isConflict());
    }
    @Test void themeLockConflictReturns409AndStableCode() throws Exception {
        var product = product();
        schedule(product, "2026-11-01", false);
        update(product, Theme.HONEYMOON_ROMANCE).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TOUR_PRODUCT_THEME_LOCKED"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.length()").value(3));
    }
    @Test void themeLockConflictDoesNotPartiallyUpdateProductOrPrices() throws Exception {
        var product = product();
        schedule(product, "2026-11-01", false);
        var before = http.perform(get("/api/v1/tours/{id}", product.getId()))
                .andReturn().getResponse().getContentAsString();
        var priceIds = prices.findByTourProductId(product.getId()).stream()
                .map(TourProductStylePriceJpaEntity::getId).toList();
        update(product, Theme.HONEYMOON_ROMANCE).andExpect(status().isConflict());
        http.perform(get("/api/v1/tours/{id}", product.getId())).andExpect(content().json(before));
        assertEquals(priceIds, prices.findByTourProductId(product.getId()).stream()
                .map(TourProductStylePriceJpaEntity::getId).toList());
    }
    @Test void ordinaryWriteValidationStillRunsBeforeMutation() throws Exception {
        var product = product();
        schedule(product, "2026-11-01", false);
        var invalid = new TourProductWriteRequest(Theme.GOLF_CHALLENGE, "New", "New", java.util.List.of());
        http.perform(put("/api/v1/employee/tours/{id}", product.getId()).header("Authorization", employee)
                .contentType("application/json").content(json.writeValueAsString(invalid)))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertEquals("Old name", products.findById(product.getId()).orElseThrow().getName());
    }
    @Test void customerCannotBypassEmployeeThemeLockBoundary() throws Exception {
        var product = product();
        http.perform(put("/api/v1/employee/tours/{id}", product.getId())
                .header("Authorization", "Bearer " + tokens.issue(2, UserRole.CUSTOMER).value())
                .contentType("application/json").content(json.writeValueAsString(request(Theme.GOLF_CHALLENGE))))
                .andExpect(status().isForbidden());
    }
    @Test void anotherProductsScheduleDoesNotLockUnscheduledProduct() throws Exception {
        var scheduled = product();
        schedule(scheduled, "2026-11-01", false);
        update(product(), Theme.HONEYMOON_ROMANCE).andExpect(status().isOk());
    }

    private TourProductJpaEntity product() {
        var product = products.saveAndFlush(new TourProductJpaEntity(Theme.GOLF_CHALLENGE, "Old name", "Old description"));
        for (var style : TourStyle.values()) prices.saveAndFlush(new TourProductStylePriceJpaEntity(product, style, 100));
        return product;
    }
    private void schedule(TourProductJpaEntity product, String date, boolean confirmed) {
        var start = LocalDate.parse(date);
        schedules.saveAndFlush(new TourScheduleJpaEntity(product, start, start, confirmed));
    }
    private TourProductWriteRequest request(Theme theme) {
        var entries = TourStylePolicy.allowedStyles(theme).stream()
                .map(style -> new TourProductWriteRequest.StylePrice(style, 200L, "KRW")).toList();
        return new TourProductWriteRequest(theme, "New name", "New description", entries);
    }
    private ResultActions update(TourProductJpaEntity product, Theme theme) throws Exception {
        return http.perform(put("/api/v1/employee/tours/{id}", product.getId()).header("Authorization", employee)
                .contentType("application/json").content(json.writeValueAsString(request(theme))));
    }
}
