package com.tuempresa.storage.reservations.application.usecase;

import com.tuempresa.storage.notifications.application.usecase.NotificationService;
import com.tuempresa.storage.notifications.application.email.CustomerEmailService;
import com.tuempresa.storage.inventory.application.usecase.InventoryService;
import com.tuempresa.storage.inventory.domain.CheckinRecord;
import com.tuempresa.storage.inventory.domain.CheckoutRecord;
import com.tuempresa.storage.inventory.domain.StoredItemEvidence;
import com.tuempresa.storage.inventory.infrastructure.out.persistence.CheckinRecordRepository;
import com.tuempresa.storage.inventory.infrastructure.out.persistence.CheckoutRecordRepository;
import com.tuempresa.storage.inventory.infrastructure.out.persistence.StoredItemEvidenceRepository;
import com.tuempresa.storage.ops.domain.QrHandoffCase;
import com.tuempresa.storage.ops.infrastructure.out.persistence.QrHandoffCaseRepository;
import com.tuempresa.storage.payments.domain.PaymentAttempt;
import com.tuempresa.storage.payments.domain.PaymentMethod;
import com.tuempresa.storage.payments.domain.PaymentStatus;
import com.tuempresa.storage.payments.infrastructure.out.persistence.CancellationRecordRepository;
import com.tuempresa.storage.payments.infrastructure.out.persistence.PaymentAttemptRepository;
import com.tuempresa.storage.reservations.application.dto.CancelReservationRequest;
import com.tuempresa.storage.reservations.application.dto.CreateAssistedReservationRequest;
import com.tuempresa.storage.reservations.application.dto.CreateReservationRequest;
import com.tuempresa.storage.reservations.application.dto.ReservationExportRow;
import com.tuempresa.storage.reservations.application.dto.ReservationLuggagePhotoResponse;
import com.tuempresa.storage.reservations.application.dto.RevenueReportResponse;
import com.tuempresa.storage.reservations.application.dto.ReservationOperationalDetailResponse;
import com.tuempresa.storage.reservations.application.dto.ReservationResponse;
import com.tuempresa.storage.reservations.domain.Reservation;
import com.tuempresa.storage.reservations.domain.ReservationAvailabilityRules;
import com.tuempresa.storage.reservations.domain.ReservationBagSize;
import com.tuempresa.storage.reservations.domain.ReservationStatus;
import com.tuempresa.storage.reservations.infrastructure.out.persistence.ReservationRepository;
import com.tuempresa.storage.shared.domain.exception.ApiException;
import com.tuempresa.storage.shared.infrastructure.security.AuthUserPrincipal;
import com.tuempresa.storage.shared.infrastructure.security.WarehouseAccessService;
import com.tuempresa.storage.shared.infrastructure.web.PagedResponse;
import com.tuempresa.storage.shared.infrastructure.web.PublicUrlService;
import com.tuempresa.storage.users.domain.Role;
import com.tuempresa.storage.users.domain.User;
import com.tuempresa.storage.users.infrastructure.out.persistence.UserRepository;
import com.tuempresa.storage.warehouses.application.usecase.WarehouseService;
import com.tuempresa.storage.warehouses.domain.Warehouse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);
    private static final Set<ReservationStatus> DUPLICATE_RESERVATION_EXCLUDED_STATUSES = Set.of(
            ReservationStatus.CANCELLED,
            ReservationStatus.EXPIRED
    );

    private final ReservationRepository reservationRepository;
    private final WarehouseService warehouseService;
    private final UserRepository userRepository;
    private final QrCodeService qrCodeService;
    private final PublicUrlService publicUrlService;
    private final NotificationService notificationService;
    private final CustomerEmailService customerEmailService;
    private final WarehouseAccessService warehouseAccessService;
    private final PasswordEncoder passwordEncoder;
    private final CheckinRecordRepository checkinRecordRepository;
    private final CheckoutRecordRepository checkoutRecordRepository;
    private final StoredItemEvidenceRepository storedItemEvidenceRepository;
    private final QrHandoffCaseRepository qrHandoffCaseRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final CancellationRecordRepository cancellationRecordRepository;
    private final long duplicateReservationWindowSeconds;

    public ReservationService(
            ReservationRepository reservationRepository,
            WarehouseService warehouseService,
            UserRepository userRepository,
            QrCodeService qrCodeService,
            PublicUrlService publicUrlService,
            NotificationService notificationService,
            CustomerEmailService customerEmailService,
            WarehouseAccessService warehouseAccessService,
            PasswordEncoder passwordEncoder,
            CheckinRecordRepository checkinRecordRepository,
            CheckoutRecordRepository checkoutRecordRepository,
            StoredItemEvidenceRepository storedItemEvidenceRepository,
            QrHandoffCaseRepository qrHandoffCaseRepository,
            PaymentAttemptRepository paymentAttemptRepository,
            CancellationRecordRepository cancellationRecordRepository,
            @Value("${app.reservations.duplicate-window-seconds:60}") long duplicateReservationWindowSeconds) {
        this.reservationRepository = reservationRepository;
        this.warehouseService = warehouseService;
        this.userRepository = userRepository;
        this.qrCodeService = qrCodeService;
        this.publicUrlService = publicUrlService;
        this.notificationService = notificationService;
        this.customerEmailService = customerEmailService;
        this.warehouseAccessService = warehouseAccessService;
        this.passwordEncoder = passwordEncoder;
        this.checkinRecordRepository = checkinRecordRepository;
        this.checkoutRecordRepository = checkoutRecordRepository;
        this.storedItemEvidenceRepository = storedItemEvidenceRepository;
        this.qrHandoffCaseRepository = qrHandoffCaseRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.cancellationRecordRepository = cancellationRecordRepository;
        this.duplicateReservationWindowSeconds = Math.max(0, duplicateReservationWindowSeconds);
    }

    @Transactional
    public ReservationResponse create(CreateReservationRequest request, AuthUserPrincipal principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_INVALID", "Usuario invalido."));
        if (!user.isEmailVerified()) {
            throw new ApiException(
                    HttpStatus.PRECONDITION_REQUIRED,
                    "ACCOUNT_EMAIL_NOT_VERIFIED",
                    "Debes verificar tu correo antes de crear reservas.");
        }
        if (!user.isProfileCompleted()) {
            throw new ApiException(
                    HttpStatus.PRECONDITION_REQUIRED,
                    "PROFILE_COMPLETION_REQUIRED",
                    "Debes completar tu perfil antes de crear reservas.");
        }
        return createReservation(
                user,
                request.warehouseId(),
                request.startAt(),
                request.endAt(),
                request.estimatedItems(),
                request.bagSize(),
                request.pickupRequested(),
                request.dropoffRequested(),
                request.deliveryRequested(),
                request.extraInsurance());
    }

    @Transactional
    public ReservationResponse createAssisted(CreateAssistedReservationRequest request, AuthUserPrincipal principal) {
        boolean isAdmin = warehouseAccessService.isAdmin(principal);
        boolean isOperatorScope = warehouseAccessService.isOperatorOrCitySupervisor(principal);
        if (!isAdmin && !isOperatorScope) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "RESERVATION_ASSISTED_FORBIDDEN",
                    "No tienes permisos para registrar reservas asistidas.");
        }
        if (!isAdmin && !warehouseAccessService.canAccessWarehouse(principal, request.warehouseId())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "WAREHOUSE_SCOPE_FORBIDDEN",
                    "No puedes registrar reservas asistidas fuera de tu sede asignada.");
        }
        User customer = resolveOrCreateAssistedCustomer(request);

        if (!customer.isEmailVerified()) {
            throw new ApiException(
                    HttpStatus.PRECONDITION_REQUIRED,
                    "ASSISTED_CUSTOMER_EMAIL_NOT_VERIFIED",
                    "El cliente debe verificar su correo antes de crear reservas asistidas.");
        }
        if (!customer.isProfileCompleted()) {
            throw new ApiException(
                    HttpStatus.PRECONDITION_REQUIRED,
                    "ASSISTED_CUSTOMER_PROFILE_INCOMPLETE",
                    "El cliente debe completar su perfil antes de crear reservas asistidas.");
        }

        return createReservation(
                customer,
                request.warehouseId(),
                request.startAt(),
                request.endAt(),
                request.estimatedItems(),
                request.bagSize(),
                request.pickupRequested(),
                request.dropoffRequested(),
                request.deliveryRequested(),
                request.extraInsurance());
    }

    private ReservationResponse createReservation(
            User user,
            Long warehouseId,
            Instant startAt,
            Instant endAt,
            int estimatedItems,
            String bagSizeRaw,
            Boolean pickupRequestedRaw,
            Boolean dropoffRequestedRaw,
            Boolean deliveryRequestedRaw,
            Boolean extraInsuranceRaw) {
        // Idempotency guard to block rapid duplicate reservations.
        if (duplicateReservationWindowSeconds > 0) {
            Instant duplicateThreshold = Instant.now().minusSeconds(duplicateReservationWindowSeconds);
            if (reservationRepository.existsByUserIdAndWarehouseIdAndCreatedAtAfterAndStatusNotIn(
                    user.getId(),
                    warehouseId,
                    duplicateThreshold,
                    DUPLICATE_RESERVATION_EXCLUDED_STATUSES)) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "DUPLICATE_RESERVATION_DETECTED",
                        "Ya has generado una reserva en esta sede hace unos segundos. Revisa tu panel de reservas o intenta nuevamente.");
            }
        }

        if (!endAt.isAfter(startAt)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE",
                    "La fecha de fin debe ser mayor a la de inicio.");
        }
        Warehouse warehouse = warehouseService.requireWarehouseForUpdate(warehouseId);
        long overlaps = reservationRepository.countOverlapping(
                warehouse.getId(),
                startAt,
                endAt,
                ReservationAvailabilityRules.OCCUPYING_STATES);
        if (overlaps >= warehouse.getCapacity()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "WAREHOUSE_CAPACITY_FULL",
                    "No hay disponibilidad para ese rango horario en el almacen seleccionado.");
        }

        ReservationBagSize bagSize = resolveBagSize(bagSizeRaw);
        boolean pickupRequested = Boolean.TRUE.equals(pickupRequestedRaw);
        boolean dropoffRequested = resolveDropoffRequested(dropoffRequestedRaw, deliveryRequestedRaw);
        boolean extraInsurance = Boolean.TRUE.equals(extraInsuranceRaw);
        PricingBreakdown pricing = calculatePrice(
                warehouse,
                startAt,
                endAt,
                estimatedItems,
                bagSize,
                pickupRequested,
                dropoffRequested,
                extraInsurance);
        Reservation reservation = Reservation.createPendingPayment(
                user,
                warehouse,
                startAt,
                endAt,
                pricing.totalPrice(),
                estimatedItems,
                bagSize,
                pickupRequested,
                dropoffRequested,
                extraInsurance,
                pricing.storageAmount(),
                pricing.pickupFee(),
                pricing.dropoffFee(),
                pricing.insuranceFee(),
                Instant.now().plus(Duration.ofMinutes(5)));
        Reservation saved = reservationRepository.save(reservation);
        log.info("Reservation created: id={}, userId={}, warehouseId={}, price={}, qr={}",
                saved.getId(), user.getId(), warehouseId, saved.getTotalPrice(), saved.getQrCode());
        notificationService.notifyReservationCreated(
                saved.getUser().getId(),
                saved.getId(),
                saved.getQrCode(),
                saved.getWarehouse().getName(),
                saved.getStartAt(),
                saved.getEndAt(),
                saved.getTotalPrice());
        customerEmailService.sendReservationCreated(saved.getUser(), saved);
        notifyOperationalUsersForReservationEvent(
                saved,
                "RESERVATION_CREATED_FOR_WAREHOUSE",
                "Nueva reserva en tu sede",
                "Se registro una nueva reserva en " + saved.getWarehouse().getName() + ".");
        return toResponse(saved);
    }

    private User resolveOrCreateAssistedCustomer(CreateAssistedReservationRequest request) {
        String normalizedEmail = normalizeEmail(request.customerEmail());
        NameParts nameParts = NameParts.from(request.customerFullName());
        String nationality = defaultText(request.customerNationality(), "Peru");
        String preferredLanguage = defaultText(request.customerPreferredLanguage(), "es");
        return userRepository.findByEmailIgnoreCase(normalizedEmail)
                .map(existing -> {
                    if (!existing.getRoles().contains(Role.CLIENT)) {
                        throw new ApiException(
                                HttpStatus.BAD_REQUEST,
                                "ASSISTED_CUSTOMER_ROLE_INVALID",
                                "El correo indicado pertenece a un usuario operativo. Usa un correo de cliente.");
                    }
                    if (!existing.isActive()) {
                        existing.setActive(true);
                    }
                    return existing;
                })
                .orElseGet(() -> {
                    String rawPassword = generateTemporaryCustomerPassword();
                    User customer = User.of(
                            request.customerFullName(),
                            normalizedEmail,
                            passwordEncoder.encode(rawPassword),
                            request.customerPhone(),
                            Set.of(Role.CLIENT));
                    customer.applyRegistrationDetails(
                            nameParts.firstName(),
                            nameParts.lastName(),
                            nationality,
                            preferredLanguage,
                            request.customerPhone(),
                            true,
                            null);
                    customer.markEmailVerified();
                    customer.setActive(true);
                    User savedCustomer = userRepository.save(customer);

                    // Aquí puedes usar el customerEmailService para enviar rawPassword al nuevo
                    // usuario.
                    // customerEmailService.sendWelcomeWithTemporaryPassword(savedCustomer,
                    // rawPassword);

                    return savedCustomer;
                });
    }

    @Transactional(readOnly = true)
    public ReservationResponse getById(Long id, AuthUserPrincipal principal) {
        Reservation reservation = loadReservation(id);
        if (!hasReservationAccess(reservation, principal)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "RESERVATION_FORBIDDEN", "No puedes acceder a esta reserva.");
        }
        return toResponse(reservation, principal);
    }

    @Transactional
    public ReservationResponse cancel(Long id, CancelReservationRequest request, AuthUserPrincipal principal) {
        log.info("Cancelling reservation: id={}, userId={}, reason={}", id, principal.getId(), request.reason());
        Reservation reservation = loadReservation(id);
        if (!hasReservationAccess(reservation, principal)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "RESERVATION_FORBIDDEN", "No puedes cancelar esta reserva.");
        }
        ensureRefundNotRequiredBeforeCancel(reservation);
        reservation.cancel(request.reason());
        releasePendingPaymentAttempts(reservation, request.reason());
        notificationService.notifyUser(
                reservation.getUser().getId(),
                "RESERVATION_CANCELLED",
                "Reserva cancelada",
                "La reserva " + reservation.getQrCode() + " fue cancelada.",
                Map.of(
                        "reservationId", reservation.getId(),
                        "reason", request.reason() != null ? request.reason() : ""));
        notifyOperationalUsersForReservationEvent(
                reservation,
                "RESERVATION_CANCELLED_FOR_WAREHOUSE",
                "Reserva cancelada en tu sede",
                "La reserva " + reservation.getQrCode() + " fue cancelada en " + reservation.getWarehouse().getName()
                        + ".");
        return toResponse(reservation);
    }

    @Transactional
    public void discardAbortedCheckout(Long id, AuthUserPrincipal principal) {
        log.info("Discarding aborted checkout reservation: id={}, userId={}", id, principal.getId());
        Reservation reservation = loadReservation(id);
        if (!hasReservationAccess(reservation, principal)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "RESERVATION_FORBIDDEN",
                    "No puedes descartar esta reserva.");
        }
        if (reservation.getStatus() != ReservationStatus.PENDING_PAYMENT) {
            throw new ApiException(HttpStatus.CONFLICT, "ABORTED_CHECKOUT_DISCARD_NOT_ALLOWED",
                    "Solo se pueden eliminar reservas temporales pendientes de pago.");
        }

        List<PaymentAttempt> attempts = paymentAttemptRepository
                .findByReservationIdOrderByCreatedAtDesc(reservation.getId());
        boolean hasProtectedAttempt = attempts.stream()
                .map(PaymentAttempt::getStatus)
                .anyMatch(status -> status != PaymentStatus.PENDING && status != PaymentStatus.FAILED);
        if (hasProtectedAttempt) {
            throw new ApiException(HttpStatus.CONFLICT, "ABORTED_CHECKOUT_DISCARD_NOT_ALLOWED",
                    "La reserva ya tiene un pago que debe conservarse en el historial.");
        }

        cancellationRecordRepository.deleteByReservationId(reservation.getId());
        paymentAttemptRepository.deleteByReservationId(reservation.getId());
        reservationRepository.delete(reservation);
    }

    private void releasePendingPaymentAttempts(Reservation reservation, String reason) {
        List<PaymentAttempt> attempts = paymentAttemptRepository
                .findByReservationIdOrderByCreatedAtDesc(reservation.getId());
        if (attempts.isEmpty()) {
            return;
        }

        String resolvedReason = reason != null && !reason.isBlank()
                ? reason
                : "Reserva cancelada antes de confirmar el pago.";

        List<PaymentAttempt> pendingAttempts = attempts.stream()
                .filter(PaymentAttempt::isPending)
                .toList();
        if (pendingAttempts.isEmpty()) {
            return;
        }

        for (PaymentAttempt attempt : pendingAttempts) {
            attempt.fail(attempt.getProviderReference());
            attempt.registerGatewayOutcome("CANCELLED_BY_RESERVATION", resolvedReason);
        }
        paymentAttemptRepository.saveAll(pendingAttempts);
    }

    private void ensureRefundNotRequiredBeforeCancel(Reservation reservation) {
        PaymentAttempt latestAttempt = paymentAttemptRepository
                .findFirstByReservationIdOrderByCreatedAtDesc(reservation.getId())
                .orElse(null);
        if (latestAttempt == null || latestAttempt.getStatus() != PaymentStatus.CONFIRMED) {
            return;
        }
        PaymentMethod paymentMethod = inferPaymentMethodFromAttempt(latestAttempt);
        if (!paymentMethod.isDigitalOnline()) {
            return;
        }
        throw new ApiException(
                HttpStatus.CONFLICT,
                "RESERVATION_REFUND_REQUIRED",
                "La reserva tiene pago digital confirmado. Debes ejecutar reembolso antes de cancelar.");
    }

    private PaymentMethod inferPaymentMethodFromAttempt(PaymentAttempt attempt) {
        String providerReference = attempt.getProviderReference() == null ? ""
                : attempt.getProviderReference().trim().toLowerCase(Locale.ROOT);

        if (providerReference.startsWith("off_") || providerReference.startsWith("cip_")) {
            return PaymentMethod.CASH;
        }
        if (providerReference.startsWith("wal_plin")) {
            return PaymentMethod.PLIN;
        }
        if (providerReference.startsWith("wal_yape")) {
            return PaymentMethod.YAPE;
        }

        return PaymentMethod.CARD;
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> list(AuthUserPrincipal principal) {
        if (warehouseAccessService.isAdmin(principal)) {
            return reservationRepository.findAllByOrderByCreatedAtDesc()
                    .stream()
                    .map(this::toResponse)
                    .toList();
        }
        if (warehouseAccessService.isOperatorOrCitySupervisor(principal)) {
            Set<Long> warehouseIds = warehouseAccessService.assignedWarehouseIds(principal);
            if (warehouseIds.isEmpty()) {
                return List.of();
            }
            return reservationRepository.findByWarehouseIdInOrderByCreatedAtDesc(warehouseIds)
                    .stream()
                    .map(this::toResponse)
                    .toList();
        }
        return reservationRepository.findByUserIdOrderByCreatedAtDesc(principal.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PagedResponse<ReservationResponse> listPage(
            AuthUserPrincipal principal,
            int page,
            int size,
            ReservationStatus status,
            String query) {
        PageRequest pageRequest = PageRequest.of(
                Math.max(page, 0),
                clampSize(size),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        String normalizedQuery = normalizeQuery(query);
        Page<ReservationResponse> result;
        if (warehouseAccessService.isAdmin(principal)) {
            result = reservationRepository
                    .searchPrivileged(status, normalizedQuery, pageRequest)
                    .map(this::toResponse);
        } else if (warehouseAccessService.isOperatorOrCitySupervisor(principal)) {
            Set<Long> warehouseIds = warehouseAccessService.assignedWarehouseIds(principal);
            if (warehouseIds.isEmpty()) {
                return new PagedResponse<>(List.of(), pageRequest.getPageNumber(), pageRequest.getPageSize(), 0, 0,
                        false, false);
            }
            result = reservationRepository
                    .searchByWarehouses(warehouseIds, status, normalizedQuery, pageRequest)
                    .map(this::toResponse);
        } else {
            result = reservationRepository
                    .searchByUser(principal.getId(), status, normalizedQuery, pageRequest)
                    .map(this::toResponse);
        }
        return PagedResponse.from(result);
    }

    @Transactional(readOnly = true)
    public byte[] qrPng(Long id, AuthUserPrincipal principal) {
        Reservation reservation = loadReservation(id);
        if (!hasReservationAccess(reservation, principal)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "RESERVATION_FORBIDDEN",
                    "No puedes acceder al QR de esta reserva.");
        }
        return qrCodeService.generatePng(reservation.getQrCode());
    }

    @Transactional(readOnly = true)
    public byte[] qrPngPublic(Long id) {
        Reservation reservation = loadReservation(id);
        return qrCodeService.generatePng(reservation.getQrCode());
    }

    @Transactional
    public ReservationResponse markPaymentConfirmed(Long reservationId) {
        return markPaymentConfirmed(reservationId, "online");
    }

    @Transactional
    public ReservationResponse markPaymentConfirmed(Long reservationId, String paymentMethod) {
        log.info("Marking payment confirmed: reservationId={}, method={}", reservationId, paymentMethod);
        Reservation reservation = loadReservation(reservationId);
        // Si la reserva ya fue confirmada (ej: webhook tard­io), no lanzar error
        if (reservation.getStatus() == ReservationStatus.CONFIRMED
                || reservation.getStatus() == ReservationStatus.CHECKIN_PENDING
                || reservation.getStatus() == ReservationStatus.STORED
                || reservation.getStatus() == ReservationStatus.READY_FOR_PICKUP
                || reservation.getStatus() == ReservationStatus.COMPLETED) {
            return toResponse(reservation);
        }
        reservation.confirmPayment();
        String resolvedPaymentMethod = paymentMethod == null || paymentMethod.isBlank() ? "online" : paymentMethod;
        notificationService.notifyPaymentConfirmed(
                reservation.getUser().getId(),
                reservation.getId(),
                reservation.getQrCode(),
                resolvedPaymentMethod);
        customerEmailService.sendPaymentConfirmed(reservation.getUser(), reservation, resolvedPaymentMethod);
        notifyOperationalUsersForReservationEvent(
                reservation,
                "PAYMENT_CONFIRMED_FOR_WAREHOUSE",
                "Pago confirmado en tu sede",
                "Se confirmo el pago de la reserva " + reservation.getId() + ".");
        return toResponse(reservation);
    }

    @Transactional
    public ReservationResponse moveStatus(Long reservationId, ReservationStatus target) {
        log.info("Moving reservation status: id={}, target={}", reservationId, target);
        Reservation reservation = loadReservation(reservationId);
        reservation.transitionTo(target);
        notifyOperationalUsersForReservationEvent(
                reservation,
                "RESERVATION_STATUS_UPDATED",
                "Estado de reserva actualizado",
                "La reserva " + reservation.getId() + " cambio a estado " + target.name() + ".");
        return toResponse(reservation);
    }

    @Transactional(readOnly = true)
    public Reservation requireReservation(Long id) {
        return loadReservation(id);
    }

    @Transactional
    public int expirePendingPaymentsNow() {
        List<Reservation> expiring = reservationRepository.findByStatusAndExpiresAtBefore(
                ReservationStatus.PENDING_PAYMENT,
                Instant.now());
        expiring.forEach(reservation -> {
            reservation.expire();
            notificationService.notifyReservationExpired(reservation.getUser().getId(), reservation.getId(),
                    reservation.getQrCode());
        });
        return expiring.size();
    }

    @Transactional(readOnly = true)
    public List<ReservationExportRow> exportReservations() {
        List<Reservation> reservations = reservationRepository.findAllByOrderByCreatedAtDesc();
        return reservations.stream()
                .map(this::toExportRow)
                .toList();
    }

    private ReservationExportRow toExportRow(Reservation r) {
        return new ReservationExportRow(
                r.getId(),
                r.getQrCode(),
                r.getUser().getId(),
                r.getUser().getFullName(),
                r.getUser().getEmail(),
                r.getWarehouse().getId(),
                r.getWarehouse().getName(),
                r.getWarehouse().getCity().getName(),
                r.getStartAt(),
                r.getEndAt(),
                r.getStatus().name(),
                r.getTotalPrice(),
                r.getEstimatedItems(),
                r.getBagSize() != null ? r.getBagSize().code() : null,
                r.isPickupRequested(),
                r.isDropoffRequested(),
                r.isExtraInsurance(),
                r.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public RevenueReportResponse generateRevenueReport(Instant startDate, Instant endDate) {
        List<Reservation> reservations = reservationRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(startDate,
                endDate);

        List<Reservation> paidReservations = reservations.stream()
                .filter(r -> r.getStatus() == ReservationStatus.CONFIRMED ||
                        r.getStatus() == ReservationStatus.CHECKIN_PENDING ||
                        r.getStatus() == ReservationStatus.STORED ||
                        r.getStatus() == ReservationStatus.READY_FOR_PICKUP ||
                        r.getStatus() == ReservationStatus.OUT_FOR_DELIVERY ||
                        r.getStatus() == ReservationStatus.COMPLETED)
                .toList();

        BigDecimal totalRevenue = paidReservations.stream()
                .map(Reservation::getTotalPrice)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal avgValue = paidReservations.isEmpty()
                ? BigDecimal.ZERO
                : totalRevenue.divide(BigDecimal.valueOf(paidReservations.size()), 2, RoundingMode.HALF_UP);

        Map<Long, List<Reservation>> byWarehouse = paidReservations.stream()
                .collect(Collectors.groupingBy(r -> r.getWarehouse().getId()));

        List<RevenueReportResponse.RevenueByWarehouse> byWarehouseList = byWarehouse.entrySet().stream()
                .map(entry -> {
                    Warehouse w = entry.getValue().get(0).getWarehouse();
                    BigDecimal wRevenue = entry.getValue().stream()
                            .map(Reservation::getTotalPrice)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new RevenueReportResponse.RevenueByWarehouse(w.getId(), w.getName(), wRevenue,
                            entry.getValue().size());
                })
                .sorted((a, b) -> b.revenue().compareTo(a.revenue()))
                .toList();

        Map<String, List<Reservation>> byCity = paidReservations.stream()
                .collect(Collectors.groupingBy(r -> r.getWarehouse().getCity().getName()));

        List<RevenueReportResponse.RevenueByCity> byCityList = byCity.entrySet().stream()
                .map(entry -> {
                    BigDecimal cRevenue = entry.getValue().stream()
                            .map(Reservation::getTotalPrice)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new RevenueReportResponse.RevenueByCity(entry.getKey(), cRevenue, entry.getValue().size());
                })
                .sorted((a, b) -> b.revenue().compareTo(a.revenue()))
                .toList();

        DateTimeFormatter dayFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());
        Map<String, List<Reservation>> byDay = paidReservations.stream()
                .collect(Collectors.groupingBy(r -> dayFormatter.format(r.getCreatedAt())));

        List<RevenueReportResponse.RevenueByDay> byDayList = byDay.entrySet().stream()
                .map(entry -> {
                    BigDecimal dRevenue = entry.getValue().stream()
                            .map(Reservation::getTotalPrice)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new RevenueReportResponse.RevenueByDay(entry.getKey(), dRevenue, entry.getValue().size());
                })
                .sorted(Comparator.comparing(RevenueReportResponse.RevenueByDay::date))
                .toList();

        String periodLabel = "Desde " + dayFormatter.format(startDate) + " hasta " + dayFormatter.format(endDate);

        return new RevenueReportResponse(
                totalRevenue,
                paidReservations.size(),
                avgValue,
                startDate,
                endDate,
                periodLabel,
                byWarehouseList,
                byCityList,
                byDayList);
    }

    private Reservation loadReservation(Long id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RESERVATION_NOT_FOUND",
                        "Reserva no encontrada."));
    }

    private PricingBreakdown calculatePrice(
            Warehouse warehouse,
            Instant startAt,
            Instant endAt,
            int estimatedItems,
            ReservationBagSize bagSize,
            boolean pickupRequested,
            boolean dropoffRequested,
            boolean extraInsurance) {
        long hours = Math.max(1, Duration.between(startAt, endAt).toHours());
        int bagCount = Math.max(1, estimatedItems);
        BigDecimal storageAmount = BigDecimal.valueOf(hours)
                .multiply(BigDecimal.valueOf(bagCount))
                .multiply(hourlyRateForSize(warehouse, bagSize))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal pickupFee = pickupRequested ? warehouse.getPickupFee() : BigDecimal.ZERO;
        BigDecimal dropoffFee = dropoffRequested ? warehouse.getDropoffFee() : BigDecimal.ZERO;
        BigDecimal insuranceFee = extraInsurance ? warehouse.getInsuranceFee() : BigDecimal.ZERO;
        BigDecimal totalPrice = storageAmount
                .add(pickupFee)
                .add(dropoffFee)
                .add(insuranceFee)
                .setScale(2, RoundingMode.HALF_UP);
        return new PricingBreakdown(
                totalPrice,
                storageAmount,
                pickupFee.setScale(2, RoundingMode.HALF_UP),
                dropoffFee.setScale(2, RoundingMode.HALF_UP),
                insuranceFee.setScale(2, RoundingMode.HALF_UP));
    }

    private ReservationBagSize resolveBagSize(String bagSizeRaw) {
        try {
            return ReservationBagSize.fromRaw(bagSizeRaw);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_BAG_SIZE",
                    "Tamano de equipaje invalido. Usa S, M, L o XL.");
        }
    }

    private boolean resolveDropoffRequested(Boolean dropoffRequested, Boolean deliveryRequested) {
        if (dropoffRequested != null) {
            return dropoffRequested;
        }
        return Boolean.TRUE.equals(deliveryRequested);
    }

    private BigDecimal hourlyRateForSize(Warehouse warehouse, ReservationBagSize bagSize) {
        return switch (bagSize) {
            case SMALL -> warehouse.getPricePerHourSmall();
            case MEDIUM -> warehouse.getPricePerHourMedium();
            case LARGE -> warehouse.getPricePerHourLarge();
            case EXTRA_LARGE -> warehouse.getPricePerHourExtraLarge();
        };
    }

    private int clampSize(int requestedSize) {
        if (requestedSize <= 0) {
            return 20;
        }
        return Math.min(requestedSize, 100);
    }

    private String normalizeQuery(String rawQuery) {
        if (rawQuery == null) {
            return null;
        }
        String trimmed = rawQuery.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean hasReservationAccess(Reservation reservation, AuthUserPrincipal principal) {
        if (warehouseAccessService.isAdmin(principal)) {
            return true;
        }
        if (warehouseAccessService.isOperatorOrCitySupervisor(principal)) {
            return warehouseAccessService.canAccessWarehouse(principal, reservation.getWarehouse().getId());
        }
        return reservation.belongsTo(principal.getId());
    }

    private void notifyOperationalUsersForReservationEvent(
            Reservation reservation,
            String type,
            String title,
            String message) {
        Long warehouseId = reservation.getWarehouse().getId();
        String warehouseName = reservation.getWarehouse().getName();
        Set<Long> notifiedUserIds = new HashSet<>();

        List<User> scopedOperators = userRepository.findActiveByAnyRoleAndWarehouseId(
                Set.of(Role.OPERATOR, Role.CITY_SUPERVISOR),
                warehouseId);
        for (User user : scopedOperators) {
            if (!notifiedUserIds.add(user.getId())) {
                continue;
            }
            notificationService.notifyUser(
                    user.getId(),
                    type,
                    title,
                    message,
                    Map.of(
                            "reservationId", reservation.getId(),
                            "warehouseId", warehouseId,
                            "warehouseName", warehouseName,
                            "route", "/operator/reservations"));
        }

        List<User> scopedCouriers = userRepository.findActiveByAnyRoleAndWarehouseId(
                Set.of(Role.COURIER),
                warehouseId);
        for (User user : scopedCouriers) {
            if (!notifiedUserIds.add(user.getId())) {
                continue;
            }
            notificationService.notifyUser(
                    user.getId(),
                    type,
                    title,
                    message,
                    Map.of(
                            "reservationId", reservation.getId(),
                            "warehouseId", warehouseId,
                            "warehouseName", warehouseName,
                            "route", "/courier/services"));
        }

        List<User> admins = userRepository.findActiveByAnyRole(Set.of(Role.ADMIN));
        for (User user : admins) {
            if (!notifiedUserIds.add(user.getId())) {
                continue;
            }
            notificationService.notifyUser(
                    user.getId(),
                    type,
                    title,
                    message,
                    Map.of(
                            "reservationId", reservation.getId(),
                            "warehouseId", warehouseId,
                            "warehouseName", warehouseName,
                            "route", "/admin/reservations"));
        }
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ASSISTED_CUSTOMER_EMAIL_REQUIRED",
                    "Debes indicar correo del cliente.");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String defaultText(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private String generateTemporaryCustomerPassword() {
        String token = UUID.randomUUID().toString().replace("-", "");
        return "Tbx!" + token.substring(0, 12);
    }

    private ReservationResponse toResponse(Reservation reservation) {
        return toResponse(reservation, null, false);
    }

    private ReservationResponse toResponse(Reservation reservation, AuthUserPrincipal viewer) {
        return toResponse(reservation, viewer, true);
    }

    private ReservationResponse toResponse(
            Reservation reservation,
            AuthUserPrincipal viewer,
            boolean includeOperationalDetail) {
        String qrDataUrl = qrCodeService.generateDataUrl(reservation.getQrCode());
        String qrImageUrl = publicUrlService.absolute("/api/v1/reservations/" + reservation.getId() + "/qr");
        ReservationOperationalDetailResponse operationalDetail = includeOperationalDetail
                ? buildOperationalDetail(reservation, viewer)
                : null;
        return new ReservationResponse(
                reservation.getId(),
                reservation.getUser().getId(),
                reservation.getWarehouse().getId(),
                reservation.getWarehouse().getName(),
                reservation.getWarehouse().getAddress(),
                reservation.getWarehouse().getCity().getName(),
                reservation.getWarehouse().getZone() != null ? reservation.getWarehouse().getZone().getName() : null,
                reservation.getWarehouse().getLatitude(),
                reservation.getWarehouse().getLongitude(),
                reservation.getStartAt(),
                reservation.getEndAt(),
                reservation.getStatus(),
                reservation.getTotalPrice(),
                reservation.getEstimatedItems(),
                reservation.getBagSize().code(),
                reservation.isPickupRequested(),
                reservation.isDropoffRequested(),
                reservation.isExtraInsurance(),
                reservation.getStorageAmount(),
                reservation.getPickupFee(),
                reservation.getDropoffFee(),
                reservation.getInsuranceFee(),
                reservation.getLatePickupSurcharge(),
                reservation.getQrCode(),
                qrImageUrl,
                qrDataUrl,
                reservation.getExpiresAt(),
                reservation.getCancelReason(),
                operationalDetail);
    }

    private ReservationOperationalDetailResponse buildOperationalDetail(
            Reservation reservation,
            AuthUserPrincipal viewer) {
        QrHandoffCase handoff = qrHandoffCaseRepository.findByReservationId(reservation.getId()).orElse(null);
        CheckinRecord checkin = checkinRecordRepository
                .findFirstByReservationIdOrderByCreatedAtDesc(reservation.getId()).orElse(null);
        CheckoutRecord checkout = checkoutRecordRepository
                .findFirstByReservationIdOrderByCreatedAtDesc(reservation.getId()).orElse(null);
        List<StoredItemEvidence> warehouseLuggagePhotos = storedItemEvidenceRepository
                .findByReservationIdAndTypeOrderByCreatedAtAsc(
                        reservation.getId(),
                        InventoryService.LUGGAGE_PHOTO_EVIDENCE_TYPE);
        List<StoredItemEvidence> clientHandoffPhotos = storedItemEvidenceRepository
                .findByReservationIdAndTypeOrderByCreatedAtAsc(
                        reservation.getId(),
                        InventoryService.CLIENT_HANDOFF_PHOTO_EVIDENCE_TYPE);
        List<StoredItemEvidence> luggagePhotos = new java.util.ArrayList<>(
                warehouseLuggagePhotos.size() + clientHandoffPhotos.size());
        luggagePhotos.addAll(clientHandoffPhotos);
        luggagePhotos.addAll(warehouseLuggagePhotos);
        luggagePhotos.sort(java.util.Comparator.comparing(StoredItemEvidence::getCreatedAt,
                java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));
        boolean canViewBagTag = canViewBagTag(viewer, reservation);
        boolean canViewPickupPin = canViewPickupPin(viewer, reservation);
        boolean canViewLuggagePhotos = canViewLuggagePhotos(viewer, reservation);
        int expectedLuggagePhotos = handoff != null
                ? Math.max(1, handoff.getBagUnits())
                : Math.max(1, reservation.getEstimatedItems());
        return new ReservationOperationalDetailResponse(
                handoff == null || handoff.getStage() == null ? null : handoff.getStage().name(),
                canViewBagTag && handoff != null ? handoff.getBagTagId() : null,
                canViewBagTag && handoff != null ? handoff.getBagTagQrPayload() : null,
                handoff != null ? handoff.getBagUnits() : expectedLuggagePhotos,
                handoff != null && handoff.getPickupPinHash() != null && !handoff.getPickupPinHash().isBlank(),
                canViewPickupPin,
                canViewPickupPin && handoff != null ? handoff.getPickupPinPreview() : null,
                canViewLuggagePhotos,
                !warehouseLuggagePhotos.isEmpty() || isLuggageRegistrationClosed(reservation),
                expectedLuggagePhotos,
                warehouseLuggagePhotos.size(),
                checkin == null ? null : checkin.getCreatedAt(),
                checkout == null ? null : checkout.getCreatedAt(),
                canViewLuggagePhotos
                        ? luggagePhotos.stream().map(this::toLuggagePhotoResponse).toList()
                        : List.of());
    }

    private ReservationLuggagePhotoResponse toLuggagePhotoResponse(StoredItemEvidence evidence) {
        return new ReservationLuggagePhotoResponse(
                evidence.getId(),
                evidence.getType(),
                evidence.getBagUnitIndex(),
                evidence.getUrl(),
                evidence.getCreatedAt(),
                evidence.getOperator() == null ? null : evidence.getOperator().getId(),
                evidence.getOperator() == null ? null : evidence.getOperator().getFullName());
    }

    private boolean canViewBagTag(AuthUserPrincipal viewer, Reservation reservation) {
        if (viewer == null || reservation == null) {
            return false;
        }
        if (warehouseAccessService.isAdmin(viewer)) {
            return true;
        }
        if (reservation.belongsTo(viewer.getId())) {
            return true;
        }
        if (warehouseAccessService.isOperatorOrCitySupervisor(viewer) || warehouseAccessService.isCourier(viewer)) {
            return warehouseAccessService.canAccessWarehouse(viewer, reservation.getWarehouse().getId());
        }
        return false;
    }

    private boolean canViewPickupPin(AuthUserPrincipal viewer, Reservation reservation) {
        if (viewer == null || reservation == null) {
            return false;
        }
        if (warehouseAccessService.isAdmin(viewer)) {
            return true;
        }
        if (reservation.belongsTo(viewer.getId())) {
            return true;
        }
        if (warehouseAccessService.isCourier(viewer)
                && warehouseAccessService.canAccessWarehouse(viewer, reservation.getWarehouse().getId())) {
            return true;
        }
        return warehouseAccessService.isOperatorOrCitySupervisor(viewer)
                && warehouseAccessService.canAccessWarehouse(viewer, reservation.getWarehouse().getId());
    }

    private boolean canViewLuggagePhotos(AuthUserPrincipal viewer, Reservation reservation) {
        if (viewer == null || reservation == null) {
            return false;
        }
        if (warehouseAccessService.isAdmin(viewer)) {
            return true;
        }
        if (reservation.belongsTo(viewer.getId())) {
            return true;
        }
        if (warehouseAccessService.isOperatorOrCitySupervisor(viewer) || warehouseAccessService.isCourier(viewer)) {
            return warehouseAccessService.canAccessWarehouse(viewer, reservation.getWarehouse().getId());
        }
        return false;
    }

    private boolean isLuggageRegistrationClosed(Reservation reservation) {
        return reservation.getStatus() == ReservationStatus.STORED
                || reservation.getStatus() == ReservationStatus.READY_FOR_PICKUP
                || reservation.getStatus() == ReservationStatus.OUT_FOR_DELIVERY
                || reservation.getStatus() == ReservationStatus.COMPLETED;
    }

    private record NameParts(String firstName, String lastName) {
        private static NameParts from(String fullName) {
            String normalized = fullName == null ? "" : fullName.trim();
            if (normalized.isEmpty()) {
                return new NameParts("Cliente", "TravelBox");
            }
            String[] parts = normalized.split("\\s+");
            if (parts.length == 1) {
                return new NameParts(parts[0], parts[0]);
            }
            int middle = Math.max(1, parts.length / 2);
            String first = String.join(" ", java.util.Arrays.copyOfRange(parts, 0, middle));
            String last = String.join(" ", java.util.Arrays.copyOfRange(parts, middle, parts.length));
            return new NameParts(first, last);
        }
    }

    private record PricingBreakdown(
            BigDecimal totalPrice,
            BigDecimal storageAmount,
            BigDecimal pickupFee,
            BigDecimal dropoffFee,
            BigDecimal insuranceFee) {
    }
}
