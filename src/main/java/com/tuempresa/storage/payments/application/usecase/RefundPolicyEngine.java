package com.tuempresa.storage.payments.application.usecase;

import com.tuempresa.storage.payments.domain.BookingType;
import com.tuempresa.storage.payments.domain.CancellationPolicyType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Calculates refund amounts and policy type based on booking type (IMMEDIATE vs
 * ADVANCE),
 * elapsed time since payment, and time remaining until reservation start.
 * <p>
 * All thresholds and percentages are configurable via application properties.
 * The engine is stateless — it receives inputs and returns a
 * {@link RefundCalculation}.
 */
@Component
public class RefundPolicyEngine {

    private static final Logger log = LoggerFactory.getLogger(RefundPolicyEngine.class);

    // ── Booking type threshold ──
    private final long immediateBookingThresholdHours;

    // ── Immediate booking policy ──
    private final long immediateFullRefundMinutes;
    private final BigDecimal immediatePartialFeePercent;

    // ── Advance booking policy ──
    private final long advanceFullRefundHours;
    private final long advancePartialLowWindowHours;
    private final BigDecimal advancePartialLowFeePercent;
    private final long advancePartialHighWindowHours;
    private final BigDecimal advancePartialHighFeePercent;

    // ── Provider fee modeling (feature flags) ──
    private final boolean enableProviderFeeModeling;
    private final boolean enablePassProviderFeeToCustomer;
    private final boolean enablePaymentMethodSurcharge;
    private final boolean providerFeeRefundable;
    private final String providerFeeMode; // ABSORB_BY_BUSINESS | PASS_TO_CUSTOMER_IF_ALLOWED

    public RefundPolicyEngine(
            @Value("${app.payments.refunds.immediate-booking-threshold-hours:6}") long immediateBookingThresholdHours,
            @Value("${app.payments.refunds.immediate-full-refund-minutes:5}") long immediateFullRefundMinutes,
            @Value("${app.payments.refunds.immediate-partial-fee-percent:15.00}") BigDecimal immediatePartialFeePercent,
            @Value("${app.payments.refunds.advance-full-refund-hours:24}") long advanceFullRefundHours,
            @Value("${app.payments.refunds.advance-partial-low-window-hours:6}") long advancePartialLowWindowHours,
            @Value("${app.payments.refunds.advance-partial-low-fee-percent:10.00}") BigDecimal advancePartialLowFeePercent,
            @Value("${app.payments.refunds.advance-partial-high-window-hours:1}") long advancePartialHighWindowHours,
            @Value("${app.payments.refunds.advance-partial-high-fee-percent:25.00}") BigDecimal advancePartialHighFeePercent,
            @Value("${app.payments.provider-fees.enable-modeling:false}") boolean enableProviderFeeModeling,
            @Value("${app.payments.provider-fees.enable-pass-to-customer:false}") boolean enablePassProviderFeeToCustomer,
            @Value("${app.payments.provider-fees.enable-payment-method-surcharge:false}") boolean enablePaymentMethodSurcharge,
            @Value("${app.payments.provider-fees.refundable:false}") boolean providerFeeRefundable,
            @Value("${app.payments.provider-fees.mode:ABSORB_BY_BUSINESS}") String providerFeeMode) {
        this.immediateBookingThresholdHours = immediateBookingThresholdHours;
        this.immediateFullRefundMinutes = immediateFullRefundMinutes;
        this.immediatePartialFeePercent = immediatePartialFeePercent;
        this.advanceFullRefundHours = advanceFullRefundHours;
        this.advancePartialLowWindowHours = advancePartialLowWindowHours;
        this.advancePartialLowFeePercent = advancePartialLowFeePercent;
        this.advancePartialHighWindowHours = advancePartialHighWindowHours;
        this.advancePartialHighFeePercent = advancePartialHighFeePercent;
        this.enableProviderFeeModeling = enableProviderFeeModeling;
        this.enablePassProviderFeeToCustomer = enablePassProviderFeeToCustomer;
        this.enablePaymentMethodSurcharge = enablePaymentMethodSurcharge;
        this.providerFeeRefundable = providerFeeRefundable;
        this.providerFeeMode = providerFeeMode;
    }

    /**
     * Determine whether a booking is IMMEDIATE or ADVANCE based on the
     * time between purchase and reservation start.
     */
    public BookingType classifyBooking(Instant purchasedAt, Instant reservationStartAt) {
        Duration timeToStart = Duration.between(purchasedAt, reservationStartAt);
        if (timeToStart.toHours() < immediateBookingThresholdHours) {
            return BookingType.IMMEDIATE;
        }
        return BookingType.ADVANCE;
    }

