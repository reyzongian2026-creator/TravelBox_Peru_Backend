package com.tuempresa.storage.reservations.application.usecase;

import com.tuempresa.storage.inventory.infrastructure.out.persistence.CheckinRecordRepository;
import com.tuempresa.storage.inventory.infrastructure.out.persistence.CheckoutRecordRepository;
import com.tuempresa.storage.inventory.infrastructure.out.persistence.StoredItemEvidenceRepository;
import com.tuempresa.storage.notifications.application.email.CustomerEmailService;
import com.tuempresa.storage.notifications.application.usecase.NotificationService;
import com.tuempresa.storage.ops.infrastructure.out.persistence.QrHandoffCaseRepository;
import com.tuempresa.storage.payments.infrastructure.out.persistence.CancellationRecordRepository;
import com.tuempresa.storage.payments.infrastructure.out.persistence.PaymentAttemptRepository;
import com.tuempresa.storage.reservations.application.dto.CreateReservationRequest;
import com.tuempresa.storage.reservations.domain.ReservationStatus;
import com.tuempresa.storage.reservations.infrastructure.out.persistence.ReservationRepository;
import com.tuempresa.storage.shared.domain.exception.ApiException;
import com.tuempresa.storage.shared.infrastructure.security.AuthUserPrincipal;
import com.tuempresa.storage.shared.infrastructure.security.WarehouseAccessService;
import com.tuempresa.storage.shared.infrastructure.web.PublicUrlService;
import com.tuempresa.storage.users.domain.User;
import com.tuempresa.storage.users.infrastructure.out.persistence.UserRepository;
import com.tuempresa.storage.warehouses.application.usecase.WarehouseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReservationServiceTest {

    private static final Set<ReservationStatus> DUPLICATE_EXCLUDED_STATUSES = Set.of(
            ReservationStatus.CANCELLED,
            ReservationStatus.EXPIRED
    );

    @Mock private ReservationRepository reservationRepository;
    @Mock private WarehouseService warehouseService;
    @Mock private UserRepository userRepository;
    @Mock private QrCodeService qrCodeService;
    @Mock private PublicUrlService publicUrlService;
    @Mock private NotificationService notificationService;
    @Mock private CustomerEmailService customerEmailService;
    @Mock private WarehouseAccessService warehouseAccessService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private CheckinRecordRepository checkinRecordRepository;
    @Mock private CheckoutRecordRepository checkoutRecordRepository;
    @Mock private StoredItemEvidenceRepository storedItemEvidenceRepository;
    @Mock private QrHandoffCaseRepository qrHandoffCaseRepository;
    @Mock private PaymentAttemptRepository paymentAttemptRepository;
    @Mock private CancellationRecordRepository cancellationRecordRepository;
    @Mock private AuthUserPrincipal principal;
    @Mock private User user;

    private ReservationService reservationService;

    @BeforeEach
    void setUp() {
        reservationService = new ReservationService(
                reservationRepository,
                warehouseService,
                userRepository,
                qrCodeService,
                publicUrlService,
                notificationService,
                customerEmailService,
                warehouseAccessService,
                passwordEncoder,
                checkinRecordRepository,
                checkoutRecordRepository,
                storedItemEvidenceRepository,
                qrHandoffCaseRepository,
                paymentAttemptRepository,
                cancellationRecordRepository,
                60
        );
    }

    @Test
    void shouldRejectRapidDuplicateReservationOnlyWhenActiveReservationStillExists() {
        CreateReservationRequest request = buildRequest();
        mockAuthorizedUser();

        when(reservationRepository.existsByUserIdAndWarehouseIdAndCreatedAtAfterAndStatusNotIn(
                eq(63L),
                eq(10L),
                any(Instant.class),
                eq(DUPLICATE_EXCLUDED_STATUSES)
        )).thenReturn(true);

        ApiException error = assertThrows(
                ApiException.class,
                () -> reservationService.create(request, principal)
        );

        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        assertEquals("DUPLICATE_RESERVATION_DETECTED", error.getCode());
        verifyNoInteractions(warehouseService);
    }

    @Test
    void shouldAllowReservationFlowToContinueAfterCanceledReservationWasExcludedFromDuplicateGuard() {
        CreateReservationRequest request = buildRequest();
        mockAuthorizedUser();

        when(reservationRepository.existsByUserIdAndWarehouseIdAndCreatedAtAfterAndStatusNotIn(
                eq(63L),
                eq(10L),
                any(Instant.class),
                argThat(statuses -> statuses != null && statuses.equals(DUPLICATE_EXCLUDED_STATUSES))
        )).thenReturn(false);

        RuntimeException sentinel = new RuntimeException("stop-after-duplicate-guard");
        when(warehouseService.requireWarehouseForUpdate(10L)).thenThrow(sentinel);

        RuntimeException error = assertThrows(
                RuntimeException.class,
                () -> reservationService.create(request, principal)
        );

        assertSame(sentinel, error);
        verify(warehouseService).requireWarehouseForUpdate(10L);
    }

    private void mockAuthorizedUser() {
        when(principal.getId()).thenReturn(63L);
        when(userRepository.findById(63L)).thenReturn(Optional.of(user));
        when(user.getId()).thenReturn(63L);
        when(user.isEmailVerified()).thenReturn(true);
        when(user.isProfileCompleted()).thenReturn(true);
    }

    private CreateReservationRequest buildRequest() {
        Instant startAt = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant endAt = startAt.plus(2, ChronoUnit.HOURS);
        return new CreateReservationRequest(
                10L,
                startAt,
                endAt,
                1,
                "S",
                false,
                false,
                false,
                false
        );
    }
}
