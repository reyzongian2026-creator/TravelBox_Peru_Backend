package com.tuempresa.storage.reservations.domain;

import java.util.Map;
import java.util.Set;

public enum ReservationStatus {
    DRAFT,
    PENDING_PAYMENT,
    CONFIRMED,
    CHECKIN_PENDING,
    STORED,
    READY_FOR_PICKUP,
    OUT_FOR_DELIVERY,
    INCIDENT,
    COMPLETED,
    CANCELLED,
    EXPIRED;

    private static final Map<ReservationStatus, Set<ReservationStatus>> ALLOWED_TRANSITIONS = Map.ofEntries(
            Map.entry(DRAFT, Set.of(PENDING_PAYMENT, CANCELLED)),
            Map.entry(PENDING_PAYMENT, Set.of(CONFIRMED, CANCELLED, EXPIRED)),
            Map.entry(CONFIRMED, Set.of(CHECKIN_PENDING, CANCELLED)),
            Map.entry(CHECKIN_PENDING, Set.of(STORED, CANCELLED, INCIDENT)),
            Map.entry(STORED, Set.of(READY_FOR_PICKUP, OUT_FOR_DELIVERY, INCIDENT)),
            Map.entry(READY_FOR_PICKUP, Set.of(COMPLETED, INCIDENT, OUT_FOR_DELIVERY)),
            Map.entry(OUT_FOR_DELIVERY, Set.of(COMPLETED, INCIDENT)),
            Map.entry(INCIDENT, Set.of(STORED, READY_FOR_PICKUP, COMPLETED, CANCELLED)),
            Map.entry(COMPLETED, Set.of()),
            Map.entry(CANCELLED, Set.of()),
            // Permite reconciliar pagos aprobados tardios por webhook de pasarela.
            Map.entry(EXPIRED, Set.of(CONFIRMED))
    );

    public boolean canTransitionTo(ReservationStatus next) {
        return ALLOWED_TRANSITIONS.getOrDefault(this, Set.of()).contains(next);
    }
}
