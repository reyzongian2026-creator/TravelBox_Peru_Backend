package com.tuempresa.storage.payments.domain;

import com.tuempresa.storage.shared.infrastructure.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(
        name = "payment_webhook_events",
        uniqueConstraints = @UniqueConstraint(name = "uk_payment_webhook_provider_event", columnNames = {"provider", "event_id"})
)
public class PaymentWebhookEvent extends AuditableEntity {

    @Column(nullable = false, length = 40)
    private String provider;

    @Column(name = "event_id", nullable = false, length = 120)
    private String eventId;

    @Column(length = 140)
    private String eventType;

    @Column(length = 120)
    private String providerReference;

    @Column(name = "payload_json", nullable = false, columnDefinition = "text")
    private String payloadJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 40)
    private PaymentWebhookProcessingStatus processingStatus;

    @Column(nullable = false)
    private boolean processed;

    @Column(nullable = false)
    private Instant receivedAt;

    @Column
    private Instant processedAt;

    @Column(length = 500)
    private String errorMessage;

    @Column
    private Long paymentAttemptId;

    @Column
    private Long reservationId;

    public static PaymentWebhookEvent received(
            String provider,
            String eventId,
            String eventType,
            String providerReference,
            String payloadJson
    ) {
        PaymentWebhookEvent event = new PaymentWebhookEvent();
        event.provider = provider;
        event.eventId = eventId;
        event.eventType = eventType;
        event.providerReference = providerReference;
        event.payloadJson = payloadJson;
        event.processingStatus = PaymentWebhookProcessingStatus.RECEIVED;
        event.processed = false;
        event.receivedAt = Instant.now();
        return event;
    }

    public void markProcessed(Long paymentAttemptId, Long reservationId) {
        this.processingStatus = PaymentWebhookProcessingStatus.PROCESSED;
        this.processed = true;
        this.paymentAttemptId = paymentAttemptId;
        this.reservationId = reservationId;
        this.processedAt = Instant.now();
        this.errorMessage = null;
    }

    public void markIgnored(String reason, Long paymentAttemptId, Long reservationId) {
        this.processingStatus = PaymentWebhookProcessingStatus.IGNORED;
        this.processed = true;
        this.errorMessage = reason;
        this.paymentAttemptId = paymentAttemptId;
        this.reservationId = reservationId;
        this.processedAt = Instant.now();
    }

    public void markFailed(String reason, Long paymentAttemptId, Long reservationId) {
        this.processingStatus = PaymentWebhookProcessingStatus.FAILED;
        this.processed = false;
        this.errorMessage = reason;
        this.paymentAttemptId = paymentAttemptId;
        this.reservationId = reservationId;
        this.processedAt = Instant.now();
    }

    public String getProvider() {
        return provider;
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public PaymentWebhookProcessingStatus getProcessingStatus() {
        return processingStatus;
    }

    public boolean isProcessed() {
        return processed;
    }

    public Long getPaymentAttemptId() {
        return paymentAttemptId;
    }

    public Long getReservationId() {
        return reservationId;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
