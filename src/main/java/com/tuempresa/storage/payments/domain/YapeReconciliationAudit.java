package com.tuempresa.storage.payments.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "yape_reconciliation_audit")
public class YapeReconciliationAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_attempt_id")
    private Long paymentAttemptId;

    @Column(name = "email_amount", precision = 12, scale = 2)
    private BigDecimal emailAmount;

    @Column(name = "sender_name", length = 255)
    private String senderName;

    @Column(name = "sender_email", length = 255)
    private String senderEmail;

    @Column(name = "tx_date_time_raw", length = 100)
    private String txDateTimeRaw;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "message_id", nullable = false, length = 500)
    private String messageId;

    @Column(name = "subject", length = 500)
    private String subject;

    @Column(name = "outcome", nullable = false, length = 30)
    private String outcome;

    @Column(name = "match_reason", columnDefinition = "TEXT")
    private String matchReason;

    @Column(name = "matched_fields", length = 200)
    private String matchedFields;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public static YapeReconciliationAudit of(
            Long paymentAttemptId,
            BigDecimal emailAmount,
            String senderName,
            String senderEmail,
            String txDateTimeRaw,
            Instant receivedAt,
            String messageId,
            String subject,
            String outcome,
            String matchReason,
            String matchedFields) {
        YapeReconciliationAudit audit = new YapeReconciliationAudit();
        audit.paymentAttemptId = paymentAttemptId;
        audit.emailAmount = emailAmount;
        audit.senderName = senderName;
        audit.senderEmail = senderEmail;
        audit.txDateTimeRaw = txDateTimeRaw;
        audit.receivedAt = receivedAt != null ? receivedAt : Instant.now();
        audit.messageId = messageId;
        audit.subject = subject;
        audit.outcome = outcome;
        audit.matchReason = matchReason;
        audit.matchedFields = matchedFields;
        return audit;
    }

    public Long getId() { return id; }
    public Long getPaymentAttemptId() { return paymentAttemptId; }
    public BigDecimal getEmailAmount() { return emailAmount; }
    public String getSenderName() { return senderName; }
    public String getSenderEmail() { return senderEmail; }
    public String getTxDateTimeRaw() { return txDateTimeRaw; }
    public Instant getReceivedAt() { return receivedAt; }
    public String getMessageId() { return messageId; }
    public String getSubject() { return subject; }
    public String getOutcome() { return outcome; }
    public String getMatchReason() { return matchReason; }
    public String getMatchedFields() { return matchedFields; }
    public Instant getCreatedAt() { return createdAt; }
}
