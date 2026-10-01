package com.wonhoone.misterworld.infrastructure.persistence;

import com.wonhoone.misterworld.application.reservation.LoyaltyEligibilityService;
import com.wonhoone.misterworld.domain.*;
import com.wonhoone.misterworld.infrastructure.persistence.entity.*;
import com.wonhoone.misterworld.infrastructure.persistence.repository.*;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:loyalty;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@ActiveProfiles("test")
@Transactional
class LoyaltyEligibilityTests {
    @TestBean(name = "businessClock", methodName = "fixedBusinessClock") Clock clock;
    static Clock fixedBusinessClock() {
        return Clock.fixed(Instant.parse("2026-09-30T15:00:00Z"), ZoneId.of("Asia/Seoul"));
    }
    @Autowired LoyaltyEligibilityService loyalty;
    @Autowired ReservationJpaRepository reservations;
    @Autowired UserAccountJpaRepository accounts;
    @Autowired TourProductJpaRepository products;
    @Autowired TourScheduleJpaRepository schedules;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;
    UserAccountJpaEntity customer;
    TourProductJpaEntity product;

    @BeforeEach void fixtures() {
        customer = account("loyalty-owner");
        product = products.saveAndFlush(new TourProductJpaEntity(Theme.GOLF_CHALLENGE, "Trip", "Description"));
    }
    @Test void confirmedTripEndingYesterdayMakesCustomerEligible() {
        trip(customer, "2026-09-30", true);
        assertTrue(loyalty.isEligible(customer.getId()));
    }
    @Test void confirmedTripEndingOnBusinessDateIsNotCompleted() {
        trip(customer, "2026-10-01", true);
        assertFalse(loyalty.isEligible(customer.getId()));
    }
    @Test void confirmedFutureTripIsNotCompleted() {
        trip(customer, "2026-10-02", true);
        assertFalse(loyalty.isEligible(customer.getId()));
    }
    @Test void unconfirmedPastTripDoesNotQualify() {
        trip(customer, "2026-09-30", false);
        assertFalse(loyalty.isEligible(customer.getId()));
    }
    @Test void anotherCustomersCompletedTripDoesNotQualifyCurrentCustomer() {
        trip(account("other-owner"), "2026-09-30", true);
        assertFalse(loyalty.isEligible(customer.getId()));
    }
    @Test void oneCompletedTripIsEnoughAmongIncompleteTrips() {
        trip(customer, "2026-10-01", true);
        trip(customer, "2026-09-30", false);
        trip(customer, "2026-09-30", true);
        assertTrue(loyalty.isEligible(customer.getId()));
    }
    @Test void customerWithoutPreviousReservationsIsNotEligible() {
        assertFalse(loyalty.isEligible(customer.getId()));
    }
    @Test void laterScheduleConfirmationQualifiesExistingHistoricalReservation() {
        var reservation = trip(customer, "2026-09-30", false);
        assertFalse(loyalty.isEligible(customer.getId()));
        jdbc.update("UPDATE tour_schedule SET confirmed = TRUE WHERE id = ?", reservation.getTourSchedule().getId());
        entityManager.clear();
        assertTrue(loyalty.isEligible(customer.getId()));
    }
    @Test void loyaltyUsesHistoricalEndDateAfterCurrentScheduleDateChanges() {
        var reservation = trip(customer, "2026-09-30", true);
        jdbc.update("UPDATE tour_schedule SET start_date = ?, end_date = ? WHERE id = ?",
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 5), reservation.getTourSchedule().getId());
        entityManager.clear();
        assertTrue(loyalty.isEligible(customer.getId()));
    }
    @Test void laterCurrentEndDateCannotMakeSameDaySnapshotCompleted() {
        var reservation = trip(customer, "2026-10-01", true);
        jdbc.update("UPDATE tour_schedule SET start_date = ?, end_date = ? WHERE id = ?",
                LocalDate.of(2026, 9, 28), LocalDate.of(2026, 9, 30), reservation.getTourSchedule().getId());
        entityManager.clear();
        assertFalse(loyalty.isEligible(customer.getId()));
    }
    @Test void invalidCustomerIdentityIsRejectedInternally() {
        assertThrows(IllegalArgumentException.class, () -> loyalty.isEligible(0));
    }

    private UserAccountJpaEntity account(String loginId) {
        return accounts.saveAndFlush(new UserAccountJpaEntity(loginId, "test-hash", "A", "B", "C", UserRole.CUSTOMER));
    }
    private ReservationJpaEntity trip(UserAccountJpaEntity owner, String end, boolean confirmed) {
        var endDate = LocalDate.parse(end);
        var schedule = schedules.saveAndFlush(new TourScheduleJpaEntity(product, endDate.minusDays(2), endDate, confirmed));
        var configuration = TourConfiguration.create(TourStyle.GRAND, HotelOption.HOTEL_4_STAR,
                TransportOption.PREMIUM_VAN_10, MealOption.LOCAL_RESTAURANT, List.of());
        return reservations.saveAndFlush(ReservationJpaEntity.capture(owner, schedule, 2,
                configuration, ReservationPriceCalculator.calculate(100, 2, false)));
    }
}
