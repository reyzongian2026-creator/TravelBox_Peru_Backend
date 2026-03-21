package com.tuempresa.storage.reservations.application.usecase;

import com.tuempresa.storage.reservations.domain.Reservation;
import com.tuempresa.storage.reservations.domain.ReservationStatus;
import com.tuempresa.storage.reservations.infrastructure.out.persistence.ReservationRepository;
import com.tuempresa.storage.warehouses.domain.Warehouse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class LatePickupSurchargeService {

    private static final Logger log = LoggerFactory.getLogger(LatePickupSurchargeService.class);

    private final ReservationRepository reservationRepository;
    private final BigDecimal defaultSurchargePerHour;
    private final int gracePeriodMinutes;
    private final int maxSurchargeHours;
    private final BigDecimal defaultMaxSurcharge;

    public LatePickupSurchargeService(
            ReservationRepository reservationRepository,
            @Value("${app.pricing.late-pickup.surcharge-per-hour:2.50}") BigDecimal surchargePerHour,
            @Value("${app.pricing.late-pickup.grace-period-minutes:60}") int gracePeriodMinutes,
            @Value("${app.pricing.late-pickup.max-surcharge-hours:48}") int maxSurchargeHours,
            @Value("${app.pricing.late-pickup.max-surcharge:50.00}") BigDecimal maxSurcharge
    ) {
        this.reservationRepository = reservationRepository;
        this.defaultSurchargePerHour = surchargePerHour;
        this.gracePeriodMinutes = gracePeriodMinutes;
        this.maxSurchargeHours = maxSurchargeHours;
        this.defaultMaxSurcharge = maxSurcharge;
    }

    @Transactional
    public void calculateAndApplySurcharge(Reservation reservation) {
        if (!canApplySurcharge(reservation)) {
            return;
        }

        Instant endTime = reservation.getEndAt();
        Instant now = Instant.now();
        
        if (now.isBefore(endTime)) {
            return;
        }

        Duration gracePeriod = Duration.ofMinutes(gracePeriodMinutes);
        Instant effectiveEndTime = endTime.plus(gracePeriod);
        
        if (now.isBefore(effectiveEndTime)) {
            return;
        }

        Duration overdueDuration = Duration.between(effectiveEndTime, now);
        
        Warehouse warehouse = reservation.getWarehouse();
        BigDecimal hourlyRate = getSurchargeRate(warehouse);
        int overdueHours = (int) overdueDuration.toHours();
        
        if (overdueHours <= 0) {
            return;
        }

        int billableHours = Math.min(overdueHours, maxSurchargeHours);
        BigDecimal surcharge = hourlyRate
                .multiply(BigDecimal.valueOf(billableHours))
                .multiply(BigDecimal.valueOf(reservation.getEstimatedItems()))
                .setScale(2, RoundingMode.HALF_UP);

        surcharge = surcharge.min(defaultMaxSurcharge);

        BigDecimal currentSurcharge = reservation.getLatePickupSurcharge();
        if (surcharge.compareTo(currentSurcharge) > 0) {
            reservation.applyLatePickupSurcharge(surcharge);
            reservationRepository.save(reservation);
            
            log.info("Applied late pickup surcharge: reservationId={}, surcharge={}, overdueHours={}",
                    reservation.getId(), surcharge, billableHours);
        }
    }

    public BigDecimal calculateEstimatedSurcharge(Reservation reservation) {
        Instant endTime = reservation.getEndAt();
        Instant now = Instant.now();
        
        if (now.isBefore(endTime)) {
            return BigDecimal.ZERO;
        }

        Duration gracePeriod = Duration.ofMinutes(gracePeriodMinutes);
        Instant effectiveEndTime = endTime.plus(gracePeriod);
        
        if (now.isBefore(effectiveEndTime)) {
            return BigDecimal.ZERO;
        }

        Duration overdueDuration = Duration.between(effectiveEndTime, now);
        Warehouse warehouse = reservation.getWarehouse();
        BigDecimal hourlyRate = getSurchargeRate(warehouse);
        int overdueHours = (int) overdueDuration.toHours();
        
        if (overdueHours <= 0) {
            return BigDecimal.ZERO;
        }

        int billableHours = Math.min(overdueHours, maxSurchargeHours);
        return hourlyRate
                .multiply(BigDecimal.valueOf(billableHours))
                .multiply(BigDecimal.valueOf(reservation.getEstimatedItems()))
                .setScale(2, RoundingMode.HALF_UP)
                .min(defaultMaxSurcharge);
    }

    public BigDecimal getSurchargeRate(Warehouse warehouse) {
        if (warehouse == null) {
            return defaultSurchargePerHour;
        }
        BigDecimal rate = warehouse.getPricePerHourMedium();
        return rate != null ? rate : defaultSurchargePerHour;
    }

    private boolean canApplySurcharge(Reservation reservation) {
        ReservationStatus status = reservation.getStatus();
        return status == ReservationStatus.STORED ||
               status == ReservationStatus.READY_FOR_PICKUP ||
               status == ReservationStatus.OUT_FOR_DELIVERY;
    }

    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void processOverdueReservations() {
        log.debug("Processing overdue reservations for late pickup surcharge");
        
        List<ReservationStatus> activeStatuses = List.of(
                ReservationStatus.STORED,
                ReservationStatus.READY_FOR_PICKUP,
                ReservationStatus.OUT_FOR_DELIVERY
        );
        
        List<Reservation> activeReservations = reservationRepository
                .findActiveReservationsForSurchargeProcessing(activeStatuses, Instant.now());
        
        for (Reservation reservation : activeReservations) {
            try {
                calculateAndApplySurcharge(reservation);
            } catch (Exception e) {
                log.error("Error processing surcharge for reservation {}: {}",
                        reservation.getId(), e.getMessage());
            }
        }
        
        log.debug("Processed {} active reservations for late pickup surcharge",
                activeReservations.size());
    }

    public SurchargeSummary getSurchargeSummary(Reservation reservation) {
        BigDecimal currentSurcharge = reservation.getLatePickupSurcharge();
        BigDecimal estimatedAdditional = calculateEstimatedSurcharge(reservation);
        Instant endTime = reservation.getEndAt();
        Instant now = Instant.now();
        
        long overdueMinutes = 0;
        if (now.isAfter(endTime)) {
            Duration gracePeriod = Duration.ofMinutes(gracePeriodMinutes);
            Instant effectiveEndTime = endTime.plus(gracePeriod);
            if (now.isAfter(effectiveEndTime)) {
                overdueMinutes = Duration.between(effectiveEndTime, now).toMinutes();
            }
        }

        boolean inGracePeriod = !now.isAfter(endTime.plus(Duration.ofMinutes(gracePeriodMinutes)));
        
        return new SurchargeSummary(
                currentSurcharge,
                estimatedAdditional,
                overdueMinutes,
                now.isAfter(endTime),
                inGracePeriod
        );
    }

    public record SurchargeSummary(
            BigDecimal currentSurcharge,
            BigDecimal estimatedAdditionalSurcharge,
            long overdueMinutes,
            boolean isOverdue,
            boolean isInGracePeriod
    ) {}
}
