package com.wonhoone.misterworld.application.sms;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import java.util.concurrent.Executor;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;

@Component
@ConditionalOnProperty(name = "sms.delivery.enabled", havingValue = "true")
public class SmsConfirmationAfterCommitListener {
    private static final Logger log = LoggerFactory.getLogger(SmsConfirmationAfterCommitListener.class);
    private final SmsDeliveryDispatcher dispatcher;
    private final Executor executor;
    public SmsConfirmationAfterCommitListener(SmsDeliveryDispatcher dispatcher,
            @Qualifier("smsDeliveryExecutor") Executor executor) {
        this.dispatcher = dispatcher; this.executor = executor;
    }
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReady(SmsConfirmationOutboxReady event) {
        try {
            executor.execute(() -> {
                try { dispatcher.dispatchEvent(event.eventId()); }
                catch (RuntimeException failure) { log.warn("SMS event dispatch failed eventId={} category=QUERY_FAILURE", event.eventId()); }
            });
        } catch (RuntimeException rejection) {
            // Executor saturation/shutdown must not change the committed POST result. Polling recovers the row.
            log.warn("SMS event signal deferred eventId={} category=EXECUTOR_REJECTED", event.eventId());
        }
    }
}
