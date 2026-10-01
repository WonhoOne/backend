package com.wonhoone.misterworld.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TourScheduleRecruitmentPolicyTests {
    @Test void generalThemesCountParticipantsAndRequireThree() {
        for (var theme : new Theme[]{Theme.GOLF_CHALLENGE, Theme.OUTDOOR_TREKKING, Theme.PARENTS_HEALING}) {
            assertEquals(RecruitmentUnit.PARTICIPANT, TourScheduleRecruitmentPolicy.unit(theme));
            assertEquals(17, TourScheduleRecruitmentPolicy.currentCountFromParticipants(theme, 17));
            assertEquals(3, TourScheduleRecruitmentPolicy.requiredCount(theme));
        }
    }
    @Test void honeymoonCountsCoupleTeamsAndRequiresTwo() {
        assertEquals(RecruitmentUnit.COUPLE_TEAM, TourScheduleRecruitmentPolicy.unit(Theme.HONEYMOON_ROMANCE));
        assertEquals(6, TourScheduleRecruitmentPolicy.currentCountFromParticipants(Theme.HONEYMOON_ROMANCE, 12));
        assertEquals(2, TourScheduleRecruitmentPolicy.requiredCount(Theme.HONEYMOON_ROMANCE));
    }
    @Test void thresholdUsesGreaterThanOrEqualRatherThanExactEquality() {
        assertFalse(TourScheduleRecruitmentPolicy.thresholdReached(Theme.GOLF_CHALLENGE, 2));
        assertTrue(TourScheduleRecruitmentPolicy.thresholdReached(Theme.GOLF_CHALLENGE, 3));
        assertTrue(TourScheduleRecruitmentPolicy.thresholdReached(Theme.GOLF_CHALLENGE, 5));
        assertFalse(TourScheduleRecruitmentPolicy.thresholdReached(Theme.HONEYMOON_ROMANCE, 1));
        assertTrue(TourScheduleRecruitmentPolicy.thresholdReached(Theme.HONEYMOON_ROMANCE, 2));
        assertTrue(TourScheduleRecruitmentPolicy.thresholdReached(Theme.HONEYMOON_ROMANCE, 3));
    }
    @Test void oddPersistedHoneymoonAggregateFailsWithoutTruncation() {
        assertThrows(IllegalStateException.class, () -> TourScheduleRecruitmentPolicy.currentCountFromParticipants(Theme.HONEYMOON_ROMANCE, 3));
    }
    @Test void aggregateCanExceedIntegerRangeWithoutClamping() {
        assertEquals(3_000_000_000L, TourScheduleRecruitmentPolicy.currentCountFromParticipants(Theme.GOLF_CHALLENGE, 3_000_000_000L));
    }
    @Test void negativePersistedAggregateFailsAsAnInternalInvariant() {
        assertThrows(IllegalStateException.class, () -> TourScheduleRecruitmentPolicy.currentCountFromParticipants(Theme.GOLF_CHALLENGE, -1));
    }
}
