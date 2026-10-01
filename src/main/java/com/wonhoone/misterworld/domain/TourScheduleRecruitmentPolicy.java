package com.wonhoone.misterworld.domain;

import java.util.Objects;

public final class TourScheduleRecruitmentPolicy {
    private TourScheduleRecruitmentPolicy() {}

    public static RecruitmentUnit unit(Theme theme) {
        Objects.requireNonNull(theme, "theme must not be null");
        return theme == Theme.HONEYMOON_ROMANCE ? RecruitmentUnit.COUPLE_TEAM : RecruitmentUnit.PARTICIPANT;
    }

    public static int requiredCount(Theme theme) {
        return unit(theme) == RecruitmentUnit.COUPLE_TEAM ? 2 : 3;
    }

    public static long currentCountFromParticipants(Theme theme, long participantTotal) {
        if (participantTotal < 0) throw new IllegalStateException("Participant aggregate must be nonnegative");
        if (unit(theme) == RecruitmentUnit.COUPLE_TEAM) {
            // Truncating an odd aggregate would hide a broken persisted Honeymoon invariant.
            if (participantTotal % 2 != 0) throw new IllegalStateException("Honeymoon aggregate must be even");
            return participantTotal / 2;
        }
        return participantTotal;
    }

    public static boolean thresholdReached(Theme theme, long currentCount) {
        return currentCount >= requiredCount(theme);
    }
}