    /**
     * Calculate the refund amount, fee, and policy type for a cancellation request.
     *
     * @param grossPaidAmount    Total amount the customer paid
     * @param paymentConfirmedAt When the payment was confirmed
     * @param reservationStartAt When the reservation is scheduled to start
     * @param cancellationAt     When the cancellation is being requested (usually
     *                           Instant.now())
     * @param providerFeeAmount  Provider fee for this transaction (0 if
     *                           unknown/disabled)
     */
    public RefundCalculation calculate(
            BigDecimal grossPaidAmount,
            Instant paymentConfirmedAt,
            Instant reservationStartAt,
            Instant cancellationAt,
            BigDecimal providerFeeAmount) {

        BigDecimal amount = normalize(grossPaidAmount);
        BigDecimal providerFee = normalize(providerFeeAmount);
        BookingType bookingType = classifyBooking(paymentConfirmedAt, reservationStartAt);

        CancellationPolicyType policyType;
        BigDecimal cancellationPenalty;
        String policyWindow;

        if (bookingType == BookingType.IMMEDIATE) {
            long minutesSincePayment = Duration.between(paymentConfirmedAt, cancellationAt).toMinutes();

            if (minutesSincePayment <= immediateFullRefundMinutes) {
                policyType = CancellationPolicyType.FULL_REFUND;
                cancellationPenalty = BigDecimal.ZERO;
                policyWindow = "immediate_full_refund (within " + immediateFullRefundMinutes + " min)";
            } else if (cancellationAt.isBefore(reservationStartAt)) {
                policyType = CancellationPolicyType.PARTIAL_REFUND;
                cancellationPenalty = amount.multiply(immediatePartialFeePercent)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                policyWindow = "immediate_partial (" + immediatePartialFeePercent + "% fee)";
            } else {
                // After reservation start — no refund for immediate bookings
                policyType = CancellationPolicyType.NO_REFUND;
                cancellationPenalty = amount;
                policyWindow = "immediate_no_refund (after start)";
            }
        } else {
            // ADVANCE booking — policy based on time until reservation start
            long hoursUntilStart = Duration.between(cancellationAt, reservationStartAt).toHours();

            if (hoursUntilStart >= advanceFullRefundHours) {
                policyType = CancellationPolicyType.FULL_REFUND;
                cancellationPenalty = BigDecimal.ZERO;
                policyWindow = "advance_full_refund (>=" + advanceFullRefundHours + "h before start)";
            } else if (hoursUntilStart >= advancePartialLowWindowHours) {
                policyType = CancellationPolicyType.PARTIAL_REFUND;
                cancellationPenalty = amount.multiply(advancePartialLowFeePercent)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                policyWindow = "advance_partial_low (" + advancePartialLowFeePercent + "% fee, " +
                        advancePartialLowWindowHours + "-" + advanceFullRefundHours + "h)";
            } else if (hoursUntilStart >= advancePartialHighWindowHours) {
                policyType = CancellationPolicyType.PARTIAL_REFUND;
                cancellationPenalty = amount.multiply(advancePartialHighFeePercent)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                policyWindow = "advance_partial_high (" + advancePartialHighFeePercent + "% fee, " +
                        advancePartialHighWindowHours + "-" + advancePartialLowWindowHours + "h)";
            } else if (cancellationAt.isBefore(reservationStartAt)) {
                policyType = CancellationPolicyType.NO_REFUND;
                cancellationPenalty = amount;
                policyWindow = "advance_no_refund (<" + advancePartialHighWindowHours + "h before start)";
            } else {
                policyType = CancellationPolicyType.NO_REFUND;
                cancellationPenalty = amount;
                policyWindow = "advance_no_refund (after start)";
            }
        }

        cancellationPenalty = cancellationPenalty.setScale(2, RoundingMode.HALF_UP);
        BigDecimal refundToCustomer = amount.subtract(cancellationPenalty).max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        log.info("Refund calculated: bookingType={}, policy={}, window={}, amount={}, penalty={}, refund={}",
                bookingType, policyType, policyWindow, amount, cancellationPenalty, refundToCustomer);

        // Provider fee tracking (always tracked internally, even if not passed to
        // customer)
        BigDecimal effectiveProviderFee = enableProviderFeeModeling ? providerFee : BigDecimal.ZERO;
        boolean feeRefundable = enableProviderFeeModeling && providerFeeRefundable;

        // Business retention = penalty + provider fee if not refundable
        BigDecimal retainedByBusiness = cancellationPenalty;
        BigDecimal netBusinessLoss = BigDecimal.ZERO;
        if (enableProviderFeeModeling && effectiveProviderFee.signum() > 0) {
            if (policyType == CancellationPolicyType.FULL_REFUND) {
                // Full refund: business absorbs provider fee
                netBusinessLoss = feeRefundable ? BigDecimal.ZERO : effectiveProviderFee;
            } else if (policyType == CancellationPolicyType.PARTIAL_REFUND) {
                // Partial: penalty retained, but provider fee might not be recoverable
                netBusinessLoss = feeRefundable ? BigDecimal.ZERO
                        : effectiveProviderFee.min(retainedByBusiness).setScale(2, RoundingMode.HALF_UP);
            }
        }

        return new RefundCalculation(
                bookingType,
                policyType,
                policyWindow,
                amount,
                cancellationPenalty,
                refundToCustomer,
                retainedByBusiness,
                effectiveProviderFee.setScale(2, RoundingMode.HALF_UP),
                feeRefundable,
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), // surcharge (disabled by default)

                false, // surchargeAllowed
                netBusinessLoss.setScale(2, RoundingMode.HALF_UP));
    }

    /**
     * Returns whether the payment method surcharge is allowed (feature flag +
     * config).
     */
    public boolean isSurchargeAllowed() {
        return enablePaymentMethodSurcharge && enablePassProviderFeeToCustomer;
    }

    public String getProviderFeeMode() {
        return providerFeeMode;
    }

    public long getImmediateBookingThresholdHours() {
        return immediateBookingThresholdHours;
    }

    public long getImmediateFullRefundMinutes() {
        return immediateFullRefundMinutes;
    }

    private BigDecimal normalize(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : value.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Immutable result of a refund calculation.
     */
    public record RefundCalculation(
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
            boolean paymentMethodSurchargeAllowed,
            BigDecimal netBusinessLoss) {
    }
}
