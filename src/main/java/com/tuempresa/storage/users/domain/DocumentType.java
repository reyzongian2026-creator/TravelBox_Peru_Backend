package com.tuempresa.storage.users.domain;

public enum DocumentType {
    DNI,
    PASSPORT,
    FOREIGNER_CARD,
    ID_CARD,
    DRIVER_LICENSE,
    OTHER;

    public static DocumentType fromNullable(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        String normalized = rawValue.trim().toUpperCase().replace(' ', '_').replace('-', '_');
        return switch (normalized) {
            case "DNI" -> DNI;
            case "PASSPORT", "PASAPORTE" -> PASSPORT;
            case "FOREIGNER_CARD", "CARNE_DE_EXTRANJERIA", "CARNET_DE_EXTRANJERIA" -> FOREIGNER_CARD;
            case "ID_CARD", "CEDULA", "CEDULA_DE_IDENTIDAD" -> ID_CARD;
            case "DRIVER_LICENSE", "LICENCIA_DE_CONDUCIR", "LICENCE" -> DRIVER_LICENSE;
            case "OTHER", "OTRO" -> OTHER;
            default -> null;
        };
    }
}
