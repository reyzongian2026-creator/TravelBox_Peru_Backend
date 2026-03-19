package com.tuempresa.storage.reservations.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationStatusTest {

    @Test
    void shouldAllowConfiguredTransitions() {
        assertThat(ReservationStatus.PENDING_PAYMENT.canTransitionTo(ReservationStatus.CONFIRMED)).isTrue();
        assertThat(ReservationStatus.CONFIRMED.canTransitionTo(ReservationStatus.CHECKIN_PENDING)).isTrue();
        assertThat(ReservationStatus.STORED.canTransitionTo(ReservationStatus.OUT_FOR_DELIVERY)).isTrue();
        assertThat(ReservationStatus.INCIDENT.canTransitionTo(ReservationStatus.STORED)).isTrue();
        assertThat(ReservationStatus.EXPIRED.canTransitionTo(ReservationStatus.CONFIRMED)).isTrue();
    }

    @Test
    void shouldRejectInvalidTransitions() {
        assertThat(ReservationStatus.PENDING_PAYMENT.canTransitionTo(ReservationStatus.STORED)).isFalse();
        assertThat(ReservationStatus.COMPLETED.canTransitionTo(ReservationStatus.CANCELLED)).isFalse();
        assertThat(ReservationStatus.CANCELLED.canTransitionTo(ReservationStatus.CONFIRMED)).isFalse();
    }
}
