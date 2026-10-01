package com.wonhoone.misterworld.reservation;

import com.wonhoone.misterworld.application.port.*;
import com.wonhoone.misterworld.application.sms.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:sms-concurrent;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;LOCK_TIMEOUT=5000",
        "sms.delivery.enabled=true", "sms.solapi.api-key=dummy-key", "sms.solapi.api-secret=dummy-secret",
        "sms.solapi.sender-number=010-0000-0000"})
@Timeout(30)
class SmsDeliveryConcurrencyTests extends ReservationIntegrationSupport {
    @MockitoBean SmsSender sender;
    @MockitoBean SmsConfirmationAfterCommitListener listener;
    @MockitoBean SmsDeliveryPoller poller;
    @Autowired SmsDeliveryAttemptService attempts;

    @Test void concurrentDispatchWaitsForRecipientLockAndDoesNotSendSentRecipientTwice() throws Exception {
        create(schedule, 3);
        long id = smsRecipients.findAll().getFirst().getId();
        var insideProvider = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var secondStarted = new CountDownLatch(1);
        when(sender.send(any())).thenAnswer(call -> {
            insideProvider.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Test provider timed out");
            return new SmsSendResult("accepted");
        });
        var workers = Executors.newFixedThreadPool(2);
        try {
            var first = workers.submit(() -> attempts.deliver(id));
            assertTrue(insideProvider.await(5, TimeUnit.SECONDS));
            var second = workers.submit(() -> { secondStarted.countDown(); attempts.deliver(id); });
            assertTrue(secondStarted.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> second.get(300, TimeUnit.MILLISECONDS));
            verify(sender, times(1)).send(any());
            release.countDown(); first.get(10, TimeUnit.SECONDS); second.get(10, TimeUnit.SECONDS);
            verify(sender, times(1)).send(any());
            assertThat(smsRecipients.findById(id).orElseThrow().getStatus())
                    .isEqualTo(com.wonhoone.misterworld.infrastructure.persistence.entity.SmsDeliveryStatus.SENT);
        } finally {
            release.countDown(); workers.shutdownNow();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
    @Test void concurrentReservationThresholdCrossingCreatesOneEventAndOneCustomerRecipient() throws Exception {
        var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
        var workers = Executors.newFixedThreadPool(2);
        Callable<Boolean> create = () -> {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test start timed out");
            return create(schedule, 2).scheduleJustConfirmed();
        };
        try {
            var first = workers.submit(create); var second = workers.submit(create);
            assertTrue(ready.await(5, TimeUnit.SECONDS)); start.countDown();
            assertThat(java.util.List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(smsEvents.count()).isEqualTo(1); assertThat(smsRecipients.count()).isEqualTo(1);
        } finally {
            start.countDown(); workers.shutdownNow();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
}
