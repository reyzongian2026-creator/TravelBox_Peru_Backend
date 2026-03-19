package com.tuempresa.storage.reports.application.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;

public enum DashboardPeriod {
    WEEK("week", "Ultimos 7 dias"),
    MONTH("month", "Ultimos 30 dias"),
    YEAR("year", "Ultimos 12 meses");

    private final String code;
    private final String label;

    DashboardPeriod(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    public Instant startAt(Instant now, ZoneId zoneId) {
        ZonedDateTime zonedNow = now.atZone(zoneId);
        return switch (this) {
            case WEEK -> zonedNow.toLocalDate()
                    .minusDays(6)
                    .atStartOfDay(zoneId)
                    .toInstant();
            case MONTH -> zonedNow.toLocalDate()
                    .minusDays(29)
                    .atStartOfDay(zoneId)
                    .toInstant();
            case YEAR -> zonedNow.toLocalDate()
                    .withDayOfMonth(1)
                    .minusMonths(11)
                    .atStartOfDay(zoneId)
                    .toInstant();
        };
    }

    public boolean includes(Instant value, Instant now, ZoneId zoneId) {
        if (value == null || value.isAfter(now)) {
            return false;
        }
        return !value.isBefore(startAt(now, zoneId));
    }

    public static DashboardPeriod from(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return MONTH;
        }
        String normalized = rawValue.trim().toUpperCase(Locale.ROOT);
        for (DashboardPeriod candidate : values()) {
            if (candidate.name().equals(normalized) || candidate.code.equalsIgnoreCase(normalized)) {
                return candidate;
            }
        }
        return MONTH;
    }

    public String trendLabel(LocalDate date) {
        return switch (this) {
            case WEEK, MONTH -> date.getDayOfMonth() + "/" + String.format(Locale.ROOT, "%02d", date.getMonthValue());
            case YEAR -> date.getMonth().name().substring(0, 3);
        };
    }
}
