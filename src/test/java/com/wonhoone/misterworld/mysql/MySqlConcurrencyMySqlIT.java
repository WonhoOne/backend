package com.wonhoone.misterworld.mysql;

import com.wonhoone.misterworld.api.dto.InventoryAddRequest;
import com.wonhoone.misterworld.application.reservation.ReservationCreationResult;
import java.sql.Connection;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import static com.wonhoone.misterworld.domain.InventoryItemType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.Advised;

class MySqlConcurrencyMySqlIT extends MySqlIntegrationSupport {
    @Test void concurrentReservationsOnSameScheduleSerializeUnderInnoDb() throws Exception {
        var customer = customer("concurrent");
        var schedule = futureSchedule();
        var results = together(() -> commands.create(customer.getId(), request(schedule.getId(), 2)),
                () -> commands.create(customer.getId(), request(schedule.getId(), 2)));
        assertThat(results.stream().map(r -> r.response().schedule().recruitment().currentCount()))
                .containsExactlyInAnyOrder(2L, 4L);
        assertThat(results.stream().filter(ReservationCreationResult::scheduleJustConfirmed).count()).isEqualTo(1);
        assertThat(reservations.count()).isEqualTo(2);
        assertThat(reservations.totalParticipantsForSchedule(schedule.getId())).isEqualTo(4);
        assertThat(schedules.findById(schedule.getId()).orElseThrow().isConfirmed()).isTrue();
        assertThat(events.count()).isEqualTo(1);
        assertThat(recipients.count()).isEqualTo(1); // Same Customer, two Reservations.
    }

    @Test void reservationAggregateSeesPreviousCommitAfterReadCommittedLockWait() throws Exception {
        var firstCustomer = customer("first");
        var secondCustomer = customer("second");
        var schedule = futureSchedule();
        var held = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var accountRead = new CountDownLatch(1);
        var isolation = new AtomicInteger();
        var workers = Executors.newFixedThreadPool(2);
        var accountProxy = (Advised) accounts;
        MethodInterceptor observer = call -> {
            var result = call.proceed();
            if (call.getMethod().getName().equals("findById")) {
                isolation.set(jdbc.execute((ConnectionCallback<Integer>) Connection::getTransactionIsolation));
                accountRead.countDown();
            }
            return result;
        };
        try {
            var first = workers.submit(() -> readCommitted().execute(status -> {
                var result = commands.create(firstCustomer.getId(), request(schedule.getId(), 2));
                held.countDown();
                await(release);
                return result;
            }));
            assertTrue(held.await(5, TimeUnit.SECONDS));
            // Observe the real account query before B reaches the held Schedule lock.
            // With REPEATABLE_READ this read would establish B's stale SUM snapshot.
            // Observe before Spring Data's terminal query interceptor, which does not proceed to later advice.
            accountProxy.addAdvice(0, observer);
            var second = workers.submit(() -> commands.create(secondCustomer.getId(), request(schedule.getId(), 2)));
            if (!accountRead.await(5, TimeUnit.SECONDS)) {
                if (second.isDone()) second.get(1, TimeUnit.SECONDS); // Surface the worker failure, not just a latch timeout.
                fail("Second real account lookup did not reach the lock boundary");
            }
            assertThat(isolation.get()).isEqualTo(Connection.TRANSACTION_READ_COMMITTED);
            assertThrows(TimeoutException.class, () -> second.get(300, TimeUnit.MILLISECONDS));
            release.countDown();
            assertFalse(first.get(10, TimeUnit.SECONDS).scheduleJustConfirmed());
            var result = second.get(10, TimeUnit.SECONDS);
            assertTrue(result.scheduleJustConfirmed());
            assertThat(result.response().schedule().recruitment().currentCount()).isEqualTo(4);
            assertThat(reservations.count()).isEqualTo(2);
            assertThat(events.count()).isEqualTo(1);
            assertThat(recipients.findAll().stream().map(r -> r.getCustomerId()))
                    .containsExactlyInAnyOrder(firstCustomer.getId(), secondCustomer.getId());
            MySqlRuntimeMySqlIT.recordEvidence("Reservation lock wait: account read before previous commit; JDBC isolation=READ_COMMITTED; SUM=4; transition=1; events=1; distinct recipients=2");
        } finally { release.countDown(); shutdown(workers); accountProxy.removeAdvice(observer); }
    }

    @Test void confirmedFutureScheduleStillAcceptsReservationsWithoutAnotherEvent() {
        var customer = customer("confirmed");
        var schedule = futureSchedule();
        assertTrue(commands.create(customer.getId(), request(schedule.getId(), 3)).scheduleJustConfirmed());
        var extra = commands.create(customer.getId(), request(schedule.getId(), 2));
        assertFalse(extra.scheduleJustConfirmed());
        assertThat(extra.response().schedule().recruitment().currentCount()).isEqualTo(5);
        assertThat(events.count()).isEqualTo(1);
        assertThat(recipients.count()).isEqualTo(1);
    }

