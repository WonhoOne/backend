package com.wonhoone.misterworld.application.reservation;

import com.wonhoone.misterworld.application.time.BusinessDateProvider;
import com.wonhoone.misterworld.infrastructure.persistence.repository.ReservationJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class LoyaltyEligibilityService {
    private final ReservationJpaRepository reservations;
    private final BusinessDateProvider businessDate;

    public LoyaltyEligibilityService(ReservationJpaRepository reservations, BusinessDateProvider businessDate) {
        this.reservations = reservations;
        this.businessDate = businessDate;
    }

    /** B5 must call this before saving the new reservation in its create transaction. */
    public boolean isEligible(long customerId) {
        if (customerId <= 0) throw new IllegalArgumentException("customerId must be positive");
        return reservations.countCompletedTrips(customerId, businessDate.today()) > 0;
    }
}
