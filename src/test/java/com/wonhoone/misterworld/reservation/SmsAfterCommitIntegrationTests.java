package com.wonhoone.misterworld.reservation;

import com.wonhoone.misterworld.application.port.*;
import com.wonhoone.misterworld.application.sms.SmsDeliveryPoller;
import com.wonhoone.misterworld.infrastructure.persistence.entity.SmsDeliveryStatus;
import java.time.Duration;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:sms-after-commit;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;LOCK_TIMEOUT=5000",
        "sms.delivery.enabled=true", "sms.solapi.api-key=dummy-key", "sms.solapi.api-secret=dummy-secret",
        "sms.solapi.sender-number=010-0000-0000"})
@Timeout(30)
class SmsAfterCommitIntegrationTests extends ReservationIntegrationSupport {
    @MockitoBean SmsSender sender;
    @MockitoBean SmsDeliveryPoller poller;

    @Test void providerIsCalledOnlyAfterBusinessCommitOnDedicatedExecutorInDeliveryTransaction() {
        when(sender.send(any())).thenAnswer(call -> {
            assertThat(Thread.currentThread().getName()).startsWith("sms-delivery-");
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(reservations.count()).isEqualTo(1);
            assertThat(schedules.findById(schedule.getId()).orElseThrow().isConfirmed()).isTrue();
            return new SmsSendResult("after-commit");
        });
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            create(schedule, 3);
            assertThat(smsEvents.count()).isEqualTo(1);
            verifyNoInteractions(sender);
        });
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(smsRecipients.findAll().getFirst().getStatus()).isEqualTo(SmsDeliveryStatus.SENT);
        });
        verify(sender, times(1)).send(any());
    }
    @Test void rolledBackConfirmationDoesNotInvokeAfterCommitProvider() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            create(schedule, 3); status.setRollbackOnly();
        });
        assertThat(smsEvents.count()).isZero(); assertThat(smsRecipients.count()).isZero();
        verifyNoInteractions(sender);
    }
    @Test void providerFailureAfterPublicPostPreservesCreatedResponseAndCommittedConfirmation() throws Exception {
        when(sender.send(any())).thenThrow(new SmsDeliveryException(SmsDeliveryException.Category.PROVIDER_REJECTED));
        long id = postId(request(schedule, 3));
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(smsRecipients.findAll().getFirst().getAttemptCount()).isEqualTo(1);
        });
        assertThat(reservations.findById(id)).isPresent();
        assertThat(schedules.findById(schedule.getId()).orElseThrow().isConfirmed()).isTrue();
        assertThat(smsRecipients.findAll().getFirst().getStatus()).isEqualTo(SmsDeliveryStatus.PENDING);
    }
    @Test void slowProviderDoesNotHoldReservationPostResponse() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        when(sender.send(any())).thenAnswer(call -> {
            entered.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Test provider timed out");
            return new SmsSendResult("slow-provider");
        });
        try {
            long id = postId(request(schedule, 3));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertThat(reservations.findById(id)).isPresent();
            // POST has returned while the fake provider is still blocked on release.
            assertThat(release.getCount()).isEqualTo(1);
        } finally { release.countDown(); }
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(smsRecipients.findAll().getFirst().getStatus()).isEqualTo(SmsDeliveryStatus.SENT));
    }
}
