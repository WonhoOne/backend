package com.wonhoone.misterworld.application.sms;

import com.wonhoone.misterworld.application.port.*;
import com.wonhoone.misterworld.infrastructure.persistence.repository.SmsConfirmationRecipientJpaRepository;
import java.time.Clock;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@ConditionalOnProperty(name = "sms.delivery.enabled", havingValue = "true")
public class SmsDeliveryAttemptService {
    private static final Logger log = LoggerFactory.getLogger(SmsDeliveryAttemptService.class);
    private final SmsConfirmationRecipientJpaRepository recipients;
    private final SmsSender sender;
    private final SmsRetryPolicy retry;
    private final Clock clock;
    public SmsDeliveryAttemptService(SmsConfirmationRecipientJpaRepository recipients, SmsSender sender,
                                     SmsRetryPolicy retry, @Qualifier("smsClock") Clock clock) {
        this.recipients = recipients; this.sender = sender; this.retry = retry; this.clock = clock;
    }
    /** One recipient lock and transaction, independent of Reservation and every other delivery. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deliver(long recipientId) {
        var recipient = recipients.findByIdForDelivery(recipientId).orElse(null);
        if (recipient == null || !recipient.isDue(clock.instant())) return;
        SmsSendResult result;
        try {
            result = sender.send(new SmsMessage(recipient.getContactSnapshot(),
                    recipient.getConfirmationEvent().getMessageText()));
            if (result == null) throw new SmsDeliveryException(SmsDeliveryException.Category.INVALID_RESPONSE);
        } catch (RuntimeException failure) {
            var next = clock.instant().plus(retry.delayAfterFailure((long) recipient.getAttemptCount() + 1));
            recipient.recordFailure(next);
            String category = failure instanceof SmsDeliveryException safe ? safe.category().name() : "UNEXPECTED_PROVIDER_FAILURE";
            log.warn("SMS delivery failed recipientId={} attempt={} category={}",
                    recipientId, recipient.getAttemptCount(), category);
            recipients.flush();
            return;
        }
        // Do not catch persistence failures as provider failures: commit ambiguity is documented.
        recipient.markSent(result.providerMessageId(), clock.instant());
        recipients.flush();
    }
}
