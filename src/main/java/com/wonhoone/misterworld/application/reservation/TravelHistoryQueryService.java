package com.wonhoone.misterworld.application.reservation;

import com.wonhoone.misterworld.api.dto.TravelHistoryResponse;
import com.wonhoone.misterworld.application.time.BusinessDateProvider;
import com.wonhoone.misterworld.infrastructure.persistence.repository.ReservationJpaRepository;
import com.wonhoone.misterworld.infrastructure.persistence.repository.ReservationJpaRepository.TravelHistoryRow;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TravelHistoryQueryService {
    private final ReservationJpaRepository reservations;
    private final BusinessDateProvider businessDate;

    public TravelHistoryQueryService(ReservationJpaRepository reservations, BusinessDateProvider businessDate) {
        this.reservations = reservations;
        this.businessDate = businessDate;
    }

    public List<TravelHistoryResponse> list(long customerId) {
        LocalDate today = businessDate.today();
        return reservations.findTravelHistory(customerId, today).stream().map(this::response).toList();
    }

    private TravelHistoryResponse response(TravelHistoryRow row) {
        return new TravelHistoryResponse(row.getReservationId(),
                new TravelHistoryResponse.TourProductSummary(row.getTourProductId(), row.getTourProductTheme(),
                        row.getTourProductName()),
                row.getStartDate(), row.getEndDate(), row.getStyle(),
                new TravelHistoryResponse.Price(row.getPriceAmount(), row.getCurrency()));
    }
}
