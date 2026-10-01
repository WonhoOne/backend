package com.wonhoone.misterworld.application.sms;

import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "sms.delivery.enabled", havingValue = "true")
public class SmsDeliveryPoller {
    private static final Logger log = LoggerFactory.getLogger(SmsDeliveryPoller.class);
    private final SmsDeliveryDispatcher dispatcher;
    public SmsDeliveryPoller(SmsDeliveryDispatcher dispatcher) { this.dispatcher = dispatcher; }
    @Scheduled(fixedDelayString = "${sms.delivery.poll-delay-ms}", initialDelayString = "${sms.delivery.poll-delay-ms}")
    public void poll() {
        try { dispatcher.dispatchDue(); }
        catch (RuntimeException failure) { log.warn("SMS polling failed category=QUERY_FAILURE"); }
    }
}
