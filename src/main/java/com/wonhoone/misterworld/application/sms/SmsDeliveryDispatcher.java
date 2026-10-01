package com.wonhoone.misterworld.application.sms;

import com.wonhoone.misterworld.config.SmsProperties;
import com.wonhoone.misterworld.infrastructure.persistence.repository.SmsConfirmationRecipientJpaRepository;
import java.time.Clock;
import java.util.List;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "sms.delivery.enabled", havingValue = "true")
public class SmsDeliveryDispatcher {
    private static final Logger log = LoggerFactory.getLogger(SmsDeliveryDispatcher.class);
    private final SmsConfirmationRecipientJpaRepository recipients;
    private final SmsDeliveryAttemptService attempts;
    private final SmsProperties properties;
    private final Clock clock;
    public SmsDeliveryDispatcher(SmsConfirmationRecipientJpaRepository recipients, SmsDeliveryAttemptService attempts,
                                 SmsProperties properties, @Qualifier("smsClock") Clock clock) {
        this.recipients = recipients; this.attempts = attempts; this.properties = properties; this.clock = clock;
    }
    public void dispatchDue() {
        deliverEach(recipients.findDueIds(clock.instant(), batch()));
    }
    public void dispatchEvent(long eventId) {
        long afterId = 0;
        var now = clock.instant();
        while (true) {
            var ids = recipients.findDueEventIds(eventId, now, afterId, batch());
            if (ids.isEmpty()) return;
            deliverEach(ids);
            afterId = ids.getLast();
        }
    }
    private PageRequest batch() { return PageRequest.of(0, properties.delivery().batchSize()); }
    private void deliverEach(List<Long> ids) {
        for (long id : ids) {
            try { attempts.deliver(id); }
            catch (RuntimeException failure) {
                log.warn("SMS delivery transaction failed recipientId={} category=TRANSACTION_FAILURE", id);
            }
        }
    }
}
