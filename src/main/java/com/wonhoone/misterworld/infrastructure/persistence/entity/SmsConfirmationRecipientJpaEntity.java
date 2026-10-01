package com.wonhoone.misterworld.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "sms_confirmation_recipient", uniqueConstraints = @UniqueConstraint(
        name = "uk_sms_recipient_event_customer", columnNames = {"confirmation_event_id", "customer_id"}))
public class SmsConfirmationRecipientJpaEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "confirmation_event_id", nullable = false, updatable = false)
    private SmsConfirmationEventJpaEntity confirmationEvent;
    @Column(name = "customer_id", nullable = false, updatable = false)
    private long customerId;
    @Column(name = "contact_snapshot", nullable = false, length = 255, updatable = false)
    private String contactSnapshot;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16)
    private SmsDeliveryStatus status;
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;
    @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;
    @JdbcTypeCode(SqlTypes.TIMESTAMP) @Column(name = "sent_at")
    private Instant sentAt;
    @Column(name = "provider_message_id", length = 255)
    private String providerMessageId;

    protected SmsConfirmationRecipientJpaEntity() {}
    public SmsConfirmationRecipientJpaEntity(SmsConfirmationEventJpaEntity event, long customerId,
                                               String contact, Instant createdAt) {
        this.confirmationEvent = Objects.requireNonNull(event);
        if (customerId <= 0) throw new IllegalArgumentException("Customer identity must be positive");
        this.customerId = customerId;
        this.contactSnapshot = Objects.requireNonNull(contact);
        this.status = SmsDeliveryStatus.PENDING;
        this.nextAttemptAt = Objects.requireNonNull(createdAt);
    }
    public boolean isDue(Instant now) {
        return status == SmsDeliveryStatus.PENDING && !nextAttemptAt.isAfter(now);
    }
    public void markSent(String providerMessageId, Instant sentAt) {
        requirePending();
        incrementAttempt();
        this.status = SmsDeliveryStatus.SENT;
        this.providerMessageId = providerMessageId;
        this.sentAt = Objects.requireNonNull(sentAt);
        this.nextAttemptAt = null;
    }
    public void recordFailure(Instant nextAttemptAt) {
        requirePending();
        incrementAttempt();
        this.nextAttemptAt = Objects.requireNonNull(nextAttemptAt);
    }
    private void requirePending() {
        if (status != SmsDeliveryStatus.PENDING) throw new IllegalStateException("Sent delivery cannot be repeated");
    }
    // Saturate diagnostics rather than making an indefinitely retryable row terminal on INT overflow.
    private void incrementAttempt() { if (attemptCount < Integer.MAX_VALUE) attemptCount++; }
    public Long getId() { return id; }
    public SmsConfirmationEventJpaEntity getConfirmationEvent() { return confirmationEvent; }
    public long getCustomerId() { return customerId; }
    public String getContactSnapshot() { return contactSnapshot; }
    public SmsDeliveryStatus getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getSentAt() { return sentAt; }
    public String getProviderMessageId() { return providerMessageId; }
}
