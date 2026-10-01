package com.wonhoone.misterworld.infrastructure.persistence;

import com.wonhoone.misterworld.domain.*;
import com.wonhoone.misterworld.infrastructure.persistence.entity.*;
import com.wonhoone.misterworld.infrastructure.persistence.repository.*;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:reservation-persistence;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@ActiveProfiles("test")
@Transactional
class ReservationPersistenceTests {
    @Autowired UserAccountJpaRepository accounts;
    @Autowired TourProductJpaRepository products;
    @Autowired TourProductStylePriceJpaRepository prices;
    @Autowired TourScheduleJpaRepository schedules;
    @Autowired ReservationJpaRepository reservations;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;
    UserAccountJpaEntity customer;
    TourProductJpaEntity product;
    TourScheduleJpaEntity schedule;

    @BeforeEach void fixtures() {
        customer = accounts.saveAndFlush(new UserAccountJpaEntity("snapshot-owner", "test-hash", "A", "B", "C", UserRole.CUSTOMER));
        product = products.saveAndFlush(new TourProductJpaEntity(Theme.GOLF_CHALLENGE, "Old Name", "Description"));
        for (var style : TourStyle.values()) prices.save(new TourProductStylePriceJpaEntity(product, style, 1_000_000));
        schedule = schedules.saveAndFlush(new TourScheduleJpaEntity(product,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 5), false));
    }

    @Test void reservationSnapshotPersistsCustomerAndScheduleReference() {
        var saved = persist(false, Set.of());
        var reloaded = reload(saved);
        assertTrue(reloaded.getId() > 0);
        assertEquals(customer.getId(), reloaded.getCustomer().getId());
        assertEquals(schedule.getId(), reloaded.getTourSchedule().getId());
    }
    @Test void reservationSnapshotPersistsParticipantCount() {
        assertEquals(4, reload(persist(false, Set.of())).getParticipantCount());
    }
    @Test void configurationSnapshotRoundTrips() {
        var reloaded = reload(persist(false, Set.of(ExtraOption.COFFEE, ExtraOption.CHAMPAGNE)));
        assertEquals(configuration(Set.of(ExtraOption.COFFEE, ExtraOption.CHAMPAGNE)), reloaded.getConfiguration());
        assertEquals("GRAND", jdbc.queryForObject("SELECT style FROM tour_reservation WHERE id = ?", String.class, reloaded.getId()));
    }
    @Test void extraOptionsRoundTripAsUniqueSet() {
        var reloaded = reload(persist(false, Set.of(ExtraOption.COFFEE, ExtraOption.CHAMPAGNE)));
        assertEquals(Set.of(ExtraOption.CHAMPAGNE, ExtraOption.COFFEE), reloaded.getConfiguration().extraOptions());
        assertThrows(UnsupportedOperationException.class, () -> reloaded.getConfiguration().extraOptions().clear());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM tour_reservation_extra_option WHERE reservation_id = ?",
                Integer.class, reloaded.getId()));
    }
    @Test void emptyExtraOptionsRoundTrip() {
        assertTrue(reload(persist(false, Set.of())).getConfiguration().extraOptions().isEmpty());
    }
    @Test void priceSnapshotWithoutDiscountRoundTrips() {
        var reloaded = reload(persist(false, Set.of()));
        assertEquals(ReservationPriceCalculator.calculate(1_000_000, 4, false), reloaded.getPrice());
        var stored = jdbc.queryForMap("SELECT discount_type, discount_rate_percent, discount_amount, currency FROM tour_reservation WHERE id = ?", reloaded.getId());
        assertNull(stored.get("discount_type"));
        assertNull(stored.get("discount_rate_percent"));
        assertNull(stored.get("discount_amount"));
        assertEquals("KRW", stored.get("currency"));
    }
    @Test void loyaltyDiscountSnapshotRoundTrips() {
        var reloaded = reload(persist(true, Set.of()));
        assertEquals(ReservationPriceCalculator.calculate(1_000_000, 4, true), reloaded.getPrice());
        assertEquals(new DiscountSnapshot(DiscountType.LOYALTY, 5, 200_000), reloaded.getPrice().discount());
    }
    @Test void productNameAndPriceRemainStableAfterProductEdits() {
        var saved = persist(false, Set.of(ExtraOption.COFFEE));
        product.updateDetails(product.getTheme(), "New Name", "New description");
        prices.deleteByTourProductId(product.getId());
        prices.flush();
        for (var style : TourStyle.values()) prices.save(new TourProductStylePriceJpaEntity(product, style, 2_000_000));
        entityManager.flush();
        var reloaded = reload(saved);
        assertEquals("New Name", reloaded.getTourSchedule().getTourProduct().getName());
        assertTrue(prices.findByTourProductId(product.getId()).stream().allMatch(price -> price.getAmount() == 2_000_000));
        assertEquals("Old Name", reloaded.getTourProductNameSnapshot());
        assertEquals(ReservationPriceCalculator.calculate(1_000_000, 4, false), reloaded.getPrice());
    }
    @Test void historicalThemeAndDatesRemainStored() {
        var saved = persist(false, Set.of());
        jdbc.update("UPDATE tour_schedule SET start_date = ?, end_date = ? WHERE id = ?",
                LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 5), schedule.getId());
        var reloaded = reload(saved);
        assertEquals(product.getId().longValue(), reloaded.getTourProductIdSnapshot());
        assertEquals(Theme.GOLF_CHALLENGE, reloaded.getTourProductThemeSnapshot());
        assertEquals(LocalDate.of(2026, 11, 1), reloaded.getScheduleStartDateSnapshot());
        assertEquals(LocalDate.of(2026, 11, 5), reloaded.getScheduleEndDateSnapshot());
        assertEquals(LocalDate.of(2026, 12, 5), reloaded.getTourSchedule().getEndDate());
    }
    @Test void optionsDoNotChangePrice() {
        var base = persist(false, Set.of());
        var changed = TourConfiguration.create(TourStyle.GRAND, HotelOption.HOTEL_5_STAR,
                TransportOption.PREMIUM_VAN_10, MealOption.PREMIUM_RESTAURANT, List.of(ExtraOption.CHAMPAGNE));
        var other = reservations.saveAndFlush(ReservationJpaEntity.capture(customer, schedule, 4, changed, base.getPrice()));
        assertNotEquals(reload(base).getConfiguration(), reload(other).getConfiguration());
        assertEquals(base.getPrice(), other.getPrice());
    }
    @Test void reservationForeignKeysAreEnforced() {
        var saved = persist(false, Set.of());
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "UPDATE tour_reservation SET customer_id = ? WHERE id = ?", Long.MAX_VALUE, saved.getId()));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "UPDATE tour_reservation SET tour_schedule_id = ? WHERE id = ?", Long.MAX_VALUE, saved.getId()));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO tour_reservation_extra_option (reservation_id, extra_option) VALUES (?, 'COFFEE')", Long.MAX_VALUE));
    }
    @Test void duplicateReservationExtraOptionCannotBeStored() {
        var saved = persist(false, Set.of(ExtraOption.COFFEE));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO tour_reservation_extra_option (reservation_id, extra_option) VALUES (?, 'COFFEE')", saved.getId()));
    }
    @Test void databaseRejectsIncompleteAndUnsupportedDiscountColumns() {
        var saved = persist(false, Set.of());
        for (String assignment : List.of("discount_type = 'LOYALTY'", "discount_rate_percent = 5", "discount_amount = 0",
                "discount_type = 'LOYALTY', discount_rate_percent = 5, discount_amount = NULL",
                "discount_type = NULL, discount_rate_percent = 5, discount_amount = 0",
                "discount_type = 'LOYALTY', discount_rate_percent = NULL, discount_amount = 0",
                "discount_type = 'OTHER', discount_rate_percent = 5, discount_amount = 0",
                "discount_type = 'LOYALTY', discount_rate_percent = 10, discount_amount = 0",
                "discount_type = 'LOYALTY', discount_rate_percent = 5, discount_amount = -1")) {
            assertThrows(DataIntegrityViolationException.class,
                    () -> jdbc.update("UPDATE tour_reservation SET " + assignment + " WHERE id = ?", saved.getId()), assignment);
        }
    }
    @Test void databaseRejectsInvalidPartyMoneyDatesAndCurrency() {
        var saved = persist(false, Set.of());
        for (String assignment : List.of("participant_count = 0", "participant_count = 11", "unit_price = 0",
                "subtotal = 0", "total = -1", "total = 1", "currency = 'USD'",
                "schedule_start_date_snapshot = '2026-12-01'")) {
            assertThrows(DataIntegrityViolationException.class,
                    () -> jdbc.update("UPDATE tour_reservation SET " + assignment + " WHERE id = ?", saved.getId()), assignment);
        }
    }
    @Test void databaseRestrictsOptionsToCanonicalValues() {
        var saved = persist(false, Set.of());
        for (String column : List.of("style", "hotel_option", "transport_option", "meal_option", "tour_product_theme_snapshot")) {
            assertThrows(DataIntegrityViolationException.class,
                    () -> jdbc.update("UPDATE tour_reservation SET " + column + " = 'UNKNOWN' WHERE id = ?", saved.getId()));
        }
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO tour_reservation_extra_option (reservation_id, extra_option) VALUES (?, 'UNKNOWN')", saved.getId()));
    }
    @Test void snapshotFactoryRejectsMismatchedPriceAndInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> ReservationJpaEntity.capture(customer, schedule, 4,
                configuration(Set.of()), ReservationPriceCalculator.calculate(100, 2, false)));
        var smallCar = TourConfiguration.create(TourStyle.GRAND, HotelOption.HOTEL_4_STAR,
                TransportOption.PRIVATE_LUXURY_CAR_2, MealOption.LOCAL_RESTAURANT, List.of());
        assertThrows(IllegalArgumentException.class, () -> ReservationJpaEntity.capture(customer, schedule, 4,
                smallCar, ReservationPriceCalculator.calculate(100, 4, false)));
    }
    @Test void migrationV4AppliesAndHibernateValidationSucceeds() {
        assertEquals(4, flyway.info().applied().length);
        assertEquals("4", flyway.info().current().getVersion().getVersion());
        assertTrue(flyway.validateWithResult().validationSuccessful);
        assertEquals(0, flyway.info().pending().length);
        assertNotNull(entityManager.getMetamodel().entity(ReservationJpaEntity.class));
        assertEquals(0, reservations.count());
    }

    private ReservationJpaEntity persist(boolean loyalty, Set<ExtraOption> extras) {
        return reservations.saveAndFlush(ReservationJpaEntity.capture(customer, schedule, 4,
                configuration(extras), ReservationPriceCalculator.calculate(1_000_000, 4, loyalty)));
    }
    private ReservationJpaEntity reload(ReservationJpaEntity reservation) {
        entityManager.flush();
        entityManager.clear();
        return reservations.findById(reservation.getId()).orElseThrow();
    }
    private TourConfiguration configuration(Set<ExtraOption> extras) {
        return TourConfiguration.create(TourStyle.GRAND, HotelOption.HOTEL_4_STAR,
                TransportOption.PREMIUM_VAN_10, MealOption.LOCAL_RESTAURANT, extras);
    }
}
