package com.tuempresa.storage.payments.domain;

/**
 * Result of evaluating the cancellation/refund policy for a reservation.
 */
public enum CancellationPolicyType {
    FULL_REFUND,
    PARTIAL_REFUND,
    NO_REFUND,
    MANUAL_REVIEW
}
