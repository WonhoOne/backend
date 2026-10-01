package com.wonhoone.misterworld.application.sms;

import com.wonhoone.misterworld.config.SmsProperties;
import com.wonhoone.misterworld.infrastructure.persistence.repository.SmsConfirmationRecipientJpaRepository;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class SmsSignalAndDispatcherTests {
    @Test void rejectedAsyncSubmissionDoesNotEscapeCommittedBusinessSignal() {
        var dispatcher = mock(SmsDeliveryDispatcher.class);
        var listener = new SmsConfirmationAfterCommitListener(dispatcher, task -> {
            throw new RejectedExecutionException("Test executor is full");
        });
        assertDoesNotThrow(() -> listener.onReady(new SmsConfirmationOutboxReady(1)));
        verifyNoInteractions(dispatcher);
    }
    @Test void unexpectedRecipientTransactionFailureDoesNotStopFollowingRecipient() {
        var rows = mock(SmsConfirmationRecipientJpaRepository.class);
        var attempts = mock(SmsDeliveryAttemptService.class);
        var properties = mock(SmsProperties.class);
        when(properties.delivery()).thenReturn(new SmsProperties.Delivery(true, 10000, 50));
        when(rows.findDueIds(any(), any())).thenReturn(List.of(1L, 2L));
        doThrow(new IllegalStateException("Test database failure")).when(attempts).deliver(1L);
        new SmsDeliveryDispatcher(rows, attempts, properties, Clock.systemUTC()).dispatchDue();
        verify(attempts).deliver(1L); verify(attempts).deliver(2L);
    }
}
