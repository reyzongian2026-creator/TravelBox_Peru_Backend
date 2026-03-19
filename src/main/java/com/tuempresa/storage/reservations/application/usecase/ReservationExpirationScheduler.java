package com.tuempresa.storage.reservations.application.usecase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReservationExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReservationExpirationScheduler.class);

    private final ReservationService reservationService;

    public ReservationExpirationScheduler(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @Scheduled(fixedDelayString = "${app.reservations.expiration-check-ms:300000}")
    public void expirePendingReservations() {
        int expired = reservationService.expirePendingPaymentsNow();
        if (expired > 0) {
            log.info("Expired {} pending reservations due to payment timeout.", expired);
        }
    }
}
