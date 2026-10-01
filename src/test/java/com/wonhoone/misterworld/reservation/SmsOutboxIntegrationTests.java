package com.wonhoone.misterworld.reservation;

import com.wonhoone.misterworld.application.sms.*;
import com.wonhoone.misterworld.infrastructure.persistence.entity.*;
import java.sql.*;
import java.time.Instant;
import org.h2.api.Trigger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SmsOutboxIntegrationTests extends ReservationIntegrationSupport {
    @Autowired ScheduleConfirmationOutboxService outbox;

    @Test void surefireSystemPropertyOverridesDeliveryEnabledEnvironment() {
        assertThat(System.getProperty("sms.delivery.enabled")).isEqualTo("false");
        var hostileEnvironment = new org.springframework.core.env.SystemEnvironmentPropertySource(
                "systemEnvironment", java.util.Map.of("SMS_DELIVERY_ENABLED", "true"));
        var environment = new org.springframework.core.env.StandardEnvironment();
        environment.getPropertySources().replace("systemEnvironment", hostileEnvironment);
        assertThat(hostileEnvironment.getProperty("sms.delivery.enabled")).isEqualTo("true");
        assertThat(environment.getProperty("sms.delivery.enabled")).isEqualTo("false");
        assertThat(context.getEnvironment().getProperty("sms.delivery.enabled")).isEqualTo("false");
        assertThat(context.getBeansOfType(com.wonhoone.misterworld.application.port.SmsSender.class)).isEmpty();
    }

    @Test void noSmsEventBeforeScheduleConfirmation() {
        create(schedule, 2);
        assertThat(smsEvents.count()).isZero(); assertThat(smsRecipients.count()).isZero();
    }
    @Test void firstConfirmationCreatesOneDurableEventAndTriggeringCustomer() {
        assertThat(create(schedule, 3).scheduleJustConfirmed()).isTrue();
        var event = smsEvents.findAll().getFirst();
        assertThat(event.getTourScheduleId()).isEqualTo(schedule.getId());
        assertThat(event.getCreatedAt()).isNotNull();
        assertThat(smsRecipients.findAll()).singleElement().satisfies(row -> {
            assertThat(row.getCustomerId()).isEqualTo(customer.getId());
            assertThat(row.getStatus()).isEqualTo(SmsDeliveryStatus.PENDING);
            assertThat(row.getAttemptCount()).isZero();
            assertThat(row.getNextAttemptAt()).isEqualTo(event.getCreatedAt());
            assertThat(row.getSentAt()).isNull();
        });
    }
    @Test void allDistinctCustomersIncludingTriggeringCustomerAreCaptured() {
        create(schedule, 2);
        var other = account("trigger");
        commands.create(other.getId(), request(schedule, 1));
        assertThat(smsRecipients.findAll()).extracting(SmsConfirmationRecipientJpaEntity::getCustomerId)
                .containsExactlyInAnyOrder(customer.getId(), other.getId());
    }
    @Test void sameCustomerWithMultipleReservationsProducesOneRecipient() {
        create(schedule, 1); create(schedule, 2);
        assertThat(reservations.count()).isEqualTo(2);
        assertThat(smsEvents.count()).isEqualTo(1); assertThat(smsRecipients.count()).isEqualTo(1);
    }
    @Test void duplicateContactsAcrossDifferentCustomersProduceTwoRecipients() {
        create(schedule, 2);
        commands.create(account("same-contact").getId(), request(schedule, 1));
        assertThat(smsRecipients.count()).isEqualTo(2);
        assertThat(smsRecipients.findAll()).extracting(SmsConfirmationRecipientJpaEntity::getContactSnapshot)
                .containsOnly("Private contact");
    }
    @Test void laterReservationOnConfirmedScheduleDoesNotChangeEventOrRecipients() {
        create(schedule, 3);
        long eventId = smsEvents.findAll().getFirst().getId();
        assertThat(commands.create(account("late").getId(), request(schedule, 1)).scheduleJustConfirmed()).isFalse();
        assertThat(smsEvents.findAll()).extracting(SmsConfirmationEventJpaEntity::getId).containsExactly(eventId);
        assertThat(smsRecipients.count()).isEqualTo(1);
    }
    @Test void contactIsCurrentAtTransitionAndRemainsStableAfterward() {
        create(schedule, 2);
        jdbc.update("UPDATE user_account SET contact = ? WHERE id = ?", "010-1111-2222", customer.getId());
        commands.create(account("trigger").getId(), request(schedule, 1));
        var row = smsRecipients.findAll().stream().filter(r -> r.getCustomerId() == customer.getId()).findFirst().orElseThrow();
        assertThat(row.getContactSnapshot()).isEqualTo("010-1111-2222");
        jdbc.update("UPDATE user_account SET contact = ? WHERE id = ?", "010-9999-0000", customer.getId());
        assertThat(smsRecipients.findById(row.getId()).orElseThrow().getContactSnapshot()).isEqualTo("010-1111-2222");
    }
    @Test void messageCapturesCurrentFullProductNameAndScheduleDates() {
        create(schedule, 2);
        String fullName = "제주 여행 " + "아름다운 긴 상품명 ".repeat(15);
        jdbc.update("UPDATE tour_product SET name = ? WHERE id = ?", fullName, schedule.getTourProduct().getId());
        create(schedule, 1);
        assertThat(smsEvents.findAll().getFirst().getMessageText())
                .contains(fullName, "2026-11-01", "2026-11-05", "출발이 확정")
                .doesNotContain("Private", "test-hash", "owner", "201", "KRW");
    }
    @Test void pendingMessageSnapshotDoesNotChangeAfterProductOrDateEdits() {
        create(schedule, 3);
        var event = smsEvents.findAll().getFirst();
        String text = event.getMessageText();
        jdbc.update("UPDATE tour_product SET name = 'Changed Product' WHERE id = ?", schedule.getTourProduct().getId());
        jdbc.update("UPDATE tour_schedule SET start_date = '2026-12-01', end_date = '2026-12-05' WHERE id = ?", schedule.getId());
        assertThat(smsEvents.findById(event.getId()).orElseThrow().getMessageText()).isEqualTo(text);
    }
    @Test void reservationRollbackRemovesConfirmationAndOutbox() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            create(schedule, 3);
            assertThat(smsEvents.count()).isEqualTo(1);
            status.setRollbackOnly();
        });
        assertThat(reservations.count()).isZero(); assertThat(smsEvents.count()).isZero();
        assertThat(smsRecipients.count()).isZero();
        assertThat(schedules.findById(schedule.getId()).orElseThrow().isConfirmed()).isFalse();
    }
    @Test void outboxPersistenceFailureRollsBackReservationAndConfirmation() throws Exception {
        jdbc.execute("CREATE TRIGGER reject_sms_recipient BEFORE INSERT ON sms_confirmation_recipient FOR EACH ROW CALL '"
                + RejectRecipientInsert.class.getName() + "'");
        try {
            postReservation(request(schedule, 3))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isInternalServerError());
            assertThat(reservations.count()).isZero(); assertThat(smsEvents.count()).isZero();
            assertThat(smsRecipients.count()).isZero();
            assertThat(schedules.findById(schedule.getId()).orElseThrow().isConfirmed()).isFalse();
        } finally { jdbc.execute("DROP TRIGGER reject_sms_recipient"); }
    }
    @Test void secondEventForSameScheduleIsRejectedByDatabase() {
        create(schedule, 3);
        assertThrows(DataIntegrityViolationException.class, () -> smsEvents.saveAndFlush(
                new SmsConfirmationEventJpaEntity(schedule.getId(), "duplicate", Instant.now())));
        assertThat(smsEvents.count()).isEqualTo(1);
    }
    @Test void duplicateEventCustomerRecipientIsRejectedByDatabase() {
        create(schedule, 3);
        assertThrows(DataIntegrityViolationException.class, () -> smsRecipients.saveAndFlush(
                new SmsConfirmationRecipientJpaEntity(smsEvents.findAll().getFirst(), customer.getId(), "duplicate", Instant.now())));
        assertThat(smsRecipients.count()).isEqualTo(1);
    }
    @Test void captureRequiresExistingBusinessTransaction() {
        assertThrows(org.springframework.transaction.IllegalTransactionStateException.class, () -> outbox.capture(schedule));
    }
    @Test void emptyRecipientsCannotCommitAnOutbox() {
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactions)
                .executeWithoutResult(status -> outbox.capture(schedule)));
        assertThat(smsEvents.count()).isZero();
    }
    @Test void deliveryDisabledStillCommitsPendingRowsWithoutProviderInfrastructure() {
        create(schedule, 3);
        assertThat(smsRecipients.findAll()).singleElement().extracting(SmsConfirmationRecipientJpaEntity::getStatus)
                .isEqualTo(SmsDeliveryStatus.PENDING);
        assertThat(context.getBeansOfType(com.wonhoone.misterworld.application.port.SmsSender.class)).isEmpty();
        assertThat(context.getBeansOfType(SmsDeliveryPoller.class)).isEmpty();
        assertThat(context.getBeansOfType(SmsConfirmationAfterCommitListener.class)).isEmpty();
    }
    public static class RejectRecipientInsert implements Trigger {
        @Override public void fire(Connection connection, Object[] oldRow, Object[] newRow) throws SQLException {
            throw new SQLException("Test outbox insertion rejected");
        }
    }
}
