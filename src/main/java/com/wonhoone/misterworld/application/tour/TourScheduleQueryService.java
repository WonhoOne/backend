package com.wonhoone.misterworld.application.tour;

import com.wonhoone.misterworld.api.dto.TourScheduleResponse;
import com.wonhoone.misterworld.api.error.*;
import com.wonhoone.misterworld.application.time.BusinessDateProvider;
import com.wonhoone.misterworld.domain.TourScheduleReservabilityPolicy;
import com.wonhoone.misterworld.domain.TourScheduleRecruitmentPolicy;
import com.wonhoone.misterworld.infrastructure.persistence.entity.TourScheduleJpaEntity;
import com.wonhoone.misterworld.infrastructure.persistence.repository.TourScheduleJpaRepository;
import com.wonhoone.misterworld.infrastructure.persistence.repository.ReservationJpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TourScheduleQueryService {
    private final TourScheduleJpaRepository schedules;
    private final BusinessDateProvider businessDate;
    private final ReservationJpaRepository reservations;

    public TourScheduleQueryService(TourScheduleJpaRepository schedules, BusinessDateProvider businessDate,
                                    ReservationJpaRepository reservations) {
        this.schedules = schedules;
        this.businessDate = businessDate;
        this.reservations = reservations;
    }

    public List<TourScheduleResponse> list(Long tourId) {
        if (tourId != null) InvalidRequestParameterException.requirePositive(tourId);
        var result = tourId == null ? schedules.findAllByOrderByStartDateAscIdAsc()
                : schedules.findByTourProductIdOrderByStartDateAscIdAsc(tourId);
        // Use one business date for the entire response, including requests crossing midnight.
        var today = businessDate.today();
        if (result.isEmpty()) return List.of();
        var scheduleIds = result.stream().map(TourScheduleJpaEntity::getId).toList();
        var participantsBySchedule = reservations.totalParticipantsByScheduleIds(scheduleIds).stream()
                .collect(Collectors.toMap(ReservationJpaRepository.ScheduleParticipantTotal::getScheduleId,
                        ReservationJpaRepository.ScheduleParticipantTotal::getParticipantTotal));
        return result.stream().map(schedule -> project(schedule, today,
                participantsBySchedule.getOrDefault(schedule.getId(), 0L))).toList();
    }

    public TourScheduleResponse detail(long scheduleId) {
        InvalidRequestParameterException.requirePositive(scheduleId);
        var schedule = schedules.findById(scheduleId).orElseThrow(() ->
                new ResourceNotFoundException("TOUR_SCHEDULE_NOT_FOUND", "Tour schedule was not found."));
        return project(schedule, businessDate.today(), reservations.totalParticipantsForSchedule(scheduleId));
    }

    private TourScheduleResponse project(TourScheduleJpaEntity schedule, LocalDate today, long participantTotal) {
        var product = schedule.getTourProduct();
        long currentCount = TourScheduleRecruitmentPolicy.currentCountFromParticipants(product.getTheme(), participantTotal);
        return new TourScheduleResponse(schedule.getId(), product.getId(), schedule.getStartDate(),
                schedule.getEndDate(), TourScheduleReservabilityPolicy.isReservable(schedule.getStartDate(), today),
                ScheduleRecruitmentProjection.create(product.getTheme(), currentCount, schedule.isConfirmed()));
    }
}
