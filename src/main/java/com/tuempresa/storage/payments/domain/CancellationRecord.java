package com.tuempresa.storage.payments.domain;

import com.tuempresa.storage.shared.infrastructure.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * Auditable record of every cancellation/refund operation.
 * Persists the full financial breakdown, policy applied, and actor involved.
 * One CancellationRecord per cancellation attempt (successful or failed).
 */
@Entity
@Table(name = "cancellation_records")
public class CancellationRecord extends AuditableEntity {

    @Column(name = "reservation_id", nullable = false)
    private Long reservationId;

    @Column(name = "payment_attempt_id")
    private Long paymentAttemptId;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 80)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "booking_type", nullable = false, length = 20)
    private BookingType bookingType;

    @Enumerated(EnumType.STRING)
    @Column(name = "policy_type", nullable = false, length = 30)
    private CancellationPolicyType policyType;

    @Column(name = "policy_window", length = 200)
    private String policyWindow;

    @Column(name = "gross_paid_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal grossPaidAmount;

    @Column(name = "cancellation_penalty_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal cancellationPenaltyAmount;

    @Column(name = "refund_amount_to_customer", nullable = false, precision = 12, scale = 2)
    private BigDecimal refundAmountToCustomer;

    @Column(name = "retained_amount_by_business", nullable = false, precision = 12, scale = 2)
    private BigDecimal retainedAmountByBusiness;

    @Column(name = "provider_fee_amount", precision = 12, scale = 2)
    private BigDecimal providerFeeAmount;

    @Column(name = "provider_fee_refundable")
    private boolean providerFeeRefundable;

    @Column(name = "payment_method_surcharge_amount", precision = 12, scale = 2)
    private BigDecimal paymentMethodSurchargeAmount;

    @Column(name = "net_business_loss", precision = 12, scale = 2)
    private BigDecimal netBusinessLoss;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private CancellationStatus status;

    @Column(name = "refund_provider_reference", length = 120)
    private String refundProviderReference;

    @Column(name = "refund_provider_message", length = 500)
    private String refundProviderMessage;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "actor_role", length = 40)
    private String actorRole;

    @Column(name = "reservation_start_at")
    private Instant reservationStartAt;

    @Column(name = "payment_confirmed_at")
    private Instant paymentConfirmedAt;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "previous_reservation_status", length = 40)
    private String previousReservationStatus;

    @Column(name = "previous_payment_status", length = 30)
    private String previousPaymentStatus;

    protected CancellationRecord() {
    }

    public static CancellationRecord create(
            Long reservationId,
            Long paymentAttemptId,
            BookingType bookingType,
            CancellationPolicyType policyType,
            String policyWindow,
            BigDecimal grossPaidAmount,
            BigDecimal cancellationPenaltyAmount,
            BigDecimal refundAmountToCustomer,
            BigDecimal retainedAmountByBusiness,
            BigDecimal providerFeeAmount,
            boolean providerFeeRefundable,
            BigDecimal paymentMethodSurchargeAmount,
            BigDecimal netBusinessLoss,
            String reason,
            Long actorUserId,
            String actorRole,
            Instant reservationStartAt,
            Instant paymentConfirmedAt,
            String previousReservationStatus,
            String previousPaymentStatus) {
        CancellationRecord record = new CancellationRecord();
        record.reservationId = reservationId;
        record.paymentAttemptId = paymentAttemptId;
        record.idempotencyKey = UUID.randomUUID().toString();
        record.bookingType = bookingType;
        record.policyType = policyType;
        record.policyWindow = policyWindow;
        record.grossPaidAmount = norm(grossPaidAmount);
        record.cancellationPenaltyAmount = norm(cancellationPenaltyAmount);
        record.refundAmountToCustomer = norm(refundAmountToCustomer);
        record.retainedAmountByBusiness = norm(retainedAmountByBusiness);
        record.providerFeeAmount = norm(providerFeeAmount);
        record.providerFeeRefundable = providerFeeRefundable;
        record.paymentMethodSurchargeAmount = norm(paymentMethodSurchargeAmount);
        record.netBusinessLoss = norm(netBusinessLoss);
        record.status = CancellationStatus.PENDING;
        record.reason = reason;
        record.actorUserId = actorUserId;
        record.actorRole = actorRole;
        record.reservationStartAt = reservationStartAt;
        record.paymentConfirmedAt = paymentConfirmedAt;
        record.requestedAt = Instant.now();
        record.previousReservationStatus = previousReservationStatus;
        record.previousPaymentStatus = previousPaymentStatus;
        return record;
    }

    public void markRefundExecuted(String providerReference, String providerMessage) {
        this.status = CancellationStatus.REFUND_EXECUTED;
        this.refundProviderReference = providerReference;
        this.refundProviderMessage = providerMessage;
        this.completedAt = Instant.now();
    }

    public void markCompleted() {
        this.status = CancellationStatus.COMPLETED;
        if (this.completedAt == null) {
            this.completedAt = Instant.now();
        }
    }

    public void markFailed(String message) {
        this.status = CancellationStatus.FAILED;
        this.refundProviderMessage = message;
        this.completedAt = Instant.now();
    }

    public void markNoRefund() {
        this.status = CancellationStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    // ── Getters ──
    public Long getReservationId() {
        return reservationId;
    }

    public Long getPaymentAttemptId() {
        return paymentAttemptId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public BookingType getBookingType() {
        return bookingType;
    }

    public CancellationPolicyType getPolicyType() {
        return policyType;
    }

    public String getPolicyWindow() {
        return policyWindow;
    }

    public BigDecimal getGrossPaidAmount() {
        return grossPaidAmount;
    }

    public BigDecimal getCancellationPenaltyAmount() {
        return cancellationPenaltyAmount;
    }

    public BigDecimal getRefundAmountToCustomer() {
        return refundAmountToCustomer;
    }

    public BigDecimal getRetainedAmountByBusiness() {
        return retainedAmountByBusiness;
    }

    public BigDecimal getProviderFeeAmount() {
        return providerFeeAmount;
    }

    public boolean isProviderFeeRefundable() {
        return providerFeeRefundable;
    }

    public BigDecimal getPaymentMethodSurchargeAmount() {
        return paymentMethodSurchargeAmount;
    }

    public BigDecimal getNetBusinessLoss() {
        return netBusinessLoss;
    }

    public CancellationStatus getStatus() {
        return status;
    }

    public String getRefundProviderReference() {
        return refundProviderReference;
    }

    public String getRefundProviderMessage() {
        return refundProviderMessage;
    }

    public String getReason() {
        return reason;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public String getActorRole() {
        return actorRole;
    }

    public Instant getReservationStartAt() {
        return reservationStartAt;
    }

    public Instant getPaymentConfirmedAt() {
        return paymentConfirmedAt;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public String getPreviousReservationStatus() {
        return previousReservationStatus;
    }

    public String getPreviousPaymentStatus() {
        return previousPaymentStatus;
    }

    private static BigDecimal norm(BigDecimal v) {
        return v == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : v.setScale(2, RoundingMode.HALF_UP);
    }

    public enum CancellationStatus {
        PENDING,
        REFUND_EXECUTED,
        COMPLETED,
        FAILED
    }
}
