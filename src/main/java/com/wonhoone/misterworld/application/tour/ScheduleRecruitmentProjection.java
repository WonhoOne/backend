package com.wonhoone.misterworld.application.tour;

import com.wonhoone.misterworld.api.dto.TourScheduleResponse.Recruitment;
import com.wonhoone.misterworld.domain.RecruitmentUnit;
import com.wonhoone.misterworld.domain.Theme;

public final class ScheduleRecruitmentProjection {
    private ScheduleRecruitmentProjection() {}

    // currentCount is already aggregated in the theme's unit; persisted confirmation remains authoritative.
    public static Recruitment create(Theme theme, long currentCount, boolean confirmed) {
        if (theme == Theme.HONEYMOON_ROMANCE) {
            return new Recruitment(RecruitmentUnit.COUPLE_TEAM, currentCount, 2, confirmed);
        }
        return new Recruitment(RecruitmentUnit.PARTICIPANT, currentCount, 3, confirmed);
    }
}
