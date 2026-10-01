package com.wonhoone.misterworld.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "sms_confirmation_event", uniqueConstraints =
        @UniqueConstraint(name = "uk_sms_event_schedule", columnNames = "tour_schedule_id"))
public class SmsConfirmationEventJpaEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "tour_schedule_id", nullable = false, updatable = false)
    private long tourScheduleId;
    @Column(name = "message_text", nullable = false, length = 2000, updatable = false)
    private String messageText;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SmsConfirmationEventJpaEntity() {}
    public SmsConfirmationEventJpaEntity(long scheduleId, String messageText, Instant createdAt) {
        if (scheduleId <= 0) throw new IllegalArgumentException("Schedule identity must be positive");
        if (messageText == null || messageText.isBlank() || messageText.length() > 2000)
            throw new IllegalArgumentException("Confirmation message must fit event storage");
        this.tourScheduleId = scheduleId;
        this.messageText = messageText;
        this.createdAt = Objects.requireNonNull(createdAt);
    }
    public Long getId() { return id; }
    public long getTourScheduleId() { return tourScheduleId; }
    public String getMessageText() { return messageText; }
    public Instant getCreatedAt() { return createdAt; }
}
