package com.wonhoone.misterworld.application.tour;

import com.wonhoone.misterworld.api.dto.TourScheduleResponse.Recruitment;
import com.wonhoone.misterworld.domain.TourScheduleRecruitmentPolicy;
import com.wonhoone.misterworld.domain.Theme;

public final class ScheduleRecruitmentProjection {
    private ScheduleRecruitmentProjection() {}

    // currentCount is already aggregated in the theme's unit; persisted confirmation remains authoritative.
    public static Recruitment create(Theme theme, long currentCount, boolean confirmed) {
        return new Recruitment(TourScheduleRecruitmentPolicy.unit(theme), currentCount,
                TourScheduleRecruitmentPolicy.requiredCount(theme), confirmed);
    }
}
