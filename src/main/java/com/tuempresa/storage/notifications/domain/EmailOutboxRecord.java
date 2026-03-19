package com.tuempresa.storage.notifications.domain;

import com.tuempresa.storage.shared.infrastructure.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "email_outbox")
public class EmailOutboxRecord extends AuditableEntity {

    @Column(nullable = false, length = 190)
    private String recipient;

    @Column(nullable = false, length = 220)
    private String subject;

    @Column(name = "html_body", nullable = false, columnDefinition = "text")
    private String htmlBody;

    @Column(name = "text_body", columnDefinition = "text")
    private String textBody;

    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;

    @Column(name = "dedup_key", length = 200)
    private String dedupKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmailOutboxStatus status = EmailOutboxStatus.PENDING;

    @Column(length = 40)
    private String provider;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    public static EmailOutboxRecord pending(
            String recipient,
            String subject,
            String htmlBody,
            String textBody,
            String eventType,
            String dedupKey
    ) {
        EmailOutboxRecord record = new EmailOutboxRecord();
        record.recipient = recipient;
        record.subject = subject;
        record.htmlBody = htmlBody;
        record.textBody = textBody;
        record.eventType = eventType;
        record.dedupKey = dedupKey;
        record.status = EmailOutboxStatus.PENDING;
        record.nextAttemptAt = Instant.now();
        record.attemptCount = 0;
        return record;
    }

    public String getRecipient() {
        return recipient;
    }

    public String getSubject() {
        return subject;
    }

    public String getHtmlBody() {
        return htmlBody;
    }

    public String getTextBody() {
        return textBody;
    }

    public String getEventType() {
        return eventType;
    }

    public String getDedupKey() {
        return dedupKey;
    }

    public EmailOutboxStatus getStatus() {
        return status;
    }

    public String getProvider() {
        return provider;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public Instant getLastAttemptAt() {
        return lastAttemptAt;
    }

    public String getLastError() {
        return lastError;
    }

    public boolean isReadyToSend(Instant now) {
        if (status != EmailOutboxStatus.PENDING) {
            return false;
        }
        return nextAttemptAt == null || !nextAttemptAt.isAfter(now);
    }

    public void registerAttempt(Instant when) {
        this.attemptCount += 1;
        this.lastAttemptAt = when;
    }

    public void markSent(String provider, Instant when) {
        this.status = EmailOutboxStatus.SENT;
        this.provider = limit(provider, 40);
        this.sentAt = when;
        this.nextAttemptAt = null;
        this.lastError = null;
    }

    public void markRetry(String provider, String errorMessage, Instant nextAttemptAt) {
        this.status = EmailOutboxStatus.PENDING;
        this.provider = limit(provider, 40);
        this.lastError = limit(errorMessage, 500);
        this.nextAttemptAt = nextAttemptAt;
    }

    public void markFailed(String provider, String errorMessage) {
        this.status = EmailOutboxStatus.FAILED;
        this.provider = limit(provider, 40);
        this.lastError = limit(errorMessage, 500);
        this.nextAttemptAt = null;
    }

    private String limit(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}

