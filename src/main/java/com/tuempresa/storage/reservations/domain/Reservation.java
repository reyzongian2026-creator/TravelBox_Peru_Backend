package com.tuempresa.storage.reservations.domain;

import com.tuempresa.storage.shared.domain.exception.ApiException;
import com.tuempresa.storage.shared.infrastructure.persistence.AuditableEntity;
import com.tuempresa.storage.users.domain.User;
import com.tuempresa.storage.warehouses.domain.Warehouse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reservations")
public class Reservation extends AuditableEntity {

    private static final BigDecimal ZERO_AMOUNT = new BigDecimal("0.00");

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Column(nullable = false)
    private Instant startAt;

    @Column(nullable = false)
    private Instant endAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ReservationStatus status;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalPrice;

    @Column(nullable = false)
    private int estimatedItems = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationBagSize bagSize = ReservationBagSize.MEDIUM;

    @Column(nullable = false)
    private boolean pickupRequested = false;

    @Column(nullable = false)
    private boolean dropoffRequested = false;

    @Column(nullable = false)
    private boolean extraInsurance = false;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal storageAmount = ZERO_AMOUNT;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal pickupFee = ZERO_AMOUNT;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal dropoffFee = ZERO_AMOUNT;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal insuranceFee = ZERO_AMOUNT;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal latePickupSurcharge = ZERO_AMOUNT;

    @Column(nullable = false, unique = true, length = 80)
    private String qrCode;

    @Column
    private Instant expiresAt;

    @Column(length = 240)
    private String cancelReason;

    public static Reservation createPendingPayment(
            User user,
            Warehouse warehouse,
            Instant startAt,
            Instant endAt,
            BigDecimal totalPrice,
            int estimatedItems,
            ReservationBagSize bagSize,
            boolean pickupRequested,
            boolean dropoffRequested,
            boolean extraInsurance,
            BigDecimal storageAmount,
            BigDecimal pickupFee,
            BigDecimal dropoffFee,
            BigDecimal insuranceFee,
            Instant expiresAt
    ) {
        Reservation reservation = new Reservation();
        reservation.user = user;
        reservation.warehouse = warehouse;
        reservation.startAt = startAt;
        reservation.endAt = endAt;
        reservation.totalPrice = totalPrice;
        reservation.estimatedItems = Math.max(1, estimatedItems);
        reservation.bagSize = bagSize != null ? bagSize : ReservationBagSize.MEDIUM;
        reservation.pickupRequested = pickupRequested;
        reservation.dropoffRequested = dropoffRequested;
        reservation.extraInsurance = extraInsurance;
        reservation.storageAmount = storageAmount != null ? storageAmount : ZERO_AMOUNT;
        reservation.pickupFee = pickupFee != null ? pickupFee : ZERO_AMOUNT;
        reservation.dropoffFee = dropoffFee != null ? dropoffFee : ZERO_AMOUNT;
        reservation.insuranceFee = insuranceFee != null ? insuranceFee : ZERO_AMOUNT;
        reservation.status = ReservationStatus.PENDING_PAYMENT;
        reservation.expiresAt = expiresAt;
        reservation.qrCode = "TRAVELBOX-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
        return reservation;
    }

    public User getUser() {
        return user;
    }

    public Warehouse getWarehouse() {
        return warehouse;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public int getEstimatedItems() {
        return estimatedItems;
    }

    public ReservationBagSize getBagSize() {
        return bagSize;
    }

    public boolean isPickupRequested() {
        return pickupRequested;
    }

    public boolean isDropoffRequested() {
        return dropoffRequested;
    }

    public boolean isExtraInsurance() {
        return extraInsurance;
    }

    public BigDecimal getStorageAmount() {
        return storageAmount;
    }

    public BigDecimal getPickupFee() {
        return pickupFee;
    }

    public BigDecimal getDropoffFee() {
        return dropoffFee;
    }

    public BigDecimal getInsuranceFee() {
        return insuranceFee;
    }

    public BigDecimal getLatePickupSurcharge() {
        return latePickupSurcharge;
    }

    public String getQrCode() {
        return qrCode;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public boolean belongsTo(Long userId) {
        return user.getId().equals(userId);
    }

    public boolean overlaps(Instant start, Instant end) {
        return startAt.isBefore(end) && endAt.isAfter(start);
    }

    public void transitionTo(ReservationStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "INVALID_RESERVATION_TRANSITION",
                    "No se puede pasar de " + status + " a " + target + "."
            );
        }
        status = target;
    }

    public void cancel(String reason) {
        transitionTo(ReservationStatus.CANCELLED);
        this.cancelReason = reason;
    }

    public void confirmPayment() {
        transitionTo(ReservationStatus.CONFIRMED);
        expiresAt = null;
    }

    public void applyLatePickupSurcharge(BigDecimal surchargeAmount) {
        if (surchargeAmount == null) {
            return;
        }
        BigDecimal normalizedSurcharge = normalizeMoney(surchargeAmount);
        if (normalizedSurcharge.signum() <= 0) {
            return;
        }
        BigDecimal current = normalizeMoney(latePickupSurcharge);
        if (normalizedSurcharge.compareTo(current) <= 0) {
            return;
        }
        BigDecimal delta = normalizedSurcharge.subtract(current).setScale(2, RoundingMode.HALF_UP);
        latePickupSurcharge = normalizedSurcharge;
        storageAmount = normalizeMoney(storageAmount).add(delta).setScale(2, RoundingMode.HALF_UP);
        totalPrice = normalizeMoney(totalPrice).add(delta).setScale(2, RoundingMode.HALF_UP);
    }

    public void expire() {
        transitionTo(ReservationStatus.EXPIRED);
    }

    private BigDecimal normalizeMoney(BigDecimal amount) {
        if (amount == null) {
            return ZERO_AMOUNT;
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
