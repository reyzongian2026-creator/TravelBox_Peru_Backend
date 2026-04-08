package com.tuempresa.storage.reservations.application.usecase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Lazy(false)
public class ReservationExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReservationExpirationScheduler.class);

    private final ReservationService reservationService;

    public ReservationExpirationScheduler(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @Scheduled(fixedDelayString = "${app.reservations.expiration-check-ms:60000}")
    public void expirePendingReservations() {
        try {
            int expired = reservationService.expirePendingPaymentsNow();
            if (expired > 0) {
                log.info("Expired {} pending reservations due to payment timeout.", expired);
            }
        } catch (Exception ex) {
            log.error("Reservation expiration scheduler failed; keeping service alive.", ex);
        }
    }
}