    @Test void concurrentInventoryAddsDoNotLoseUpdatesUnderInnoDb() throws Exception {
        var results = together(() -> inventoryCommands.add(new InventoryAddRequest(COUPLE_TSHIRT, 5L)).quantity(),
                () -> inventoryCommands.add(new InventoryAddRequest(COUPLE_TSHIRT, 7L)).quantity());
        assertThat(results).contains(12L);
        assertThat(quantity(COUPLE_TSHIRT)).isEqualTo(12L);
        assertThat(inventory.count()).isEqualTo(4);
    }

    @Test void sameInventoryRowLockBlocksCompetingWriterWhileOtherItemIsIndependent() throws Exception {
        lockBlocks(() -> {
            var row = inventory.findByItemTypeForUpdate(COUPLE_TSHIRT).orElseThrow();
            row.addQuantity(5); inventory.flush();
        }, () -> inventoryCommands.add(new InventoryAddRequest(COUPLE_TSHIRT, 7L)),
                () -> assertThat(inventoryCommands.add(new InventoryAddRequest(GOLF_BALL, 9L)).quantity()).isEqualTo(9));
        assertThat(quantity(COUPLE_TSHIRT)).isEqualTo(12);
        assertThat(quantity(GOLF_BALL)).isEqualTo(9);
        MySqlRuntimeMySqlIT.recordEvidence("Inventory held row blocks same-item add; final quantity=12; independent GOLF_BALL=9");
    }

    @Test void sameSmsRecipientLockBlocksCompetingWorkerAndSeesSentAfterCommit() throws Exception {
        var customer = customer("sms-lock");
        var schedule = futureSchedule();
        var recipient = recipient(schedule.getId(), customer.getId(), Instant.parse("2026-10-01T00:00:00.123456Z"));
        lockBlocks(() -> {
            var row = recipients.findByIdForDelivery(recipient.getId()).orElseThrow();
            row.markSent("test-only-no-provider-call", Instant.parse("2026-10-01T00:00:01.123456Z"));
            recipients.flush();
        }, () -> new TransactionTemplate(transactions).execute(status -> {
            var row = recipients.findByIdForDelivery(recipient.getId()).orElseThrow();
            assertFalse(row.isDue(Instant.parse("2026-10-01T00:00:02Z")));
            assertThat(row.getAttemptCount()).isEqualTo(1);
            return row.getStatus();
        }), () -> {});
        MySqlRuntimeMySqlIT.recordEvidence("SMS held recipient blocks competing repository worker; sees SENT after commit; provider calls=0");
    }

    private long quantity(com.wonhoone.misterworld.domain.InventoryItemType type) {
        return inventory.findByItemType(type).orElseThrow().getQuantity();
    }
    private TransactionTemplate readCommitted() {
        var template = new TransactionTemplate(transactions);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return template;
    }
    private <T> List<T> together(Callable<T> first, Callable<T> second) throws Exception {
        var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
        var workers = Executors.newFixedThreadPool(2);
        try {
            var a = workers.submit(() -> { ready.countDown(); await(start); return first.call(); });
            var b = workers.submit(() -> { ready.countDown(); await(start); return second.call(); });
            assertTrue(ready.await(5, TimeUnit.SECONDS)); start.countDown();
            return List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
        } finally { start.countDown(); shutdown(workers); }
    }
    private void lockBlocks(Runnable acquireAndMutate, Callable<?> competitor, Runnable independent) throws Exception {
        var held = new CountDownLatch(1); var release = new CountDownLatch(1); var started = new CountDownLatch(1);
        var workers = Executors.newFixedThreadPool(2);
        try {
            var holder = workers.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                acquireAndMutate.run(); held.countDown(); await(release);
            }));
            assertTrue(held.await(5, TimeUnit.SECONDS));
            var second = workers.submit(() -> { started.countDown(); return competitor.call(); });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            independent.run();
            assertThrows(TimeoutException.class, () -> second.get(300, TimeUnit.MILLISECONDS));
            release.countDown(); holder.get(10, TimeUnit.SECONDS); second.get(10, TimeUnit.SECONDS);
        } finally { release.countDown(); shutdown(workers); }
    }
    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("B9 coordination timed out");
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("B9 worker interrupted"); }
    }
    private static void shutdown(ExecutorService workers) throws InterruptedException {
        workers.shutdownNow(); assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
    }
}
