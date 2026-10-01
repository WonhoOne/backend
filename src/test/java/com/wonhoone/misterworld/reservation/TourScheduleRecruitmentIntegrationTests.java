package com.wonhoone.misterworld.reservation;

import com.wonhoone.misterworld.domain.*;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TourScheduleRecruitmentIntegrationTests extends ReservationIntegrationSupport {
    @Test void generalScheduleStaysUnconfirmedBelowThreeParticipants() {
        var result = create(schedule, 2);
        assertThat(result.scheduleJustConfirmed()).isFalse();
        assertThat(result.response().schedule().recruitment().currentCount()).isEqualTo(2);
        assertThat(schedules.findById(schedule.getId()).orElseThrow().isConfirmed()).isFalse();
    }
    @Test void generalScheduleConfirmsAtThreeParticipants() {
        create(schedule, 2);
        var result = create(schedule, 1);
        assertThat(result.scheduleJustConfirmed()).isTrue();
        assertThat(result.response().schedule().recruitment().currentCount()).isEqualTo(3);
        assertThat(schedules.findById(schedule.getId()).orElseThrow().isConfirmed()).isTrue();
    }
    @Test void firstGeneralReservationCanExceedConfirmationThreshold() {
        var result = create(schedule, 5);
        assertThat(result.scheduleJustConfirmed()).isTrue();
        assertThat(result.response().schedule().recruitment().currentCount()).isEqualTo(5);
    }
    @Test void alreadyConfirmedGeneralScheduleDoesNotTransitionAgain() {
        create(schedule, 3);
        var later = create(schedule, 2);
        assertThat(later.scheduleJustConfirmed()).isFalse();
        assertThat(later.response().schedule().recruitment().confirmed()).isTrue();
    }
    @Test void oneTwoPersonHoneymoonReservationCountsAsOneCouple() {
        var target = honeymoon();
        var result = create(target, 2);
        assertThat(result.response().schedule().recruitment().currentCount()).isEqualTo(1);
        assertThat(result.scheduleJustConfirmed()).isFalse();
        assertThat(schedules.findById(target.getId()).orElseThrow().isConfirmed()).isFalse();
    }
    @Test void twoTwoPersonHoneymoonReservationsConfirmSchedule() {
        var target = honeymoon();
        create(target, 2);
        var result = create(target, 2);
        assertThat(result.response().schedule().recruitment().currentCount()).isEqualTo(2);
        assertThat(result.scheduleJustConfirmed()).isTrue();
        assertThat(schedules.findById(target.getId()).orElseThrow().isConfirmed()).isTrue();
    }
    @Test void singleFourPersonHoneymoonReservationCanConfirm() {
        var result = create(honeymoon(), 4);
        assertThat(result.response().schedule().recruitment().currentCount()).isEqualTo(2);
        assertThat(result.scheduleJustConfirmed()).isTrue();
    }
    @Test void sixPersonHoneymoonReservationCountsAsThreeCouples() {
        var result = create(honeymoon(), 6);
        assertThat(result.response().schedule().recruitment().currentCount()).isEqualTo(3);
        assertThat(result.scheduleJustConfirmed()).isTrue();
    }
    @Test void alreadyConfirmedHoneymoonScheduleDoesNotTransitionAgain() {
        var target = honeymoon();
        create(target, 4);
        var result = create(target, 2);
        assertThat(result.scheduleJustConfirmed()).isFalse();
        assertThat(result.response().schedule().recruitment().confirmed()).isTrue();
    }
    @Test void scheduleDetailUsesPersistedGeneralParticipantSum() throws Exception {
        create(schedule, 2);
        create(schedule, 1);
        http.perform(get("/api/v1/tour-schedules/{id}", schedule.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.recruitment.unit").value("PARTICIPANT"))
                .andExpect(jsonPath("$.recruitment.currentCount").value(3));
    }
    @Test void honeymoonScheduleDetailUsesPersistedCoupleTeams() throws Exception {
        var target = honeymoon();
        create(target, 6);
        http.perform(get("/api/v1/tour-schedules/{id}", target.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.recruitment.unit").value("COUPLE_TEAM"))
                .andExpect(jsonPath("$.recruitment.currentCount").value(3));
    }
    @Test void scheduleListUsesRealCountsForMixedThemesAndZeroForEmptySchedule() throws Exception {
        var honeymoon = honeymoon();
        var empty = schedule(Theme.OUTDOOR_TREKKING, LocalDate.of(2026, 12, 1), false);
        create(schedule, 2);
        create(honeymoon, 4);
        http.perform(get("/api/v1/tour-schedules")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].recruitment.currentCount").value(2))
                .andExpect(jsonPath("$[0].recruitment.confirmed").value(false))
                .andExpect(jsonPath("$[1].recruitment.currentCount").value(2))
                .andExpect(jsonPath("$[1].recruitment.confirmed").value(true))
                .andExpect(jsonPath("$[2].id").value(empty.getId()))
                .andExpect(jsonPath("$[2].recruitment.currentCount").value(0));
    }
    @Test void filteredScheduleCollectionUsesRealAggregates() throws Exception {
        create(schedule, 4);
        create(honeymoon(), 2);
        http.perform(get("/api/v1/tour-schedules").param("tourId", schedule.getTourProduct().getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].recruitment.currentCount").value(4));
    }
    @Test void confirmedProjectionUsesPersistedConfirmationState() throws Exception {
        jdbc.update("UPDATE tour_schedule SET confirmed = TRUE WHERE id = ?", schedule.getId());
        http.perform(get("/api/v1/tour-schedules/{id}", schedule.getId()))
                .andExpect(jsonPath("$.recruitment.currentCount").value(0))
                .andExpect(jsonPath("$.recruitment.confirmed").value(true));
    }
    @Test void confirmedFutureScheduleRemainsReservable() throws Exception {
        create(schedule, 3);
        http.perform(get("/api/v1/tour-schedules/{id}", schedule.getId()))
                .andExpect(jsonPath("$.reservable").value(true)).andExpect(jsonPath("$.recruitment.confirmed").value(true));
    }
    @Test void aggregateHasNoPerReservationMaximumClamp() throws Exception {
        create(schedule, 10);
        create(schedule, 7);
        assertThat(reservations.totalParticipantsForSchedule(schedule.getId())).isEqualTo(17);
        http.perform(get("/api/v1/tour-schedules/{id}", schedule.getId()))
                .andExpect(jsonPath("$.recruitment.currentCount").value(17));
    }
    @Test void historicalHoneymoonDetailShowsLaterCurrentRecruitment() throws Exception {
        var target = honeymoon();
        long firstId = postId(request(target, 2));
        create(target, 2);
        detail(firstId).andExpect(jsonPath("$.schedule.recruitment.currentCount").value(2))
                .andExpect(jsonPath("$.schedule.recruitment.confirmed").value(true));
    }
    private com.wonhoone.misterworld.infrastructure.persistence.entity.TourScheduleJpaEntity honeymoon() {
        return schedule(Theme.HONEYMOON_ROMANCE, LocalDate.of(2026, 11, 1), false);
    }
}
