package com.tuempresa.storage.payments.domain;

import com.tuempresa.storage.reservations.domain.Reservation;
import com.tuempresa.storage.shared.infrastructure.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_attempts")
public class PaymentAttempt extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentStatus status;

    @Column(nullable = false, unique = true, length = 80)
    private String providerReference;

    @Column(length = 120)
    private String gatewayStatus;

    @Column(length = 500)
    private String gatewayMessage;

    @Column(name = "refund_amount", precision = 12, scale = 2)
    private BigDecimal refundAmount;

    @Column(name = "refund_fee", precision = 12, scale = 2)
    private BigDecimal refundFee;

    @Column(name = "refund_reason", length = 500)
    private String refundReason;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    public static PaymentAttempt pending(Reservation reservation, BigDecimal amount) {
        PaymentAttempt paymentAttempt = new PaymentAttempt();
        paymentAttempt.reservation = reservation;
        paymentAttempt.amount = amount;
        paymentAttempt.status = PaymentStatus.PENDING;
        paymentAttempt.providerReference = "MOCK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return paymentAttempt;
    }

    public Reservation getReservation() {
        return reservation;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public String getGatewayStatus() {
        return gatewayStatus;
    }

    public String getGatewayMessage() {
        return gatewayMessage;
    }

    public BigDecimal getRefundAmount() {
        return refundAmount;
    }

    public BigDecimal getRefundFee() {
        return refundFee;
    }

    public String getRefundReason() {
        return refundReason;
    }

    public Instant getRefundedAt() {
        return refundedAt;
    }

    public boolean isPending() {
        return status == PaymentStatus.PENDING;
    }

    public boolean isConfirmed() {
        return status == PaymentStatus.CONFIRMED;
    }

    public boolean isRefunded() {
        return status == PaymentStatus.REFUNDED;
    }

    public void registerProviderReference(String providerReference) {
        if (providerReference != null && !providerReference.isBlank()) {
            this.providerReference = providerReference;
        }
    }

    public void registerGatewayOutcome(String status, String message) {
        if (status != null && !status.isBlank()) {
            this.gatewayStatus = status;
        }
        if (message != null && !message.isBlank()) {
            this.gatewayMessage = message;
        }
    }

    public void confirm(String providerReference) {
        this.status = PaymentStatus.CONFIRMED;
        registerProviderReference(providerReference);
    }

    public void fail(String providerReference) {
        this.status = PaymentStatus.FAILED;
        registerProviderReference(providerReference);
    }

    public void refund(
            String providerReference,
            BigDecimal refundAmount,
            BigDecimal refundFee,
            String refundReason
    ) {
        this.status = PaymentStatus.REFUNDED;
        this.refundAmount = normalizeMoney(refundAmount);
        this.refundFee = normalizeMoney(refundFee);
        this.refundReason = normalizeText(refundReason, 500);
        this.refundedAt = Instant.now();
        registerProviderReference(providerReference);
    }

    private BigDecimal normalizeMoney(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private String normalizeText(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized.length() > maxLength ? normalized.substring(0, maxLength) : normalized;
    }
}
