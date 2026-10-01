package com.wonhoone.misterworld.reservation;

import com.wonhoone.misterworld.application.port.*;
import com.wonhoone.misterworld.application.sms.*;
import com.wonhoone.misterworld.infrastructure.persistence.entity.*;
import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:sms-delivery;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;LOCK_TIMEOUT=5000",
        "sms.delivery.enabled=true", "sms.solapi.api-key=dummy-key", "sms.solapi.api-secret=dummy-secret",
        "sms.solapi.sender-number=010-0000-0000", "sms.delivery.batch-size=2"})
@Timeout(30)
class SmsDeliveryIntegrationTests extends ReservationIntegrationSupport {
    @MockitoBean SmsSender sender;
    @MockitoBean SmsConfirmationAfterCommitListener listener;
    @MockitoBean SmsDeliveryPoller poller;
    @TestBean(name = "smsClock", methodName = "deliveryClock") Clock smsClock;
    static Clock deliveryClock() { return new MutableSmsClock(); }
    @Autowired SmsDeliveryDispatcher dispatcher;
    @Autowired SmsDeliveryAttemptService attempts;

    @BeforeEach void prepareSender() {
        ((MutableSmsClock) smsClock).reset();
        when(sender.send(any())).thenReturn(new SmsSendResult("provider-accepted-id"));
    }
    @Test void successfulAcceptanceMarksSentAndStoresProviderIdAndTime() {
        create(schedule, 3); dispatcher.dispatchDue();
        var row = onlyRecipient();
        assertThat(row.getStatus()).isEqualTo(SmsDeliveryStatus.SENT);
        assertThat(row.getAttemptCount()).isEqualTo(1);
        assertThat(row.getProviderMessageId()).isEqualTo("provider-accepted-id");
        assertThat(row.getSentAt()).isEqualTo(smsClock.instant());
        assertThat(row.getNextAttemptAt()).isNull();
    }
    @Test void providerFailureRemainsPendingAndDoesNotRollbackBusinessState() {
        when(sender.send(any())).thenThrow(new SmsDeliveryException(SmsDeliveryException.Category.NETWORK));
        create(schedule, 3); dispatcher.dispatchDue();
        var row = onlyRecipient();
        assertThat(row.getStatus()).isEqualTo(SmsDeliveryStatus.PENDING);
        assertThat(row.getAttemptCount()).isEqualTo(1);
        assertThat(row.getNextAttemptAt()).isEqualTo(smsClock.instant().plusSeconds(30));
        assertThat(row.getSentAt()).isNull();
        assertThat(reservations.count()).isEqualTo(1);
        assertThat(schedules.findById(schedule.getId()).orElseThrow().isConfirmed()).isTrue();
    }
    @Test void failedRecipientIsNotRetriedBeforeDueAndEventuallyBecomesSent() {
        when(sender.send(any())).thenThrow(new SmsDeliveryException(SmsDeliveryException.Category.TIMEOUT))
                .thenReturn(new SmsSendResult("retry-success"));
        create(schedule, 3); dispatcher.dispatchDue();
        long id = onlyRecipient().getId();
        ((MutableSmsClock) smsClock).advance(29); dispatcher.dispatchDue(); attempts.deliver(id);
        verify(sender, times(1)).send(any());
        ((MutableSmsClock) smsClock).advance(1); dispatcher.dispatchDue();
        var row = onlyRecipient();
        assertThat(row.getStatus()).isEqualTo(SmsDeliveryStatus.SENT);
        assertThat(row.getAttemptCount()).isEqualTo(2);
        assertThat(row.getProviderMessageId()).isEqualTo("retry-success");
        assertThat(row.getNextAttemptAt()).isNull();
    }
    @Test void successiveFailuresIncreaseBackoffAndRemainRetryable() {
        when(sender.send(any())).thenThrow(new SmsDeliveryException(SmsDeliveryException.Category.PROVIDER_REJECTED));
        create(schedule, 3); dispatcher.dispatchDue(); ((MutableSmsClock) smsClock).advance(30); dispatcher.dispatchDue();
        assertThat(onlyRecipient().getAttemptCount()).isEqualTo(2);
        assertThat(onlyRecipient().getNextAttemptAt()).isEqualTo(smsClock.instant().plusSeconds(60));
        assertThat(onlyRecipient().getStatus()).isEqualTo(SmsDeliveryStatus.PENDING);
    }
    @Test void oneRecipientFailureDoesNotStopOtherRecipientsOrResendSuccessfulOnes() {
        create(schedule, 1);
        commands.create(account("second").getId(), request(schedule, 1));
        commands.create(account("third").getId(), request(schedule, 1));
        var ids = smsRecipients.findAll().stream().map(SmsConfirmationRecipientJpaEntity::getId).sorted().toList();
        when(sender.send(any())).thenReturn(new SmsSendResult("first"))
                .thenThrow(new SmsDeliveryException(SmsDeliveryException.Category.NETWORK))
                .thenReturn(new SmsSendResult("third"), new SmsSendResult("second-retry"));
        dispatcher.dispatchEvent(smsEvents.findAll().getFirst().getId());
        assertThat(smsRecipients.findById(ids.get(0)).orElseThrow().getStatus()).isEqualTo(SmsDeliveryStatus.SENT);
        assertThat(smsRecipients.findById(ids.get(1)).orElseThrow().getStatus()).isEqualTo(SmsDeliveryStatus.PENDING);
        assertThat(smsRecipients.findById(ids.get(2)).orElseThrow().getStatus()).isEqualTo(SmsDeliveryStatus.SENT);
        ((MutableSmsClock) smsClock).advance(30); dispatcher.dispatchDue();
        verify(sender, times(4)).send(any());
        assertThat(smsRecipients.findAll()).allMatch(r -> r.getStatus() == SmsDeliveryStatus.SENT);
        assertThat(smsRecipients.findById(ids.get(1)).orElseThrow().getAttemptCount()).isEqualTo(2);
    }
    @Test void sentRecipientIsNeverResentThroughAnyEntryPoint() {
        create(schedule, 3); dispatcher.dispatchDue();
        dispatcher.dispatchDue(); dispatcher.dispatchEvent(smsEvents.findAll().getFirst().getId());
        attempts.deliver(onlyRecipient().getId());
        verify(sender, times(1)).send(any());
    }
    @Test void pendingRowsCanBeRecoveredWithoutImmediateSignalOrNewReservation() {
        create(schedule, 3);
        verifyNoInteractions(sender);
        assertThat(onlyRecipient().getStatus()).isEqualTo(SmsDeliveryStatus.PENDING);
        dispatcher.dispatchDue();
        assertThat(onlyRecipient().getStatus()).isEqualTo(SmsDeliveryStatus.SENT);
        assertThat(reservations.count()).isEqualTo(1);
    }
    @Test void retryUsesCapturedContactAndMessageAfterCurrentDataChanges() {
        create(schedule, 3);
        String captured = smsEvents.findAll().getFirst().getMessageText();
        jdbc.update("UPDATE user_account SET contact = 'changed' WHERE id = ?", customer.getId());
        jdbc.update("UPDATE tour_product SET name = 'changed' WHERE id = ?", schedule.getTourProduct().getId());
        jdbc.update("UPDATE tour_schedule SET start_date = '2026-12-01', end_date = '2026-12-05' WHERE id = ?", schedule.getId());
        dispatcher.dispatchDue();
        verify(sender).send(new SmsMessage("Private contact", captured));
    }
    @Test void unexpectedSenderFailureIsStillDurableAndRetryable() {
        when(sender.send(any())).thenThrow(new IllegalStateException("sensitive test payload"));
        create(schedule, 3); dispatcher.dispatchDue();
        assertThat(onlyRecipient().getStatus()).isEqualTo(SmsDeliveryStatus.PENDING);
        assertThat(onlyRecipient().getAttemptCount()).isEqualTo(1);
    }
    @Test void nullSenderResultIsNotMarkedSent() {
        when(sender.send(any())).thenReturn(null);
        create(schedule, 3); dispatcher.dispatchDue();
        assertThat(onlyRecipient().getStatus()).isEqualTo(SmsDeliveryStatus.PENDING);
    }
    @Test void pollingProcessesAtMostConfiguredBatch() {
        create(schedule, 1);
        commands.create(account("second").getId(), request(schedule, 1));
        commands.create(account("third").getId(), request(schedule, 1));
        dispatcher.dispatchDue(); verify(sender, times(2)).send(any());
        dispatcher.dispatchDue(); verify(sender, times(3)).send(any());
    }
    @Test void sentCannotBeChangedBackToPending() {
        create(schedule, 3); dispatcher.dispatchDue();
        assertThatThrownBy(() -> onlyRecipient().recordFailure(smsClock.instant())).isInstanceOf(IllegalStateException.class);
    }
    SmsConfirmationRecipientJpaEntity onlyRecipient() { return smsRecipients.findAll().getFirst(); }

    static final class MutableSmsClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>();
        MutableSmsClock() { reset(); }
        void reset() { now.set(Instant.parse("2026-10-01T00:00:00Z")); }
        void advance(long seconds) { now.updateAndGet(time -> time.plusSeconds(seconds)); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    }
}
