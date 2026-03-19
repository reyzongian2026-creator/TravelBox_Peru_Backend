package com.tuempresa.storage.reservations.domain;

import java.util.Locale;

public enum ReservationBagSize {
    SMALL("S"),
    MEDIUM("M"),
    LARGE("L"),
    EXTRA_LARGE("XL");

    private final String code;

    ReservationBagSize(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static ReservationBagSize fromRaw(String raw) {
        if (raw == null || raw.isBlank()) {
            return MEDIUM;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "S", "SMALL" -> SMALL;
            case "M", "MEDIUM" -> MEDIUM;
            case "L", "LARGE" -> LARGE;
            case "XL", "EXTRA_LARGE", "EXTRALARGE", "X_LARGE" -> EXTRA_LARGE;
            default -> throw new IllegalArgumentException("Bag size no valido: " + raw);
        };
    }
}
