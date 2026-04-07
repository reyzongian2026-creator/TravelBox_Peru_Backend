package com.tuempresa.storage.payments.infrastructure.validation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Service responsible for validating monetary amounts in the payment pipeline.
 *
 * <p>All comparisons use {@link BigDecimal} arithmetic normalised to 2 decimal
 * places so that floating-point rounding issues are avoided. Configurable
 * bounds (min/max per transaction, max daily per user) are injected from
 * application properties.</p>
 */
@Component
public class AmountValidator {

    private static final Logger log = LoggerFactory.getLogger(AmountValidator.class);

    /** Scale used for all monetary comparisons (2 decimal places). */
    private static final int MONEY_SCALE = 2;

    private final BigDecimal minAmount;
    private final BigDecimal maxAmount;
    private final BigDecimal maxDailyAmount;

    public AmountValidator(
            @Value("${app.payments.amount-validation.min-transaction-amount:5.00}")
            BigDecimal minAmount,
            @Value("${app.payments.amount-validation.max-transaction-amount:50000.00}")
            BigDecimal maxAmount,
            @Value("${app.payments.amount-validation.max-daily-amount-per-user:100000.00}")
            BigDecimal maxDailyAmount) {
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
        this.maxDailyAmount = maxDailyAmount;

        log.info("AmountValidator initialized: min={}, max={}, maxDaily={}",
                minAmount, maxAmount, maxDailyAmount);
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Checks whether two amounts are exactly equal after normalizing both
     * to 2 decimal places.
     *
     * <p>Returns {@code false} if either value is {@code null} or zero
     * (zero-amount payments are never considered valid).</p>
     *
     * @param expected the expected amount (e.g., from the reservation)
     * @param actual   the actual amount (e.g., from the payment provider)
     * @return {@code true} if both amounts are non-null, positive, and equal
     */
    public boolean isExact(BigDecimal expected, BigDecimal actual) {
        if (expected == null || actual == null) {
            log.debug("isExact: null amount detected (expected={}, actual={})", expected, actual);
            return false;
        }

        BigDecimal normExpected = normalize(expected);
        BigDecimal normActual = normalize(actual);

        // Reject zero amounts
        if (normExpected.signum() == 0 || normActual.signum() == 0) {
            log.debug("isExact: zero amount detected (expected={}, actual={})",
                    normExpected, normActual);
            return false;
        }

        boolean result = normExpected.compareTo(normActual) == 0;
        if (!result) {
            log.debug("isExact: mismatch (expected={}, actual={})", normExpected, normActual);
        }
        return result;
    }

    /**
     * Checks whether a given amount falls within the specified inclusive bounds.
     *
     * @param amount the amount to check
     * @param min    the inclusive lower bound
     * @param max    the inclusive upper bound
     * @return {@code true} if {@code min <= amount <= max}
     * @throws IllegalArgumentException if any parameter is {@code null}
     */
    public boolean isWithinBounds(BigDecimal amount, BigDecimal min, BigDecimal max) {
        if (amount == null || min == null || max == null) {
            throw new IllegalArgumentException(
                    "amount, min, and max must not be null");
        }

        BigDecimal normAmount = normalize(amount);
        BigDecimal normMin = normalize(min);
        BigDecimal normMax = normalize(max);

        return normAmount.compareTo(normMin) >= 0
                && normAmount.compareTo(normMax) <= 0;
    }

    /**
     * Validates that the given amount is strictly positive (greater than zero).
     *
     * @param amount    the amount to validate
     * @param fieldName descriptive field name used in the exception message
     * @throws IllegalArgumentException if the amount is {@code null}, zero,
     *                                  or negative
     */
    public void validatePositive(BigDecimal amount, String fieldName) {
        if (amount == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    fieldName + " must be positive, but was " + amount.toPlainString());
        }
    }

    /**
     * Validates that a transaction amount satisfies the configured constraints:
     * positive, within min/max bounds.
     *
     * @param amount the transaction amount to validate
     * @throws IllegalArgumentException if the amount is invalid
     */
    public void validateTransactionAmount(BigDecimal amount) {
        validatePositive(amount, "Transaction amount");

        if (!isWithinBounds(amount, minAmount, maxAmount)) {
            throw new IllegalArgumentException(
                    String.format("Transaction amount %s is out of allowed range [%s, %s]",
                            amount.toPlainString(),
                            minAmount.toPlainString(),
                            maxAmount.toPlainString()));
        }
    }

    /**
     * Validates that a user's cumulative daily amount (including this
     * transaction) does not exceed the configured daily limit.
     *
     * @param currentDailyTotal the user's current daily total before this transaction
     * @param transactionAmount the amount of the current transaction
     * @throws IllegalArgumentException if the combined total exceeds the daily limit
     */
    public void validateDailyLimit(BigDecimal currentDailyTotal, BigDecimal transactionAmount) {
        if (currentDailyTotal == null) {
            currentDailyTotal = BigDecimal.ZERO;
        }
        validatePositive(transactionAmount, "Transaction amount");

        BigDecimal projectedTotal = normalize(currentDailyTotal).add(normalize(transactionAmount));

        if (projectedTotal.compareTo(normalize(maxDailyAmount)) > 0) {
            throw new IllegalArgumentException(
                    String.format("Daily amount limit exceeded: projected total %s > max %s",
                            projectedTotal.toPlainString(),
                            maxDailyAmount.toPlainString()));
        }
    }

    // =========================================================================
    // Getters for configured limits
    // =========================================================================

    /** Returns the configured minimum transaction amount. */
    public BigDecimal getMinAmount() {
        return minAmount;
    }

    /** Returns the configured maximum transaction amount. */
    public BigDecimal getMaxAmount() {
        return maxAmount;
    }

    /** Returns the configured maximum daily amount per user. */
    public BigDecimal getMaxDailyAmount() {
        return maxDailyAmount;
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    private BigDecimal normalize(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
