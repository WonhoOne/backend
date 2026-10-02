package com.wonhoone.misterworld.demo;

import com.wonhoone.misterworld.application.demo.*;
import com.wonhoone.misterworld.api.dto.*;
import com.wonhoone.misterworld.application.auth.AuthService;
import com.wonhoone.misterworld.application.reservation.ReservationCommandService;
import com.wonhoone.misterworld.application.tour.TourScheduleQueryService;
import com.wonhoone.misterworld.domain.*;
import com.wonhoone.misterworld.infrastructure.persistence.entity.*;
import com.wonhoone.misterworld.infrastructure.persistence.repository.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {"demo.provisioning.enabled=false", "sms.delivery.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:b9-demo;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"})
@ActiveProfiles("test")
class DemoProvisioningTests {
    @TestBean(name = "businessClock", methodName = "fixedClock") Clock clock;
    static Clock fixedClock() { return Clock.fixed(Instant.parse("2026-09-30T15:00:00Z"), ZoneId.of("Asia/Seoul")); }
    @Autowired DemoScenarioReader reader;
    @Autowired DemoScenarioProvisioner provisioner;
    @Autowired TourProductJpaRepository products;
    @Autowired TourProductStylePriceJpaRepository prices;
    @MockitoSpyBean TourScheduleJpaRepository schedules;
    @Autowired ReservationJpaRepository reservations;
    @Autowired SmsConfirmationEventJpaRepository events;
    @Autowired SmsConfirmationRecipientJpaRepository recipients;
    @Autowired UserAccountJpaRepository accounts;
    @Autowired TourScheduleQueryService queries;
    @Autowired AuthService auth;
    @Autowired ReservationCommandService commands;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @TempDir Path temporary;
    String database;

