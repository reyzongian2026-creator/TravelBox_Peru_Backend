package com.tuempresa.storage.payments.infrastructure.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AmountValidatorTest {

    private AmountValidator amountValidator;

    @BeforeEach
    void setUp() {
        amountValidator = new AmountValidator(
                new BigDecimal("5.00"),
                new BigDecimal("500.00"),
                new BigDecimal("1000.00")
        );
    }

    @Test
    void shouldCompareExactAmountsAfterNormalization() {
        assertTrue(amountValidator.isExact(new BigDecimal("10"), new BigDecimal("10.00")));
        assertTrue(amountValidator.isExact(new BigDecimal("10.005"), new BigDecimal("10.01")));
        assertFalse(amountValidator.isExact(new BigDecimal("0"), new BigDecimal("0.00")));
        assertFalse(amountValidator.isExact(new BigDecimal("10.00"), new BigDecimal("10.02")));
    }

    @Test
    void shouldValidateBoundsAndPositiveAmounts() {
        assertTrue(amountValidator.isWithinBounds(
                new BigDecimal("25.00"),
                new BigDecimal("5.00"),
                new BigDecimal("100.00")
        ));
        assertThrows(IllegalArgumentException.class,
                () -> amountValidator.isWithinBounds(null, BigDecimal.ONE, BigDecimal.TEN));
        assertDoesNotThrow(() -> amountValidator.validatePositive(new BigDecimal("1.00"), "Amount"));
        assertThrows(IllegalArgumentException.class,
                () -> amountValidator.validatePositive(new BigDecimal("-1.00"), "Amount"));
    }

    @Test
    void shouldValidateTransactionAmountAgainstConfiguredRange() {
        assertDoesNotThrow(() -> amountValidator.validateTransactionAmount(new BigDecimal("20.00")));
        assertThrows(IllegalArgumentException.class,
                () -> amountValidator.validateTransactionAmount(new BigDecimal("4.99")));
        assertThrows(IllegalArgumentException.class,
                () -> amountValidator.validateTransactionAmount(new BigDecimal("501.00")));
    }

    @Test
    void shouldValidateDailyLimitUsingProjectedTotal() {
        assertDoesNotThrow(() -> amountValidator.validateDailyLimit(
                new BigDecimal("900.00"),
                new BigDecimal("100.00")
        ));
        assertThrows(IllegalArgumentException.class,
                () -> amountValidator.validateDailyLimit(
                        new BigDecimal("950.00"),
                        new BigDecimal("60.00")
                ));
    }
}
