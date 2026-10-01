package com.wonhoone.misterworld.application.sms;

import com.wonhoone.misterworld.infrastructure.persistence.entity.*;
import com.wonhoone.misterworld.infrastructure.persistence.repository.*;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class ScheduleConfirmationOutboxService {
    private final ReservationJpaRepository reservations;
    private final SmsConfirmationEventJpaRepository events;
    private final SmsConfirmationRecipientJpaRepository recipients;
    private final ScheduleConfirmationMessageComposer composer;
    private final Clock clock;
    private final ApplicationEventPublisher publisher;

    public ScheduleConfirmationOutboxService(ReservationJpaRepository reservations,
            SmsConfirmationEventJpaRepository events, SmsConfirmationRecipientJpaRepository recipients,
            ScheduleConfirmationMessageComposer composer, @Qualifier("smsClock") Clock clock,
            ApplicationEventPublisher publisher) {
        this.reservations = reservations; this.events = events; this.recipients = recipients;
        this.composer = composer; this.clock = clock; this.publisher = publisher;
    }

    /** Called only at the first transition, while ReservationCommandService holds the Schedule lock. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void capture(TourScheduleJpaEntity schedule) {
        var targets = reservations.findConfirmationRecipients(schedule.getId());
        if (targets.isEmpty()) throw new IllegalStateException("Confirmation must have reservation recipients");
        var now = clock.instant();
        var message = composer.compose(schedule.getTourProduct().getName(), schedule.getStartDate(), schedule.getEndDate());
        var event = events.saveAndFlush(new SmsConfirmationEventJpaEntity(schedule.getId(), message, now));
        recipients.saveAll(targets.stream().map(target -> new SmsConfirmationRecipientJpaEntity(
                event, target.getCustomerId(), target.getContact(), now)).toList());
        recipients.flush();
        publisher.publishEvent(new SmsConfirmationOutboxReady(event.getId()));
    }
}
