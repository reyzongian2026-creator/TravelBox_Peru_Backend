package com.tuempresa.storage.reservations.domain;

import java.util.EnumSet;
import java.util.Set;

public final class ReservationAvailabilityRules {

    public static final Set<ReservationStatus> OCCUPYING_STATES = EnumSet.of(
            ReservationStatus.PENDING_PAYMENT,
            ReservationStatus.CONFIRMED,
            ReservationStatus.CHECKIN_PENDING,
            ReservationStatus.STORED,
            ReservationStatus.READY_FOR_PICKUP,
            ReservationStatus.OUT_FOR_DELIVERY,
            ReservationStatus.INCIDENT
    );

    private ReservationAvailabilityRules() {
    }
}
