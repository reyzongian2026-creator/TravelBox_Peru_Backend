package com.tuempresa.storage.payments.domain;

import java.util.Locale;

public enum PaymentMethod {
    CARD,
    SAVED_CARD,
    YAPE,
    PLIN,
    WALLET,
    COUNTER,
    CASH,
    UNKNOWN;

    public static PaymentMethod from(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return UNKNOWN;
        }
        String normalized = rawValue.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        return switch (normalized) {
            case "card", "tarjeta", "visa", "mastercard" -> CARD;
            case "saved_card", "oneclick", "tokenized" -> SAVED_CARD;
            case "yape" -> YAPE;
            case "plin" -> PLIN;
            case "wallet", "billetera", "yape/plin", "yapeplin" -> WALLET;
            case "counter", "encaja", "en_caja" -> COUNTER;
            case "cash", "efectivo" -> CASH;
            default -> UNKNOWN;
        };
    }

    public boolean isDirectChargeFlow() {
        return this == CARD || this == SAVED_CARD || this == YAPE;
    }

    public boolean isCheckoutOrderFlow() {
        return this == PLIN || this == WALLET;
    }

    public boolean isManualTransfer() {
        return this == YAPE || this == PLIN || this == WALLET;
    }

    public boolean isDigitalOnline() {
        return this == CARD || this == SAVED_CARD;
    }

    public String label() {
        return name().toLowerCase(Locale.ROOT);
    }
}
