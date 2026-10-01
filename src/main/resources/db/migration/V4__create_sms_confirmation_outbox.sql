CREATE TABLE sms_confirmation_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tour_schedule_id BIGINT NOT NULL,
    message_text VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_sms_event_schedule UNIQUE (tour_schedule_id),
    CONSTRAINT fk_sms_event_schedule FOREIGN KEY (tour_schedule_id) REFERENCES tour_schedule (id)
);

CREATE TABLE sms_confirmation_recipient (
    id BIGINT NOT NULL AUTO_INCREMENT,
    confirmation_event_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    contact_snapshot VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempt_count INT NOT NULL,
    next_attempt_at TIMESTAMP(6),
    sent_at TIMESTAMP(6),
    provider_message_id VARCHAR(255),
    PRIMARY KEY (id),
    CONSTRAINT uk_sms_recipient_event_customer UNIQUE (confirmation_event_id, customer_id),
    CONSTRAINT fk_sms_recipient_event FOREIGN KEY (confirmation_event_id) REFERENCES sms_confirmation_event (id),
    CONSTRAINT fk_sms_recipient_customer FOREIGN KEY (customer_id) REFERENCES user_account (id),
    CONSTRAINT ck_sms_recipient_status CHECK (status IN ('PENDING', 'SENT')),
    CONSTRAINT ck_sms_recipient_attempt CHECK (attempt_count >= 0),
    CONSTRAINT ck_sms_recipient_state CHECK (
        (status = 'PENDING' AND next_attempt_at IS NOT NULL AND sent_at IS NULL)
        OR (status = 'SENT' AND next_attempt_at IS NULL AND sent_at IS NOT NULL)
    )
);

CREATE INDEX ix_sms_recipient_due ON sms_confirmation_recipient (status, next_attempt_at, id);
