package com.wonhoone.misterworld.mysql;

import com.wonhoone.misterworld.api.dto.*;
import com.wonhoone.misterworld.application.reservation.ReservationCommandService;
import com.wonhoone.misterworld.application.inventory.InventoryCommandService;
import com.wonhoone.misterworld.domain.*;
import com.wonhoone.misterworld.infrastructure.persistence.entity.*;
import com.wonhoone.misterworld.infrastructure.persistence.repository.*;
import java.sql.DriverManager;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
        "sms.delivery.enabled=false", "employee.bootstrap.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.properties.hibernate.jdbc.time_zone=UTC",
        "spring.datasource.hikari.maximum-pool-size=6", "spring.datasource.hikari.connection-timeout=5000",
        "spring.datasource.hikari.connection-init-sql=SET SESSION innodb_lock_wait_timeout=8"})
@ContextConfiguration(initializers = MySqlIntegrationSupport.EmptyTestDatabase.class)
@Timeout(40)
abstract class MySqlIntegrationSupport {
    @TestBean(name = "businessClock", methodName = "businessClock") Clock clock;
    static Clock businessClock() {
        return Clock.fixed(Instant.parse("2026-09-30T15:00:00Z"), ZoneId.of("Asia/Seoul"));
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired UserAccountJpaRepository accounts;
    @Autowired TourProductJpaRepository products;
    @Autowired TourProductStylePriceJpaRepository prices;
    @Autowired TourScheduleJpaRepository schedules;
    @Autowired ReservationJpaRepository reservations;
    @Autowired InventoryJpaRepository inventory;
    @Autowired SmsConfirmationEventJpaRepository events;
    @Autowired SmsConfirmationRecipientJpaRepository recipients;
    @Autowired ReservationCommandService commands;
    @Autowired InventoryCommandService inventoryCommands;

    /** Before Flyway or startup runners: refuse accidental use of an existing/operator database. */
    public static class EmptyTestDatabase implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override public void initialize(ConfigurableApplicationContext context) {
            var env = context.getEnvironment();
            if (!"misterworld".equals(System.getenv("B9_MYSQL_TEST_DATABASE"))) {
                throw new IllegalStateException("B9 requires explicit B9_MYSQL_TEST_DATABASE=misterworld for a disposable empty database");
            }
            String url = env.getRequiredProperty("spring.datasource.url");
            if (!url.startsWith("jdbc:mysql:")) throw new IllegalStateException("B9 requires actual MySQL, never H2");
            try (var connection = DriverManager.getConnection(url,
                    env.getRequiredProperty("spring.datasource.username"), env.getRequiredProperty("spring.datasource.password"));
                 var statement = connection.createStatement()) {
                try (var rs = statement.executeQuery("SELECT DATABASE(), VERSION(), @@version_comment")) {
                    rs.next();
                    if (!"misterworld".equals(rs.getString(1)) || !rs.getString(2).startsWith("8.")
                            || rs.getString(3).toLowerCase(java.util.Locale.ROOT).contains("mariadb")) {
                        throw new IllegalStateException("B9 requires the dedicated misterworld database on MySQL 8");
                    }
                }
                try (var rs = statement.executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()")) {
                    rs.next();
                    if (rs.getLong(1) != 0) throw new IllegalStateException("B9 requires a fresh empty database; use the local fresh command");
                }
                java.nio.file.Files.createDirectories(java.nio.file.Path.of("target"));
                java.nio.file.Files.writeString(java.nio.file.Path.of("target/b9-mysql-runtime.txt"), "Fresh empty MySQL schema confirmed before Flyway\n");
            } catch (java.sql.SQLException failure) {
                // Do not include URLs, credentials or driver messages in this diagnostic.
                throw new IllegalStateException("B9 MySQL preflight failed, SQLState=" + failure.getSQLState());
            } catch (java.io.IOException failure) {
                throw new IllegalStateException("B9 could not create runtime evidence file");
            }
        }
    }

    @BeforeAll static void verifyFreshV2BeforeAnyFixtureCleanup(@Autowired JdbcTemplate jdbc) {
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForList("SELECT item_type FROM inventory ORDER BY item_type", String.class))
                .containsExactly("COUPLE_TSHIRT", "GINSENG_GIFT", "GOLF_BALL", "SCARF");
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForList("SELECT quantity FROM inventory", Long.class))
                .hasSize(4).containsOnly(0L);
    }
    @BeforeEach void cleanBefore() { cleanFixtures(); }
    @AfterEach void cleanAfter() { cleanFixtures(); }
    private void cleanFixtures() {
        // Dedicated empty schema was checked before startup. Keep Flyway history and V2 catalog intact.
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            for (String table : List.of("sms_confirmation_recipient", "sms_confirmation_event",
                    "tour_reservation_extra_option", "tour_reservation", "tour_schedule",
                    "tour_product_style_price", "tour_product", "user_account")) {
                jdbc.update("DELETE FROM " + table);
            }
            jdbc.update("UPDATE inventory SET quantity=0");
        });
    }
    UserAccountJpaEntity customer(String login) {
        return accounts.saveAndFlush(new UserAccountJpaEntity("b9-test-" + login, "test-only-hash",
                "MySQL IT Customer", "Test-only address", "010-0000-0000", UserRole.CUSTOMER));
    }
    TourScheduleJpaEntity schedule(LocalDate start, boolean confirmed) {
        var product = products.saveAndFlush(new TourProductJpaEntity(Theme.GOLF_CHALLENGE,
                "MySQL IT Product", "Obviously test-only fixture"));
        for (var style : TourStyle.values()) {
            prices.saveAndFlush(new TourProductStylePriceJpaEntity(product, style, 3_000_000_001L));
        }
        return schedules.saveAndFlush(new TourScheduleJpaEntity(product, start, start.plusDays(4), confirmed));
    }
    TourScheduleJpaEntity futureSchedule() { return schedule(LocalDate.of(2026, 11, 1), false); }
    ReservationCreateRequest request(long scheduleId, int count) {
        return new ReservationCreateRequest(scheduleId, count, new ReservationCreateRequest.Configuration(
                TourStyle.GRAND, HotelOption.HOTEL_4_STAR, TransportOption.PREMIUM_VAN_10,
                MealOption.LOCAL_RESTAURANT, List.of(ExtraOption.COFFEE)));
    }
    ReservationJpaEntity historical(UserAccountJpaEntity customer, TourScheduleJpaEntity schedule) {
        return reservations.saveAndFlush(ReservationJpaEntity.capture(customer, schedule, 2,
                TourConfiguration.create(TourStyle.GRAND, HotelOption.HOTEL_4_STAR,
                        TransportOption.PREMIUM_VAN_10, MealOption.LOCAL_RESTAURANT, List.of()),
                ReservationPriceCalculator.calculate(3_000_000_001L, 2, false)));
    }
    SmsConfirmationRecipientJpaEntity recipient(long scheduleId, long customerId, Instant at) {
        var event = events.saveAndFlush(new SmsConfirmationEventJpaEntity(scheduleId, "MySQL IT confirmation", at));
        return recipients.saveAndFlush(new SmsConfirmationRecipientJpaEntity(event, customerId, "010-0000-0000", at));
    }
}