    @BeforeEach void freshFixtures() {
        reset(schedules);
        for (String table : List.of("sms_confirmation_recipient", "sms_confirmation_event", "tour_reservation_extra_option",
                "tour_reservation", "tour_schedule", "tour_product_style_price", "tour_product", "user_account"))
            jdbc.update("DELETE FROM " + table);
        database = jdbc.queryForObject("SELECT DATABASE()", String.class);
    }
    DemoScenarioManifest.Product product(String key, Theme theme) {
        return new DemoScenarioManifest.Product(key, theme, "B9 Test Product", "Clearly test-only description",
                TourStylePolicy.allowedStyles(theme).stream().map(style ->
                        new TourProductWriteRequest.StylePrice(style, 3_000_000_001L, "KRW")).toList());
    }
    DemoScenarioManifest valid() {
        return new DemoScenarioManifest(1, List.of(product("test", Theme.GOLF_CHALLENGE)),
                List.of(new DemoScenarioManifest.Schedule("test", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 5))));
    }
    void provision(DemoScenarioManifest manifest) { provisioner.provision(manifest, database); }
    void rejected(DemoScenarioManifest manifest) {
        assertThatThrownBy(() -> provision(manifest)).isInstanceOf(IllegalStateException.class);
        assertThat(products.count()).isZero(); assertThat(prices.count()).isZero(); assertThat(schedules.count()).isZero();
    }
    DemoScenarioManifest priced(List<TourProductWriteRequest.StylePrice> values) {
        return new DemoScenarioManifest(1, List.of(new DemoScenarioManifest.Product("test", Theme.GOLF_CHALLENGE,
                "B9 Test Product", "Clearly test-only description", values)), valid().schedules());
    }
    @Test void disabledProvisioningDoesNothing() {
        var source = mock(DemoScenarioReader.class); var target = mock(DemoScenarioProvisioner.class);
        new DemoScenarioBootstrap(new DemoProvisioningProperties(false, "", ""), source, target)
                .run(new DefaultApplicationArguments(new String[0]));
        verifyNoInteractions(source, target);
    }
    @Test void enabledProvisioningRequiresManifest() {
        assertThatThrownBy(() -> new DemoProvisioningProperties(true, "", database).requireEnabledConfiguration())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("DEMO_SCENARIO_FILE");
    }
    @Test void enabledProvisioningRequiresExpectedDatabase() {
        assertThatThrownBy(() -> new DemoProvisioningProperties(true, "file", "").requireEnabledConfiguration())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("DEMO_EXPECTED_DATABASE");
    }
    @Test void expectedDatabaseMismatchFailsBeforeWrite() {
        assertThatThrownBy(() -> provisioner.provision(valid(), "wrong")).isInstanceOf(IllegalStateException.class);
        assertThat(products.count()).isZero();
    }
    @Test void nullDatabaseFailsBeforeWrite() {
        assertThatThrownBy(() -> provisioner.provision(valid(), null)).isInstanceOf(IllegalStateException.class);
        assertThat(products.count()).isZero();
    }
    @Test void invalidJsonFailsBeforeWriteWithoutEchoingContent() throws Exception {
        Path file = temporary.resolve("invalid.json"); Files.writeString(file, "private-test-only-value {");
        assertThatThrownBy(() -> reader.read(file.toString())).isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("private-test-only-value").hasCause(null);
        assertThat(products.count()).isZero();
    }
    @Test void missingAndDirectoryPathsFailSafely() {
        assertThatThrownBy(() -> reader.read(temporary.resolve("missing.json").toString())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> reader.read(temporary.toString())).isInstanceOf(IllegalStateException.class);
    }
    @Test void duplicateProductKeysRejected() {
        rejected(new DemoScenarioManifest(1, List.of(product("test", Theme.GOLF_CHALLENGE),
                product("test", Theme.PARENTS_HEALING)), valid().schedules()));
    }
    @Test void unknownScheduleProductKeyRejected() {
        rejected(new DemoScenarioManifest(1, valid().products(), List.of(new DemoScenarioManifest.Schedule("unknown",
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 5)))));
    }
    @Test void invalidThemeStylePricesRejected() {
        rejected(new DemoScenarioManifest(1, List.of(new DemoScenarioManifest.Product("test", Theme.PARENTS_HEALING,
                "B9 Test Product", "Test-only", valid().products().getFirst().stylePrices())), valid().schedules()));
    }
    @Test void incompleteAndDuplicateStylePricesRejected() {
        rejected(priced(List.of(new TourProductWriteRequest.StylePrice(TourStyle.GRAND, 1L, "KRW"))));
        var values = new ArrayList<>(valid().products().getFirst().stylePrices()); values.add(values.getFirst());
        rejected(priced(values));
    }
    @Test void nonKrwPriceRejected() {
        rejected(priced(TourStylePolicy.allowedStyles(Theme.GOLF_CHALLENGE).stream().map(style ->
                new TourProductWriteRequest.StylePrice(style, 1L, "USD")).toList()));
    }
    @ParameterizedTest @ValueSource(longs = {0, -1}) void nonPositivePriceRejected(long amount) {
        rejected(priced(TourStylePolicy.allowedStyles(Theme.GOLF_CHALLENGE).stream().map(style ->
                new TourProductWriteRequest.StylePrice(style, amount, "KRW")).toList()));
    }
    @ParameterizedTest @ValueSource(strings = {"2026-10-01", "2026-09-30"}) void sameDayAndPastScheduleRejected(String start) {
        rejected(new DemoScenarioManifest(1, valid().products(), List.of(new DemoScenarioManifest.Schedule("test",
                LocalDate.parse(start), LocalDate.of(2026, 11, 5)))));
    }
    @Test void startAfterEndRejected() {
        rejected(new DemoScenarioManifest(1, valid().products(), List.of(new DemoScenarioManifest.Schedule("test",
                LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 1)))));
    }
    @Test void nullRequiredFieldsRejected() {
        rejected(new DemoScenarioManifest(2, valid().products(), valid().schedules()));
        rejected(new DemoScenarioManifest(1, null, valid().schedules()));
        rejected(new DemoScenarioManifest(1, valid().products(), null));
        rejected(priced(null));
        rejected(new DemoScenarioManifest(1, valid().products(), List.of(new DemoScenarioManifest.Schedule("test", null, null))));
    }
    @Test void productRequiredAndStorageBoundsRejected() {
        for (String name : List.of("", "x".repeat(256)))
            rejected(new DemoScenarioManifest(1, List.of(new DemoScenarioManifest.Product("test", Theme.GOLF_CHALLENGE,
                    name, "Test-only", valid().products().getFirst().stylePrices())), valid().schedules()));
        rejected(new DemoScenarioManifest(1, List.of(new DemoScenarioManifest.Product("test", Theme.GOLF_CHALLENGE,
                "B9 Test Product", "x".repeat(2001), valid().products().getFirst().stylePrices())), valid().schedules()));
    }
    @Test void validExternalScenarioCreatesProductsPricesAndUnconfirmedReservableSchedules() throws Exception {
        var values = new ArrayList<DemoScenarioManifest.Product>();
        for (var theme : Theme.values()) values.add(product(theme.name(), theme));
        values.add(product("second-golf", Theme.GOLF_CHALLENGE));
        var manifest = new DemoScenarioManifest(1, values, values.stream().map(p ->
                new DemoScenarioManifest.Schedule(p.key(), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 5))).toList());
        Path file = temporary.resolve("test-only.json"); Files.writeString(file, json.writeValueAsString(manifest));
        provision(reader.read(file.toString()));
        assertThat(products.count()).isEqualTo(5); assertThat(prices.count()).isEqualTo(13);
        assertThat(queries.list(null)).hasSize(5).allSatisfy(s -> {
            assertThat(s.reservable()).isTrue(); assertThat(s.recruitment().confirmed()).isFalse();
            assertThat(queries.list(s.tourId())).hasSize(1);
        });
        assertThat(accounts.count()).isZero(); assertThat(reservations.count()).isZero(); assertThat(events.count()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory", Long.class)).isEqualTo(4);
        assertThat(jdbc.queryForList("SELECT quantity FROM inventory", Long.class)).containsOnly(0L);
    }
    @Test void partialFailureRollsBackEverything() {
        doThrow(new IllegalStateException("Test-only later schedule failure")).when(schedules).flush();
        rejected(valid());
    }
    @Test void existingProductDataBlocksProvisioning() {
        products.saveAndFlush(new TourProductJpaEntity(Theme.GOLF_CHALLENGE, "Existing test", "Test-only"));
        assertThatThrownBy(() -> provision(valid())).isInstanceOf(IllegalStateException.class);
        assertThat(products.count()).isEqualTo(1); assertThat(prices.count()).isZero(); assertThat(schedules.count()).isZero();
    }
    @Test void existingReservationAndSmsDataBlocksProvisioningWithoutChanges() {
        provision(valid());
        var customer = auth.signup(new SignupRequest("b9-test-only", "b9-test-only-password", "B9 Test Customer",
                "Test-only address", "010-0000-0000"));
        var schedule = schedules.findAll().getFirst();
        commands.create(customer.id(), new ReservationCreateRequest(schedule.getId(), 3,
                new ReservationCreateRequest.Configuration(TourStyle.GRAND, HotelOption.HOTEL_4_STAR,
                        TransportOption.PREMIUM_VAN_10, MealOption.LOCAL_RESTAURANT, List.of())));
        assertThatThrownBy(() -> provision(valid())).isInstanceOf(IllegalStateException.class);
        assertThat(reservations.count()).isEqualTo(1); assertThat(events.count()).isEqualTo(1);
        assertThat(recipients.findAll()).singleElement().satisfies(r -> {
            assertThat(r.getStatus()).isEqualTo(SmsDeliveryStatus.PENDING); assertThat(r.getAttemptCount()).isZero();
        });
    }
    @Test void accountsAreAllowedButNeverCreatedByManifest() {
        accounts.saveAndFlush(new UserAccountJpaEntity("b9-test-employee", "test-only-hash", "Test employee",
                "Test address", "Test contact", UserRole.EMPLOYEE));
        accounts.saveAndFlush(new UserAccountJpaEntity("b9-test-customer", "test-only-hash", "Test customer",
                "Test address", "Test contact", UserRole.CUSTOMER));
        provision(valid()); assertThat(accounts.count()).isEqualTo(2);
    }
    @ParameterizedTest @ValueSource(strings = {"customer", "password", "contact", "confirmed", "reservations", "solapi"})
    void noPersonalOrBypassFieldsAreReadFromManifest(String field) throws Exception {
        Path file = temporary.resolve("unknown.json");
        Files.writeString(file, json.writeValueAsString(valid()).replaceFirst("\\{", "{\"" + field + "\":\"private-test-only\","));
        assertThatThrownBy(() -> reader.read(file.toString())).isInstanceOf(IllegalStateException.class).hasCause(null);
        assertThat(products.count()).isZero();
    }
    @ParameterizedTest @ValueSource(strings = {"1.5", "\"101\"", "9223372036854775808"})
    void fractionalStringAndOverflowAmountsRejected(String amount) throws Exception {
        Path file = temporary.resolve("invalid-price.json");
        Files.writeString(file, json.writeValueAsString(valid()).replace("3000000001", amount));
        assertThatThrownBy(() -> reader.read(file.toString())).isInstanceOf(IllegalStateException.class);
    }
    @Test void trailingJsonAndUnknownEnumRejected() throws Exception {
        Path file = temporary.resolve("invalid.json"); Files.writeString(file, json.writeValueAsString(valid()) + " {}");
        assertThatThrownBy(() -> reader.read(file.toString())).isInstanceOf(IllegalStateException.class);
        Files.writeString(file, json.writeValueAsString(valid()).replace("GOLF_CHALLENGE", "UNKNOWN"));
        assertThatThrownBy(() -> reader.read(file.toString())).isInstanceOf(IllegalStateException.class);
    }
}
