package com.tuempresa.storage.ops.domain;

import com.tuempresa.storage.reservations.domain.Reservation;
import com.tuempresa.storage.shared.infrastructure.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Locale;

@Entity
@Table(name = "qr_handoff_cases")
public class QrHandoffCase extends AuditableEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false, unique = true)
    private Reservation reservation;

    @Column(name = "customer_language", nullable = false, length = 10)
    private String customerLanguage = "es";

    @Column(name = "customer_qr_payload", nullable = false, length = 180)
    private String customerQrPayload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private QrHandoffStage stage = QrHandoffStage.DRAFT;

    @Column(name = "bag_tag_id", unique = true, length = 80)
    private String bagTagId;

    @Column(name = "bag_tag_qr_payload", length = 220)
    private String bagTagQrPayload;

    @Column(name = "bag_units", nullable = false)
    private int bagUnits = 1;

    @Column(name = "pickup_pin_hash", length = 120)
    private String pickupPinHash;

    @Column(name = "pickup_pin_preview", length = 12)
    private String pickupPinPreview;

    @Column(name = "pin_expires_at")
    private Instant pinExpiresAt;

    @Column(name = "pin_attempt_count", nullable = false)
    private int pinAttemptCount = 0;

    @Column(name = "pin_locked_until")
    private Instant pinLockedUntil;

    @Column(name = "identity_validated", nullable = false)
    private boolean identityValidated = false;

    @Column(name = "luggage_matched", nullable = false)
    private boolean luggageMatched = false;

    @Column(name = "operator_approval_requested", nullable = false)
    private boolean operatorApprovalRequested = false;

    @Column(name = "operator_approval_granted", nullable = false)
    private boolean operatorApprovalGranted = false;

    @Column(name = "latest_message_for_customer", length = 320)
    private String latestMessageForCustomer;

    @Column(name = "latest_message_translated", length = 320)
    private String latestMessageTranslated;

    @Column(name = "delivery_completed", nullable = false)
    private boolean deliveryCompleted = false;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    public static QrHandoffCase createForReservation(Reservation reservation, String customerLanguage) {
        QrHandoffCase handoffCase = new QrHandoffCase();
        handoffCase.reservation = reservation;
        handoffCase.customerLanguage = normalizeLanguage(customerLanguage);
        handoffCase.customerQrPayload = customerPayloadFor(reservation.getQrCode());
        handoffCase.stage = QrHandoffStage.DRAFT;
        handoffCase.bagUnits = Math.max(1, reservation.getEstimatedItems());
        return handoffCase;
    }

    public Reservation getReservation() {
        return reservation;
    }

    public String getCustomerLanguage() {
        return customerLanguage;
    }

    public String getCustomerQrPayload() {
        return customerQrPayload;
    }

    public QrHandoffStage getStage() {
        return stage;
    }

    public String getBagTagId() {
        return bagTagId;
    }

    public String getBagTagQrPayload() {
        return bagTagQrPayload;
    }

    public int getBagUnits() {
        return bagUnits;
    }

    public String getPickupPinHash() {
        return pickupPinHash;
    }

    public String getPickupPinPreview() {
        return pickupPinPreview;
    }

    public Instant getPinExpiresAt() {
        return pinExpiresAt;
    }

    public int getPinAttemptCount() {
        return pinAttemptCount;
    }

    public Instant getPinLockedUntil() {
        return pinLockedUntil;
    }

    public boolean isIdentityValidated() {
        return identityValidated;
    }

    public boolean isLuggageMatched() {
        return luggageMatched;
    }

    public boolean isOperatorApprovalRequested() {
        return operatorApprovalRequested;
    }

    public boolean isOperatorApprovalGranted() {
        return operatorApprovalGranted;
    }

    public String getLatestMessageForCustomer() {
        return latestMessageForCustomer;
    }

    public String getLatestMessageTranslated() {
        return latestMessageTranslated;
    }

    public boolean isDeliveryCompleted() {
        return deliveryCompleted;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public void markQrValidated(String customerLanguage) {
        this.customerLanguage = normalizeLanguage(customerLanguage == null ? this.customerLanguage : customerLanguage);
        this.customerQrPayload = customerPayloadFor(reservation.getQrCode());
        advanceToAtLeast(QrHandoffStage.QR_VALIDATED);
    }

    public void assignBagTag(String bagTagId, int bagUnits) {
        this.bagTagId = bagTagId == null ? null : bagTagId.trim().toUpperCase(Locale.ROOT);
        this.bagTagQrPayload = bagPayloadFor(reservation.getQrCode(), this.bagTagId);
        this.bagUnits = Math.max(1, bagUnits);
        advanceToAtLeast(QrHandoffStage.BAG_TAGGED);
    }

    public void markStoredAtWarehouse() {
        advanceToAtLeast(QrHandoffStage.STORED_AT_WAREHOUSE);
    }

    public void markReadyForPickup(String pinHash, String pinPreview, Instant pinExpiresAt) {
        this.pickupPinHash = pinHash;
        this.pickupPinPreview = trimToNull(pinPreview, 12);
        this.pinExpiresAt = pinExpiresAt;
        this.pinAttemptCount = 0;
        this.pinLockedUntil = null;
        advanceToAtLeast(QrHandoffStage.READY_FOR_PICKUP);
    }

    public void markPickupPinValidated() {
        this.pinAttemptCount = 0;
        this.pinLockedUntil = null;
        this.pickupPinPreview = null;
        advanceToAtLeast(QrHandoffStage.PICKUP_PIN_VALIDATED);
    }

    public void registerFailedPinAttempt(Instant now, int maxAttempts, int lockSeconds) {
        this.pinAttemptCount = Math.max(0, this.pinAttemptCount) + 1;
        if (this.pinAttemptCount >= Math.max(1, maxAttempts)) {
            this.pinAttemptCount = 0;
            this.pinLockedUntil = now.plusSeconds(Math.max(30, lockSeconds));
        }
    }

    public boolean isPinLocked(Instant now) {
        return pinLockedUntil != null && pinLockedUntil.isAfter(now);
    }

    public void setDeliveryIdentityValidated(boolean value) {
        this.identityValidated = value;
        if (value) {
            advanceToAtLeast(QrHandoffStage.DELIVERY_IDENTITY_VALIDATED);
        }
    }

    public void setLuggageMatched(boolean value) {
        this.luggageMatched = value;
        if (value) {
            advanceToAtLeast(QrHandoffStage.DELIVERY_LUGGAGE_VALIDATED);
        }
    }

    public void markApprovalRequested(String messageForCustomer, String translatedMessage) {
        this.operatorApprovalRequested = true;
        this.operatorApprovalGranted = false;
        this.latestMessageForCustomer = trimToNull(messageForCustomer, 320);
        this.latestMessageTranslated = trimToNull(translatedMessage, 320);
        advanceToAtLeast(QrHandoffStage.DELIVERY_APPROVAL_PENDING);
    }

    public void markApprovalGranted(String pinHash, String pinPreview, Instant pinExpiresAt) {
        this.operatorApprovalRequested = true;
        this.operatorApprovalGranted = true;
        this.pickupPinHash = pinHash;
        this.pickupPinPreview = trimToNull(pinPreview, 12);
        this.pinExpiresAt = pinExpiresAt;
        this.pinAttemptCount = 0;
        this.pinLockedUntil = null;
        advanceToAtLeast(QrHandoffStage.DELIVERY_APPROVAL_GRANTED);
    }

    public void markDeliveryCompleted() {
        this.deliveryCompleted = true;
        this.deliveredAt = Instant.now();
        this.pinAttemptCount = 0;
        this.pinLockedUntil = null;
        this.pickupPinPreview = null;
        advanceToAtLeast(QrHandoffStage.DELIVERY_COMPLETED);
    }

    private void advanceToAtLeast(QrHandoffStage target) {
        if (this.stage.ordinal() < target.ordinal()) {
            this.stage = target;
        }
    }

    public static String customerPayloadFor(String reservationCode) {
        return "TRAVELBOX|RESERVATION|" + reservationCode;
    }

    private static String bagPayloadFor(String reservationCode, String bagTagId) {
        if (bagTagId == null || bagTagId.isBlank()) {
            return null;
        }
        return "TRAVELBOX|BAG|" + bagTagId + "|RES|" + reservationCode;
    }

    private static String normalizeLanguage(String value) {
        if (value == null || value.isBlank()) {
            return "es";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "es", "en" -> normalized;
            default -> "es";
        };
    }

    private String trimToNull(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, maxLength);
    }
}
