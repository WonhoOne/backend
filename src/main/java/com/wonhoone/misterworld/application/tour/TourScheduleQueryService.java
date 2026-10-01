package com.wonhoone.misterworld.application.tour;

import com.wonhoone.misterworld.api.dto.TourScheduleResponse;
import com.wonhoone.misterworld.api.error.*;
import com.wonhoone.misterworld.application.time.BusinessDateProvider;
import com.wonhoone.misterworld.domain.TourScheduleReservabilityPolicy;
import com.wonhoone.misterworld.infrastructure.persistence.entity.TourScheduleJpaEntity;
import com.wonhoone.misterworld.infrastructure.persistence.repository.TourScheduleJpaRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TourScheduleQueryService {
    private final TourScheduleJpaRepository schedules;
    private final BusinessDateProvider businessDate;

    public TourScheduleQueryService(TourScheduleJpaRepository schedules, BusinessDateProvider businessDate) {
        this.schedules = schedules;
        this.businessDate = businessDate;
    }

    public List<TourScheduleResponse> list(Long tourId) {
        if (tourId != null) InvalidRequestParameterException.requirePositive(tourId);
        var result = tourId == null ? schedules.findAllByOrderByStartDateAscIdAsc()
                : schedules.findByTourProductIdOrderByStartDateAscIdAsc(tourId);
        // Use one business date for the entire response, including requests crossing midnight.
        var today = businessDate.today();
        return result.stream().map(schedule -> project(schedule, today)).toList();
    }

    public TourScheduleResponse detail(long scheduleId) {
        InvalidRequestParameterException.requirePositive(scheduleId);
        var schedule = schedules.findById(scheduleId).orElseThrow(() ->
                new ResourceNotFoundException("TOUR_SCHEDULE_NOT_FOUND", "Tour schedule was not found."));
        return project(schedule, businessDate.today());
    }

    private TourScheduleResponse project(TourScheduleJpaEntity schedule, LocalDate today) {
        var product = schedule.getTourProduct();
        // B3 has no persisted Reservation feature. B5 supplies real aggregate counts at this boundary.
        long currentCount = 0;
        return new TourScheduleResponse(schedule.getId(), product.getId(), schedule.getStartDate(),
                schedule.getEndDate(), TourScheduleReservabilityPolicy.isReservable(schedule.getStartDate(), today),
                ScheduleRecruitmentProjection.create(product.getTheme(), currentCount, schedule.isConfirmed()));
    }
}
