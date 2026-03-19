package com.tuempresa.storage.payments.domain;

public enum PaymentWebhookProcessingStatus {
    RECEIVED,
    PROCESSED,
    IGNORED,
    FAILED,
    DUPLICATE
}
