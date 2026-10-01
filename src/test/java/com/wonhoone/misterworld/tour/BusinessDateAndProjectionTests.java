package com.wonhoone.misterworld.tour;

import com.wonhoone.misterworld.application.time.BusinessDateProvider;
import com.wonhoone.misterworld.application.tour.ScheduleRecruitmentProjection;
import com.wonhoone.misterworld.config.BusinessTimeConfig;
import com.wonhoone.misterworld.domain.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class BusinessDateAndProjectionTests {
    @Test void businessDateUsesConfiguredClockZoneAcrossUtcMidnightBoundary() {
        var instant = Instant.parse("2026-09-30T15:00:00Z");
        var seoul = new BusinessDateProvider(Clock.fixed(instant, ZoneId.of("Asia/Seoul")));
        var utc = new BusinessDateProvider(Clock.fixed(instant, ZoneOffset.UTC));
        assertThat(seoul.today()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(utc.today()).isEqualTo(LocalDate.of(2026, 9, 30));
    }

    @Test void businessClockSupportsBackendLocalTimeZoneOverride() {
        assertThat(new BusinessTimeConfig().businessClock("UTC").getZone()).isEqualTo(ZoneId.of("UTC"));
        assertThat(new BusinessTimeConfig().businessClock("Asia/Seoul").getZone()).isEqualTo(ZoneId.of("Asia/Seoul"));
    }

    @Test void reservabilityRequiresDepartureStrictlyAfterBusinessDate() {
        LocalDate today = LocalDate.of(2026, 10, 1);
        assertThat(TourScheduleReservabilityPolicy.isReservable(today.plusDays(1), today)).isTrue();
        assertThat(TourScheduleReservabilityPolicy.isReservable(today, today)).isFalse();
        assertThat(TourScheduleReservabilityPolicy.isReservable(today.minusDays(1), today)).isFalse();
    }

    @Test void recruitmentProjectionAcceptsFutureReservationAggregatesWithoutRecomputingConfirmation() {
        var honeymoon = ScheduleRecruitmentProjection.create(Theme.HONEYMOON_ROMANCE, 4, false);
        assertThat(honeymoon.unit()).isEqualTo(RecruitmentUnit.COUPLE_TEAM);
        assertThat(honeymoon.currentCount()).isEqualTo(4);
        assertThat(honeymoon.confirmed()).isFalse();
        var general = ScheduleRecruitmentProjection.create(Theme.GOLF_CHALLENGE, 7, true);
        assertThat(general.unit()).isEqualTo(RecruitmentUnit.PARTICIPANT);
        assertThat(general.currentCount()).isEqualTo(7);
        assertThat(general.confirmed()).isTrue();
    }
}
