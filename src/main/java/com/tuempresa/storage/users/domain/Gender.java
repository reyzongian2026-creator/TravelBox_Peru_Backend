package com.tuempresa.storage.users.domain;

public enum Gender {
    FEMALE,
    MALE,
    NON_BINARY,
    PREFER_NOT_TO_SAY,
    OTHER;

    public static Gender fromNullable(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        String normalized = rawValue.trim().toUpperCase().replace(' ', '_').replace('-', '_');
        return switch (normalized) {
            case "FEMALE", "MUJER" -> FEMALE;
            case "MALE", "HOMBRE" -> MALE;
            case "NON_BINARY", "NO_BINARIO" -> NON_BINARY;
            case "PREFER_NOT_TO_SAY", "PREFIERO_NO_DECIRLO" -> PREFER_NOT_TO_SAY;
            case "OTHER", "OTRO" -> OTHER;
            default -> null;
        };
    }
}
