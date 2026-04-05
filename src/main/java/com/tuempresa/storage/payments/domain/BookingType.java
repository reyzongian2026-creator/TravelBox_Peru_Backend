package com.tuempresa.storage.payments.domain;

/**
 * Classification of a reservation based on how far in advance it was booked
 * relative to its start time.
 */
public enum BookingType {
    /** Reservation starts within the immediate threshold (default 6 hours). */
    IMMEDIATE,
    /** Reservation starts after the immediate threshold. */
    ADVANCE
}
