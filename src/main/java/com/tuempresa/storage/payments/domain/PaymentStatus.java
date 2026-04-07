package com.tuempresa.storage.payments.domain;

public enum PaymentStatus {
    PENDING,
    CONFIRMED,
    FAILED,
    REFUND_PENDING,
    REFUNDED,
    PARTIALLY_REFUNDED,
    REFUND_FAILED,
    AUTO_CONFIRMED_YAPE_EMAIL,
    MANUAL_REVIEW,
    BANK_VERIFIED
}
