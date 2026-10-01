package com.wonhoone.misterworld.api.dto;

import com.wonhoone.misterworld.domain.RecruitmentUnit;
import java.time.LocalDate;

public record TourScheduleResponse(Long id, Long tourId, LocalDate startDate, LocalDate endDate,
                                   boolean reservable, Recruitment recruitment) {
    public record Recruitment(RecruitmentUnit unit, long currentCount, int requiredCount, boolean confirmed) {}
}
