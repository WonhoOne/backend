package com.wonhoone.misterworld.application.reservation;

import com.wonhoone.misterworld.api.dto.ReservationConfigurationUpdateRequest;
import com.wonhoone.misterworld.api.dto.ReservationCreateRequest;
import com.wonhoone.misterworld.api.dto.ReservationResponse;
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
        if (schedule.getOperationStatus() != ScheduleOperationStatus.SCHEDULED
                || !TourScheduleReservabilityPolicy.isReservable(schedule.getStartDate(), today)) {
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

    /**
     * Customer modification of style, hotel, transport, meal and extras before departure.
     * Participant count and schedule are fixed, so recruitment and SMS confirmation are not touched.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReservationResponse updateConfiguration(long customerId, long reservationId,
                                                   ReservationConfigurationUpdateRequest request) {
        InvalidRequestParameterException.requirePositive(reservationId);
        var owned = reservations.findOwnedReservation(reservationId, customerId).orElseThrow(() ->
                new ResourceNotFoundException("RESERVATION_NOT_FOUND", "Reservation was not found."));
        // Same lock as create and departure: a modification cannot interleave with the schedule departing.
        var schedule = schedules.findByIdForReservationUpdate(owned.getTourSchedule().getId()).orElseThrow(() ->
                new IllegalStateException("Reservation schedule is missing"));
        if (schedule.getOperationStatus() != ScheduleOperationStatus.SCHEDULED
                || !TourScheduleReservabilityPolicy.isReservable(schedule.getStartDate(), businessDate.today())) {
            throw new ResourceConflictException("RESERVATION_NOT_MODIFIABLE",
                    "This reservation can no longer be modified.");
        }

        var product = schedule.getTourProduct();
        var configuration = validator.validate(product.getTheme(), owned.getParticipantCount(), request.configuration());
        var unitPrice = prices.findByTourProductIdAndStyle(product.getId(), configuration.style())
                .orElseThrow(() -> new IllegalStateException("Selected product style price is missing")).getAmount();
        // A loyalty discount granted at booking stays with the reservation; it is not re-evaluated.
        boolean loyaltyGranted = owned.getPrice().discount() != null;
        var price = ReservationPriceCalculator.calculate(unitPrice, owned.getParticipantCount(), loyaltyGranted);
        owned.reconfigure(configuration, price);
        reservations.saveAndFlush(owned);

        long participantTotal = reservations.totalParticipantsForSchedule(schedule.getId());
        long currentCount = TourScheduleRecruitmentPolicy.currentCountFromParticipants(product.getTheme(), participantTotal);
        var recruitment = ScheduleRecruitmentProjection.create(product.getTheme(), currentCount, schedule.isConfirmed());
        return responses.create(owned, recruitment);
    }
}
