package com.tuempresa.storage.reservations.application.usecase;

import com.tuempresa.storage.reservations.domain.Reservation;
import com.tuempresa.storage.reservations.domain.ReservationStatus;
import com.tuempresa.storage.reservations.infrastructure.out.persistence.ReservationRepository;
import com.tuempresa.storage.warehouses.domain.Warehouse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LatePickupSurchargeServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private Warehouse mockWarehouse;

    @Mock
    private Reservation mockReservation;

    private LatePickupSurchargeService latePickupSurchargeService;

    @BeforeEach
    void setUp() {
        latePickupSurchargeService = new LatePickupSurchargeService(
                reservationRepository,
                new BigDecimal("2.50"),
                60,
                48,
                new BigDecimal("50.00")
        );

        when(mockWarehouse.getPricePerHourMedium()).thenReturn(new BigDecimal("4.50"));
        when(mockReservation.getWarehouse()).thenReturn(mockWarehouse);
        when(mockReservation.getEstimatedItems()).thenReturn(2);
        when(mockReservation.getStatus()).thenReturn(ReservationStatus.STORED);
    }

    @Test
    void shouldNotApplySurchargeWhenReservationIsOnTime() {
        Instant endTime = Instant.now().plus(2, ChronoUnit.HOURS);
        when(mockReservation.getEndAt()).thenReturn(endTime);

        latePickupSurchargeService.calculateAndApplySurcharge(mockReservation);

        verify(mockReservation, never()).applyLatePickupSurcharge(any());
    }

    @Test
    void shouldNotApplySurchargeWithinGracePeriod() {
        Instant endTime = Instant.now().minus(30, ChronoUnit.MINUTES);
        when(mockReservation.getEndAt()).thenReturn(endTime);

        latePickupSurchargeService.calculateAndApplySurcharge(mockReservation);

        verify(mockReservation, never()).applyLatePickupSurcharge(any());
    }

    @Test
    void shouldApplySurchargeAfterGracePeriod() {
        Instant endTime = Instant.now().minus(5, ChronoUnit.HOURS);
        when(mockReservation.getEndAt()).thenReturn(endTime);
        when(mockReservation.getLatePickupSurcharge()).thenReturn(BigDecimal.ZERO);

        latePickupSurchargeService.calculateAndApplySurcharge(mockReservation);

        verify(mockReservation, times(1)).applyLatePickupSurcharge(any());
    }

    @Test
    void shouldNotApplySurchargeForCompletedReservation() {
        when(mockReservation.getStatus()).thenReturn(ReservationStatus.COMPLETED);

        latePickupSurchargeService.calculateAndApplySurcharge(mockReservation);

        verify(mockReservation, never()).applyLatePickupSurcharge(any());
    }

    @Test
    void shouldNotApplySurchargeForCancelledReservation() {
        when(mockReservation.getStatus()).thenReturn(ReservationStatus.CANCELLED);

        latePickupSurchargeService.calculateAndApplySurcharge(mockReservation);

        verify(mockReservation, never()).applyLatePickupSurcharge(any());
    }

    @Test
    void shouldCalculateCorrectEstimatedSurcharge() {
        Instant endTime = Instant.now().minus(5, ChronoUnit.HOURS);
        when(mockReservation.getEndAt()).thenReturn(endTime);

        BigDecimal estimatedSurcharge = latePickupSurchargeService.calculateEstimatedSurcharge(mockReservation);

        assertNotNull(estimatedSurcharge);
        assertTrue(estimatedSurcharge.compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void shouldReturnZeroSurchargeForOnTimeReservation() {
        Instant endTime = Instant.now().plus(1, ChronoUnit.HOURS);
        when(mockReservation.getEndAt()).thenReturn(endTime);

        BigDecimal estimatedSurcharge = latePickupSurchargeService.calculateEstimatedSurcharge(mockReservation);

        assertEquals(BigDecimal.ZERO, estimatedSurcharge);
    }

    @Test
    void shouldRespectMaxSurchargeLimit() {
        Instant endTime = Instant.now().minus(100, ChronoUnit.HOURS);
        when(mockReservation.getEndAt()).thenReturn(endTime);

        BigDecimal estimatedSurcharge = latePickupSurchargeService.calculateEstimatedSurcharge(mockReservation);

        assertNotNull(estimatedSurcharge);
        assertTrue(estimatedSurcharge.compareTo(new BigDecimal("50.00")) <= 0);
    }

    @Test
    void shouldReturnValidSurchargeSummary() {
        Instant endTime = Instant.now().minus(5, ChronoUnit.HOURS);
        when(mockReservation.getEndAt()).thenReturn(endTime);
        when(mockReservation.getLatePickupSurcharge()).thenReturn(new BigDecimal("10.00"));

        LatePickupSurchargeService.SurchargeSummary summary = 
                latePickupSurchargeService.getSurchargeSummary(mockReservation);

        assertNotNull(summary);
        assertTrue(summary.isOverdue());
        assertFalse(summary.isInGracePeriod());
        assertEquals(new BigDecimal("10.00"), summary.currentSurcharge());
    }

    @Test
    void shouldGetCorrectSurchargeRateFromWarehouse() {
        BigDecimal rate = latePickupSurchargeService.getSurchargeRate(mockWarehouse);
        assertEquals(new BigDecimal("4.50"), rate);
    }

    @Test
    void shouldUseDefaultRateWhenWarehouseIsNull() {
        BigDecimal rate = latePickupSurchargeService.getSurchargeRate(null);
        assertNotNull(rate);
    }

    @Test
    void shouldProcessOverdueReservations() {
        when(reservationRepository.findActiveReservationsForSurchargeProcessing(any(), any()))
                .thenReturn(List.of(mockReservation));

        latePickupSurchargeService.processOverdueReservations();

        verify(reservationRepository).findActiveReservationsForSurchargeProcessing(any(), any());
    }
}
