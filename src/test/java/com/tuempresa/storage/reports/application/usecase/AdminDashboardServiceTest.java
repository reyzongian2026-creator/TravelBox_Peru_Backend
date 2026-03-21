package com.tuempresa.storage.reports.application.usecase;

import com.tuempresa.storage.delivery.domain.DeliveryStatus;
import com.tuempresa.storage.delivery.infrastructure.out.persistence.DeliveryOrderRepository;
import com.tuempresa.storage.incidents.domain.IncidentStatus;
import com.tuempresa.storage.incidents.infrastructure.out.persistence.IncidentRepository;
import com.tuempresa.storage.payments.domain.PaymentStatus;
import com.tuempresa.storage.payments.infrastructure.out.persistence.PaymentAttemptRepository;
import com.tuempresa.storage.reservations.domain.ReservationStatus;
import com.tuempresa.storage.reservations.infrastructure.out.persistence.ReservationRepository;
import com.tuempresa.storage.users.infrastructure.out.persistence.UserRepository;
import com.tuempresa.storage.warehouses.infrastructure.out.persistence.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminDashboardServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private PaymentAttemptRepository paymentAttemptRepository;

    @Mock
    private DeliveryOrderRepository deliveryOrderRepository;

    private AdminDashboardService adminDashboardService;

    @BeforeEach
    void setUp() {
        adminDashboardService = new AdminDashboardService(
                userRepository,
                warehouseRepository,
                reservationRepository,
                incidentRepository,
                paymentAttemptRepository,
                deliveryOrderRepository
        );
    }

    @Test
    void shouldReturnCachedDashboardOnSecondCall() {
        when(reservationRepository.findByStartAtBetweenOrderByStartAtAsc(any(), any()))
                .thenReturn(Collections.emptyList());
        when(userRepository.count()).thenReturn(10L);
        when(warehouseRepository.count()).thenReturn(5L);
        when(reservationRepository.countByStatusNotIn(any())).thenReturn(3L);
        when(incidentRepository.countByStatus(IncidentStatus.OPEN)).thenReturn(2L);
        when(paymentAttemptRepository.sumAmountByStatus(PaymentStatus.CONFIRMED))
                .thenReturn(new BigDecimal("1000.00"));
        when(incidentRepository.findByReservationIdIn(any())).thenReturn(Collections.emptyList());
        when(incidentRepository.findByReservationIdInAndStatus(any(), eq(IncidentStatus.OPEN)))
                .thenReturn(Collections.emptyList());
        when(paymentAttemptRepository.findByReservationIdInAndStatusOrderByCreatedAtDesc(any(), eq(PaymentStatus.CONFIRMED)))
                .thenReturn(Collections.emptyList());
        when(deliveryOrderRepository.findByUpdatedAtBetweenOrderByUpdatedAtDesc(any(), any()))
                .thenReturn(Collections.emptyList());

        var firstResult = adminDashboardService.dashboard("month");
        var secondResult = adminDashboardService.dashboard("month");

        assertNotNull(firstResult);
        assertNotNull(secondResult);

        verify(reservationRepository, times(1)).findByStartAtBetweenOrderByStartAtAsc(any(), any());
    }

    @Test
    void shouldReturnDifferentCacheForDifferentPeriods() {
        when(reservationRepository.findByStartAtBetweenOrderByStartAtAsc(any(), any()))
                .thenReturn(Collections.emptyList());
        when(userRepository.count()).thenReturn(10L);
        when(warehouseRepository.count()).thenReturn(5L);
        when(reservationRepository.countByStatusNotIn(any())).thenReturn(3L);
        when(incidentRepository.countByStatus(IncidentStatus.OPEN)).thenReturn(2L);
        when(paymentAttemptRepository.sumAmountByStatus(PaymentStatus.CONFIRMED))
                .thenReturn(new BigDecimal("1000.00"));
        when(incidentRepository.findByReservationIdIn(any())).thenReturn(Collections.emptyList());
        when(incidentRepository.findByReservationIdInAndStatus(any(), eq(IncidentStatus.OPEN)))
                .thenReturn(Collections.emptyList());
        when(paymentAttemptRepository.findByReservationIdInAndStatusOrderByCreatedAtDesc(any(), eq(PaymentStatus.CONFIRMED)))
                .thenReturn(Collections.emptyList());
        when(deliveryOrderRepository.findByUpdatedAtBetweenOrderByUpdatedAtDesc(any(), any()))
                .thenReturn(Collections.emptyList());

        var weekResult = adminDashboardService.dashboard("week");
        var monthResult = adminDashboardService.dashboard("month");
        var yearResult = adminDashboardService.dashboard("year");

        assertNotNull(weekResult);
        assertNotNull(monthResult);
        assertNotNull(yearResult);
        assertEquals("week", weekResult.period());
        assertEquals("month", monthResult.period());
        assertEquals("year", yearResult.period());
    }

    @Test
    void shouldInvalidateCacheCompletely() {
        when(reservationRepository.findByStartAtBetweenOrderByStartAtAsc(any(), any()))
                .thenReturn(Collections.emptyList());
        when(userRepository.count()).thenReturn(10L);
        when(warehouseRepository.count()).thenReturn(5L);
        when(reservationRepository.countByStatusNotIn(any())).thenReturn(3L);
        when(incidentRepository.countByStatus(IncidentStatus.OPEN)).thenReturn(2L);
        when(paymentAttemptRepository.sumAmountByStatus(PaymentStatus.CONFIRMED))
                .thenReturn(new BigDecimal("1000.00"));
        when(incidentRepository.findByReservationIdIn(any())).thenReturn(Collections.emptyList());
        when(incidentRepository.findByReservationIdInAndStatus(any(), eq(IncidentStatus.OPEN)))
                .thenReturn(Collections.emptyList());
        when(paymentAttemptRepository.findByReservationIdInAndStatusOrderByCreatedAtDesc(any(), eq(PaymentStatus.CONFIRMED)))
                .thenReturn(Collections.emptyList());
        when(deliveryOrderRepository.findByUpdatedAtBetweenOrderByUpdatedAtDesc(any(), any()))
                .thenReturn(Collections.emptyList());

        var firstResult = adminDashboardService.dashboard("month");
        adminDashboardService.invalidateCache();
        var secondResult = adminDashboardService.dashboard("month");

        assertNotNull(firstResult);
        assertNotNull(secondResult);
        verify(reservationRepository, times(2)).findByStartAtBetweenOrderByStartAtAsc(any(), any());
    }

    @Test
    void shouldInvalidateCacheForSpecificPeriod() {
        when(reservationRepository.findByStartAtBetweenOrderByStartAtAsc(any(), any()))
                .thenReturn(Collections.emptyList());
        when(userRepository.count()).thenReturn(10L);
        when(warehouseRepository.count()).thenReturn(5L);
        when(reservationRepository.countByStatusNotIn(any())).thenReturn(3L);
        when(incidentRepository.countByStatus(IncidentStatus.OPEN)).thenReturn(2L);
        when(paymentAttemptRepository.sumAmountByStatus(PaymentStatus.CONFIRMED))
                .thenReturn(new BigDecimal("1000.00"));
        when(incidentRepository.findByReservationIdIn(any())).thenReturn(Collections.emptyList());
        when(incidentRepository.findByReservationIdInAndStatus(any(), eq(IncidentStatus.OPEN)))
                .thenReturn(Collections.emptyList());
        when(paymentAttemptRepository.findByReservationIdInAndStatusOrderByCreatedAtDesc(any(), eq(PaymentStatus.CONFIRMED)))
                .thenReturn(Collections.emptyList());
        when(deliveryOrderRepository.findByUpdatedAtBetweenOrderByUpdatedAtDesc(any(), any()))
                .thenReturn(Collections.emptyList());

        adminDashboardService.dashboard("week");
        adminDashboardService.dashboard("month");
        
        adminDashboardService.invalidateCache("week");
        
        adminDashboardService.dashboard("week");
        adminDashboardService.dashboard("month");

        verify(reservationRepository, times(3)).findByStartAtBetweenOrderByStartAtAsc(any(), any());
    }

    @Test
    void shouldReturnValidCacheSize() {
        when(reservationRepository.findByStartAtBetweenOrderByStartAtAsc(any(), any()))
                .thenReturn(Collections.emptyList());
        when(userRepository.count()).thenReturn(10L);
        when(warehouseRepository.count()).thenReturn(5L);
        when(reservationRepository.countByStatusNotIn(any())).thenReturn(3L);
        when(incidentRepository.countByStatus(IncidentStatus.OPEN)).thenReturn(2L);
        when(paymentAttemptRepository.sumAmountByStatus(PaymentStatus.CONFIRMED))
                .thenReturn(new BigDecimal("1000.00"));
        when(incidentRepository.findByReservationIdIn(any())).thenReturn(Collections.emptyList());
        when(incidentRepository.findByReservationIdInAndStatus(any(), eq(IncidentStatus.OPEN)))
                .thenReturn(Collections.emptyList());
        when(paymentAttemptRepository.findByReservationIdInAndStatusOrderByCreatedAtDesc(any(), eq(PaymentStatus.CONFIRMED)))
                .thenReturn(Collections.emptyList());
        when(deliveryOrderRepository.findByUpdatedAtBetweenOrderByUpdatedAtDesc(any(), any()))
                .thenReturn(Collections.emptyList());

        adminDashboardService.dashboard("week");
        adminDashboardService.dashboard("month");
        adminDashboardService.dashboard("year");

        assertEquals(3, adminDashboardService.getCacheSize());
    }

    @Test
    void shouldHandleNullPaymentSum() {
        when(reservationRepository.findByStartAtBetweenOrderByStartAtAsc(any(), any()))
                .thenReturn(Collections.emptyList());
        when(userRepository.count()).thenReturn(10L);
        when(warehouseRepository.count()).thenReturn(5L);
        when(reservationRepository.countByStatusNotIn(any())).thenReturn(3L);
        when(incidentRepository.countByStatus(IncidentStatus.OPEN)).thenReturn(2L);
        when(paymentAttemptRepository.sumAmountByStatus(PaymentStatus.CONFIRMED))
                .thenReturn(null);
        when(incidentRepository.findByReservationIdIn(any())).thenReturn(Collections.emptyList());
        when(incidentRepository.findByReservationIdInAndStatus(any(), eq(IncidentStatus.OPEN)))
                .thenReturn(Collections.emptyList());
        when(paymentAttemptRepository.findByReservationIdInAndStatusOrderByCreatedAtDesc(any(), eq(PaymentStatus.CONFIRMED)))
                .thenReturn(Collections.emptyList());
        when(deliveryOrderRepository.findByUpdatedAtBetweenOrderByUpdatedAtDesc(any(), any()))
                .thenReturn(Collections.emptyList());

        var result = adminDashboardService.dashboard("month");

        assertNotNull(result);
        assertNotNull(result.summary());
    }
}
