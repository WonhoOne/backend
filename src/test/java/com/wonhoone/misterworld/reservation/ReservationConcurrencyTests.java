package com.wonhoone.misterworld.reservation;

import com.wonhoone.misterworld.application.reservation.ReservationCreationResult;
import com.wonhoone.misterworld.domain.Theme;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(30)
class ReservationConcurrencyTests extends ReservationIntegrationSupport {
    @Test void concurrentReservationsOnSameScheduleProduceCorrectAggregate() throws Exception {
        var results = concurrentCreates();
        assertThat(reservations.count()).isEqualTo(2);
        assertThat(reservations.totalParticipantsForSchedule(schedule.getId())).isEqualTo(4);
        assertThat(results.stream().map(result -> result.response().schedule().recruitment().currentCount()))
                .containsExactlyInAnyOrder(2L, 4L);
        assertThat(schedules.findById(schedule.getId()).orElseThrow().isConfirmed()).isTrue();
    }
    @Test void concurrentThresholdCrossingHasExactlyOneConfirmationTransition() throws Exception {
        var results = concurrentCreates();
        assertThat(results.stream().filter(ReservationCreationResult::scheduleJustConfirmed).count()).isEqualTo(1);
        assertThat(results.stream().filter(result -> !result.scheduleJustConfirmed()).count()).isEqualTo(1);
        assertThat(reservations.totalParticipantsForSchedule(schedule.getId())).isEqualTo(4);
    }
    @Test void heldScheduleLockBlocksSameScheduleCreateUntilEarlierReservationCommits() throws Exception {
        var acquired = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var secondStarted = new CountDownLatch(1);
        var workers = Executors.newFixedThreadPool(2);
        try {
            var first = workers.submit(() -> writeTransaction().execute(status -> {
                schedules.findByIdForReservationUpdate(schedule.getId()).orElseThrow();
                acquired.countDown();
                await(release);
                return create(schedule, 2);
            }));
            assertTrue(acquired.await(5, TimeUnit.SECONDS));
            var second = workers.submit(() -> {
                secondStarted.countDown();
                return create(schedule, 2);
            });
            assertTrue(secondStarted.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> second.get(300, TimeUnit.MILLISECONDS));
            release.countDown();
            assertFalse(first.get(10, TimeUnit.SECONDS).scheduleJustConfirmed());
            var result = second.get(10, TimeUnit.SECONDS);
            assertTrue(result.scheduleJustConfirmed());
            assertThat(result.response().schedule().recruitment().currentCount()).isEqualTo(4);
            assertThat(reservations.count()).isEqualTo(2);
            assertThat(schedules.findById(schedule.getId()).orElseThrow().isConfirmed()).isTrue();
        } finally {
            release.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
    @Test void differentScheduleCreateCompletesWhileAnotherScheduleIsLocked() throws Exception {
        // Both schedules share a product, so this also detects an accidental Product/global lock.
        var other = schedules.saveAndFlush(new com.wonhoone.misterworld.infrastructure.persistence.entity.TourScheduleJpaEntity(
                schedule.getTourProduct(), LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 5), false));
        var acquired = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var workers = Executors.newFixedThreadPool(2);
        try {
            var holder = workers.submit(() -> writeTransaction().execute(status -> {
                schedules.findByIdForReservationUpdate(schedule.getId()).orElseThrow();
                acquired.countDown();
                await(release);
                return null;
            }));
            assertTrue(acquired.await(5, TimeUnit.SECONDS));
            var independent = workers.submit(() -> create(other, 3));
            assertTrue(independent.get(5, TimeUnit.SECONDS).scheduleJustConfirmed());
            assertThat(reservations.totalParticipantsForSchedule(schedule.getId())).isZero();
            assertThat(reservations.totalParticipantsForSchedule(other.getId())).isEqualTo(3);
            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
    private List<ReservationCreationResult> concurrentCreates() throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var workers = Executors.newFixedThreadPool(2);
        try {
            Callable<ReservationCreationResult> attempt = () -> {
                ready.countDown();
                await(start);
                return create(schedule, 2);
            };
            var first = workers.submit(attempt);
            var second = workers.submit(attempt);
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            return List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
    private TransactionTemplate writeTransaction() {
        var template = new TransactionTemplate(transactions);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return template;
    }
    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Test coordination timed out");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Test worker interrupted", exception);
        }
    }
}
