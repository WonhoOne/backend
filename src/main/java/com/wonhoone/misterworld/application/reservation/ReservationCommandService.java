package com.wonhoone.misterworld.application.reservation;

import com.wonhoone.misterworld.api.dto.ReservationCreateRequest;
import com.wonhoone.misterworld.api.error.*;
import com.wonhoone.misterworld.application.time.BusinessDateProvider;
import com.wonhoone.misterworld.application.sms.ScheduleConfirmationOutboxService;
import com.wonhoone.misterworld.application.tour.ScheduleRecruitmentProjection;
import com.wonhoone.misterworld.domain.*;
import com.wonhoone.misterworld.infrastructure.persistence.entity.ReservationJpaEntity;
import com.wonhoone.misterworld.infrastructure.persistence.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationCommandService {
    private final UserAccountJpaRepository accounts;
    private final TourScheduleJpaRepository schedules;
    private final BusinessDateProvider businessDate;
    private final ReservationCreateValidator validator;
    private final TourProductStylePriceJpaRepository prices;
    private final LoyaltyEligibilityService loyalty;
    private final ReservationJpaRepository reservations;
    private final ReservationResponseFactory responses;
    private final ScheduleConfirmationOutboxService outbox;

    public ReservationCommandService(UserAccountJpaRepository accounts, TourScheduleJpaRepository schedules,
                                     BusinessDateProvider businessDate, ReservationCreateValidator validator,
                                     TourProductStylePriceJpaRepository prices, LoyaltyEligibilityService loyalty,
                                     ReservationJpaRepository reservations, ReservationResponseFactory responses,
                                     ScheduleConfirmationOutboxService outbox) {
        this.accounts = accounts;
        this.schedules = schedules;
        this.businessDate = businessDate;
        this.validator = validator;
        this.prices = prices;
        this.loyalty = loyalty;
        this.reservations = reservations;
        this.responses = responses;
        this.outbox = outbox;
    }

    // READ_COMMITTED prevents the account lookup from fixing a stale aggregate snapshot before lock waiting.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReservationCreationResult create(long customerId, ReservationCreateRequest request) {
        var customer = accounts.findById(customerId).orElseThrow(() ->
                new IllegalStateException("Authenticated customer account is missing"));
        if (customer.getRole() != UserRole.CUSTOMER) throw new IllegalStateException("Account must be a customer");

        // Serialize only this Schedule's creates, including aggregate and first-confirmation decisions.
        var schedule = schedules.findByIdForReservationUpdate(request.scheduleId()).orElseThrow(() ->
                new ResourceNotFoundException("TOUR_SCHEDULE_NOT_FOUND", "Tour schedule was not found."));
        var today = businessDate.today();
        if (!TourScheduleReservabilityPolicy.isReservable(schedule.getStartDate(), today)) {
            throw new ResourceConflictException("SCHEDULE_NOT_RESERVABLE", "Tour schedule is not reservable.");
        }

        var product = schedule.getTourProduct();
        var configuration = validator.validate(product.getTheme(), request);
        var unitPrice = prices.findByTourProductIdAndStyle(product.getId(), configuration.style())
                .orElseThrow(() -> new IllegalStateException("Selected product style price is missing")).getAmount();
        boolean loyaltyEligible = loyalty.isEligible(customerId);
        var price = ReservationPriceCalculator.calculate(unitPrice, request.participantCount(), loyaltyEligible);
        var reservation = ReservationJpaEntity.capture(customer, schedule, request.participantCount(), configuration, price);
        reservations.saveAndFlush(reservation);

        long participantTotal = reservations.totalParticipantsForSchedule(schedule.getId());
        long currentCount = TourScheduleRecruitmentPolicy.currentCountFromParticipants(product.getTheme(), participantTotal);
        boolean scheduleJustConfirmed = TourScheduleRecruitmentPolicy.thresholdReached(product.getTheme(), currentCount)
                && schedule.markConfirmed();
        schedules.flush();
        if (scheduleJustConfirmed) outbox.capture(schedule);

        var recruitment = ScheduleRecruitmentProjection.create(product.getTheme(), currentCount, schedule.isConfirmed());
        return new ReservationCreationResult(responses.create(reservation, recruitment), scheduleJustConfirmed);
    }
}
