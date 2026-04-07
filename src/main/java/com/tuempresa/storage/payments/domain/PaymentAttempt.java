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
import jakarta.persistence.Version;

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

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "idempotency_key", unique = true, length = 80)
    private String idempotencyKey;

    @Column(name = "provider_fee_amount", precision = 12, scale = 2)
    private BigDecimal providerFeeAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "booking_type", length = 20)
    private BookingType bookingType;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancellation_policy_applied", length = 30)
    private CancellationPolicyType cancellationPolicyApplied;

    @Column(name = "payment_method_label", length = 60)
    private String paymentMethodLabel;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promo_code_id")
    private PromoCode promoCode;

    @Column(name = "discount_amount", precision = 12, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "expected_customer_email", length = 255)
    private String expectedCustomerEmail;

    @Column(name = "expected_customer_name", length = 255)
    private String expectedCustomerName;

    @Column(name = "expected_customer_phone", length = 30)
    private String expectedCustomerPhone;

    @Column(name = "expected_method", length = 50)
    private String expectedMethod;

    @Column(name = "manual_transfer_requested_at")
    private Instant manualTransferRequestedAt;

    // =========================================================================
    // QR Signing fields (V43)
    // =========================================================================

    /** Base-64-encoded SHA-512 hash of the QR code digital signature. */
    @Column(name = "qr_signature_hash", length = 88)
    private String qrSignatureHash;

    /** Unix epoch millis when the QR code signature was generated. */
    @Column(name = "qr_signature_timestamp")
    private Long qrSignatureTimestamp;

    /** Whether the QR signature was successfully verified on the server side. */
    @Column(name = "qr_verification_success")
    private Boolean qrVerificationSuccess;

    /** Instant at which the QR signature verification was performed. */
    @Column(name = "qr_verification_timestamp")
    private Instant qrVerificationTimestamp;

    // =========================================================================
    // Identity Verification fields (V44)
    // =========================================================================

    /** Phone number of the payer (E.164 format, max 20 chars). */
    @Column(name = "payer_phone_number", length = 20)
    private String payerPhoneNumber;

    /** Base-64-encoded hash of the payer's identity document for verification. */
    @Column(name = "payer_identity_hash", length = 88)
    private String payerIdentityHash;

    /** Confidence score returned by the identity verification provider (0.00 - 1.00). */
    @Column(name = "identity_verification_confidence", precision = 3, scale = 2)
    private BigDecimal identityVerificationConfidence;

    /** Status of the identity verification (e.g. PENDING, VERIFIED, REJECTED). */
    @Column(name = "identity_verification_status", length = 30)
    private String identityVerificationStatus;

    // =========================================================================
    // Atomicity fields (V45)
    // =========================================================================

    /**
     * Client-supplied idempotency key (UUID v4) that guarantees at-most-once
     * processing of payment confirmation requests. This is separate from the
     * existing {@link #idempotencyKey} which is system-generated.
     */
    @Column(name = "client_idempotency_key", length = 36)
    private String clientIdempotencyKey;

    public static PaymentAttempt pending(Reservation reservation, BigDecimal amount) {
        PaymentAttempt paymentAttempt = new PaymentAttempt();
        paymentAttempt.reservation = reservation;
        paymentAttempt.amount = amount;
        paymentAttempt.status = PaymentStatus.PENDING;
        paymentAttempt.providerReference = "MOCK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        paymentAttempt.idempotencyKey = UUID.randomUUID().toString();
        return paymentAttempt;
    }

    public Reservation getReservation() {
        return reservation;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void overrideAmount(BigDecimal newAmount) {
        this.amount = newAmount;
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
        return status == PaymentStatus.REFUNDED || status == PaymentStatus.PARTIALLY_REFUNDED;
    }

    public boolean isRefundPending() {
        return status == PaymentStatus.REFUND_PENDING;
    }

    public Long getVersion() {
        return version;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public BigDecimal getProviderFeeAmount() {
        return providerFeeAmount;
    }

    public BookingType getBookingType() {
        return bookingType;
    }

    public CancellationPolicyType getCancellationPolicyApplied() {
        return cancellationPolicyApplied;
    }

    public String getPaymentMethodLabel() {
        return paymentMethodLabel;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public void setProviderFeeAmount(BigDecimal providerFeeAmount) {
        this.providerFeeAmount = normalizeMoney(providerFeeAmount);
    }

    public void setBookingType(BookingType bookingType) {
        this.bookingType = bookingType;
    }

    public void setCancellationPolicyApplied(CancellationPolicyType policyType) {
        this.cancellationPolicyApplied = policyType;
    }

    public void setPaymentMethodLabel(String label) {
        this.paymentMethodLabel = normalizeText(label, 60);
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
        this.confirmedAt = Instant.now();
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
            String refundReason) {
        this.status = PaymentStatus.REFUNDED;
        this.refundAmount = normalizeMoney(refundAmount);
        this.refundFee = normalizeMoney(refundFee);
        this.refundReason = normalizeText(refundReason, 500);
        this.refundedAt = Instant.now();
        registerProviderReference(providerReference);
    }

    public void markRefundPending() {
        this.status = PaymentStatus.REFUND_PENDING;
    }

    public void markRefundFailed(String reason) {
        this.status = PaymentStatus.REFUND_FAILED;
        this.refundReason = normalizeText(reason, 500);
    }

    public void refundWithPolicy(
            String providerReference,
            BigDecimal refundAmount,
            BigDecimal refundFee,
            BigDecimal providerFee,
            String refundReason,
            BookingType bookingType,
            CancellationPolicyType policyType) {
        this.status = (refundAmount != null && refundAmount.compareTo(this.amount) < 0)
                ? PaymentStatus.PARTIALLY_REFUNDED
                : PaymentStatus.REFUNDED;
        this.refundAmount = normalizeMoney(refundAmount);
        this.refundFee = normalizeMoney(refundFee);
        this.providerFeeAmount = normalizeMoney(providerFee);
        this.refundReason = normalizeText(refundReason, 500);
        this.refundedAt = Instant.now();
        this.bookingType = bookingType;
        this.cancellationPolicyApplied = policyType;
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

    public PromoCode getPromoCode() {
        return promoCode;
    }

    public void setPromoCode(PromoCode promoCode) {
        this.promoCode = promoCode;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }

    public String getExpectedCustomerEmail() {
        return expectedCustomerEmail;
    }

    public void setExpectedCustomerEmail(String expectedCustomerEmail) {
        this.expectedCustomerEmail = expectedCustomerEmail;
    }

    public String getExpectedCustomerName() {
        return expectedCustomerName;
    }

    public void setExpectedCustomerName(String expectedCustomerName) {
        this.expectedCustomerName = expectedCustomerName;
    }

    public String getExpectedCustomerPhone() {
        return expectedCustomerPhone;
    }

    public void setExpectedCustomerPhone(String expectedCustomerPhone) {
        this.expectedCustomerPhone = expectedCustomerPhone;
    }

    public String getExpectedMethod() {
        return expectedMethod;
    }

    public void setExpectedMethod(String expectedMethod) {
        this.expectedMethod = expectedMethod;
    }

    public Instant getManualTransferRequestedAt() {
        return manualTransferRequestedAt;
    }

    public void setManualTransferRequestedAt(Instant manualTransferRequestedAt) {
        this.manualTransferRequestedAt = manualTransferRequestedAt;
    }

    // =========================================================================
    // QR Signing accessors (V43)
    // =========================================================================

    /** Returns the Base-64-encoded SHA-512 hash of the QR code signature. */
    public String getQrSignatureHash() {
        return qrSignatureHash;
    }

    public void setQrSignatureHash(String qrSignatureHash) {
        this.qrSignatureHash = qrSignatureHash;
    }

    /** Returns the Unix epoch millis when the QR code signature was generated. */
    public Long getQrSignatureTimestamp() {
        return qrSignatureTimestamp;
    }

    public void setQrSignatureTimestamp(Long qrSignatureTimestamp) {
        this.qrSignatureTimestamp = qrSignatureTimestamp;
    }

    /** Returns whether the QR signature verification succeeded. */
    public Boolean getQrVerificationSuccess() {
        return qrVerificationSuccess;
    }

    public void setQrVerificationSuccess(Boolean qrVerificationSuccess) {
        this.qrVerificationSuccess = qrVerificationSuccess;
    }

    /** Returns the instant at which QR verification was performed. */
    public Instant getQrVerificationTimestamp() {
        return qrVerificationTimestamp;
    }

    public void setQrVerificationTimestamp(Instant qrVerificationTimestamp) {
        this.qrVerificationTimestamp = qrVerificationTimestamp;
    }

    // =========================================================================
    // Identity Verification accessors (V44)
    // =========================================================================

    /** Returns the payer's phone number. */
    public String getPayerPhoneNumber() {
        return payerPhoneNumber;
    }

    public void setPayerPhoneNumber(String payerPhoneNumber) {
        this.payerPhoneNumber = payerPhoneNumber;
    }

    /** Returns the Base-64-encoded hash of the payer's identity document. */
    public String getPayerIdentityHash() {
        return payerIdentityHash;
    }

    public void setPayerIdentityHash(String payerIdentityHash) {
        this.payerIdentityHash = payerIdentityHash;
    }

    /** Returns the identity verification confidence score (0.00 - 1.00). */
    public BigDecimal getIdentityVerificationConfidence() {
        return identityVerificationConfidence;
    }

    public void setIdentityVerificationConfidence(BigDecimal identityVerificationConfidence) {
        this.identityVerificationConfidence = identityVerificationConfidence;
    }

    /** Returns the identity verification status (e.g. PENDING, VERIFIED, REJECTED). */
    public String getIdentityVerificationStatus() {
        return identityVerificationStatus;
    }

    public void setIdentityVerificationStatus(String identityVerificationStatus) {
        this.identityVerificationStatus = identityVerificationStatus;
    }

    // =========================================================================
    // Atomicity accessors (V45)
    // =========================================================================

    /** Returns the client-supplied idempotency key for at-most-once processing. */
    public String getClientIdempotencyKey() {
        return clientIdempotencyKey;
    }

    public void setClientIdempotencyKey(String clientIdempotencyKey) {
        this.clientIdempotencyKey = clientIdempotencyKey;
    }
}
