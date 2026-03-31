package com.tuempresa.storage.payments.application.usecase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuempresa.storage.notifications.application.usecase.NotificationService;
import com.tuempresa.storage.payments.application.dto.CashPendingPaymentResponse;
import com.tuempresa.storage.payments.application.dto.ConfirmPaymentRequest;
import com.tuempresa.storage.payments.application.dto.CreatePaymentIntentRequest;
import com.tuempresa.storage.payments.application.dto.PaymentHistoryItemResponse;
import com.tuempresa.storage.payments.application.dto.PaymentIntentResponse;
import com.tuempresa.storage.payments.application.dto.PaymentStatusResponse;
import com.tuempresa.storage.payments.application.dto.PaymentWebhookResponse;
import com.tuempresa.storage.payments.domain.PaymentAttempt;
import com.tuempresa.storage.payments.domain.PaymentMethod;
import com.tuempresa.storage.payments.domain.PaymentStatus;
import com.tuempresa.storage.payments.domain.PaymentWebhookEvent;
import com.tuempresa.storage.payments.infrastructure.out.gateway.CulqiGatewayClient;
import com.tuempresa.storage.payments.infrastructure.out.persistence.PaymentAttemptRepository;
import com.tuempresa.storage.payments.infrastructure.out.persistence.PaymentWebhookEventRepository;
import com.tuempresa.storage.reservations.application.dto.CancelReservationRequest;
import com.tuempresa.storage.reservations.application.usecase.ReservationService;
import com.tuempresa.storage.reservations.domain.Reservation;
import com.tuempresa.storage.reservations.domain.ReservationStatus;
import com.tuempresa.storage.shared.domain.exception.ApiException;
import com.tuempresa.storage.shared.infrastructure.security.AuthUserPrincipal;
import com.tuempresa.storage.shared.infrastructure.security.WarehouseAccessService;
import com.tuempresa.storage.shared.infrastructure.web.PagedResponse;
import com.tuempresa.storage.users.domain.Role;
import com.tuempresa.storage.users.domain.User;
import com.tuempresa.storage.users.infrastructure.out.persistence.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class PaymentService {

    private static final int MAX_PAGE_SIZE = 100;

    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentWebhookEventRepository paymentWebhookEventRepository;
    private final ReservationService reservationService;
    private final CulqiGatewayClient culqiGatewayClient;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final WarehouseAccessService warehouseAccessService;
    private final UserRepository userRepository;
    private final String paymentProvider;
    private final boolean forceCashOnly;
    private final boolean allowMockConfirmation;
    private final String currencyCode;
    private final int refundCommissionGraceMinutes;
    private final BigDecimal refundCommissionPercentAfterGrace;
    private final BigDecimal refundMinimumFee;

    public PaymentService(
            PaymentAttemptRepository paymentAttemptRepository,
            PaymentWebhookEventRepository paymentWebhookEventRepository,
            ReservationService reservationService,
            CulqiGatewayClient culqiGatewayClient,
            NotificationService notificationService,
            ObjectMapper objectMapper,
            WarehouseAccessService warehouseAccessService,
            UserRepository userRepository,
            @Value("${app.payments.provider:culqi}") String paymentProvider,
            @Value("${app.payments.force-cash-only:false}") boolean forceCashOnly,
            @Value("${app.payments.allow-mock-confirmation:true}") boolean allowMockConfirmation,
            @Value("${app.payments.currency:PEN}") String currencyCode,
            @Value("${app.payments.refunds.commission-grace-minutes:60}") int refundCommissionGraceMinutes,
            @Value("${app.payments.refunds.commission-percent-after-grace:4.50}") BigDecimal refundCommissionPercentAfterGrace,
            @Value("${app.payments.refunds.minimum-fee:0.00}") BigDecimal refundMinimumFee
    ) {
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.paymentWebhookEventRepository = paymentWebhookEventRepository;
        this.reservationService = reservationService;
        this.culqiGatewayClient = culqiGatewayClient;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
        this.warehouseAccessService = warehouseAccessService;
        this.userRepository = userRepository;
        this.paymentProvider = paymentProvider == null ? "culqi" : paymentProvider.trim().toLowerCase(Locale.ROOT);
        this.forceCashOnly = forceCashOnly;
        this.allowMockConfirmation = allowMockConfirmation;
        this.currencyCode = currencyCode == null ? "PEN" : currencyCode.trim().toUpperCase(Locale.ROOT);
        this.refundCommissionGraceMinutes = Math.max(0, refundCommissionGraceMinutes);
        this.refundCommissionPercentAfterGrace = normalizePercent(refundCommissionPercentAfterGrace);
        this.refundMinimumFee = normalizeMoney(refundMinimumFee);
    }

    @Transactional
    public PaymentIntentResponse createIntent(CreatePaymentIntentRequest request, AuthUserPrincipal principal) {
        Reservation reservation = reservationService.requireReservation(request.reservationId());
        assertPaymentPermission(reservation, principal);
        if (reservation.getStatus() != ReservationStatus.PENDING_PAYMENT) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_PAYMENT_STATE", "La reserva no esta pendiente de pago.");
        }
        PaymentAttempt attempt = paymentAttemptRepository
                .findFirstByReservationIdAndStatusOrderByCreatedAtDesc(reservation.getId(), PaymentStatus.PENDING)
                .orElseGet(() -> paymentAttemptRepository.save(PaymentAttempt.pending(reservation, reservation.getTotalPrice())));
        attempt.registerGatewayOutcome("INTENT_CREATED", "Intento de pago creado.");
        return toIntentResponse(attempt, providerLabel(attempt), "unknown", "INTENT_CREATED", "Intento de pago creado.", null);
    }

    @Transactional
    public PaymentIntentResponse confirm(ConfirmPaymentRequest request, AuthUserPrincipal principal) {
        PaymentAttempt attempt = resolveAttempt(request, principal);
        if (!attempt.isPending()) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_ALREADY_PROCESSED", "El pago ya fue procesado.");
        }

        PaymentMethod method = resolveMethod(attempt, request.paymentMethod());
        if (forceCashOnly && method.isDigitalOnline()) {
            method = PaymentMethod.CASH;
        }
        boolean approved = request.approved() == null || request.approved();
        if (method == PaymentMethod.COUNTER || method == PaymentMethod.CASH) {
            return processOffline(attempt, method, approved, request.providerReference(), principal);
        }
        if (!approved) {
            String ref = normalizeRef(attempt, request.providerReference(), method.label());
            attempt.fail(ref);
            attempt.registerGatewayOutcome("DECLINED", "Pago rechazado por solicitud del cliente.");
            List<User> operators = userRepository.findActiveByAnyRoleAndWarehouseId(Set.of(Role.OPERATOR, Role.CITY_SUPERVISOR), attempt.getReservation().getWarehouse().getId());
            if (operators != null) {
                for (User user : operators) {
                    notificationService.emitSilentRealtimeEvent(user.getId(), "PAYMENT_SYNC", java.util.Map.of("reservationId", attempt.getReservation().getId()));
                }
            }
            notificationService.notifyPaymentRejected(attempt.getReservation().getUser().getId(), attempt.getReservation().getId(), attempt.getReservation().getQrCode(), "Rechazado por cliente");
            return toIntentResponse(attempt, providerLabel(attempt), method.label(), "DECLINED", "Pago rechazado.", null);
        }

        if (!"culqi".equals(paymentProvider)) {
            if (!allowMockConfirmation) {
                throw new ApiException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "PAYMENT_PROVIDER_UNAVAILABLE",
                        "Proveedor de pagos no habilitado para confirmacion mock en este entorno."
                );
            }
            String ref = normalizeRef(attempt, request.providerReference(), method.label());
            attempt.confirm(ref);
            attempt.registerGatewayOutcome("MOCK_CONFIRMED", "Pago confirmado en modo mock.");
            reservationService.markPaymentConfirmed(attempt.getReservation().getId(), method.label());
            return toIntentResponse(attempt, "MOCK", method.label(), "DIRECT_CONFIRMATION", "Pago confirmado en modo mock.", null);
        }

        if (method.isDirectChargeFlow()) {
            return confirmCulqiCharge(attempt, method, request, principal);
        }
        if (method.isCheckoutOrderFlow()) {
            return createCulqiOrder(attempt, method, request, principal);
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_METHOD_UNSUPPORTED", "Metodo no soportado.");
    }

    @Transactional(readOnly = true)
    public PaymentStatusResponse status(Long paymentIntentId, Long reservationId, AuthUserPrincipal principal) {
        PaymentAttempt attempt;
        if (paymentIntentId != null) {
            attempt = paymentAttemptRepository.findById(paymentIntentId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Pago no encontrado."));
        } else if (reservationId != null) {
            attempt = paymentAttemptRepository.findFirstByReservationIdOrderByCreatedAtDesc(reservationId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Pago no encontrado."));
        } else {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_IDENTIFIER_REQUIRED", "Debes enviar paymentIntentId o reservationId.");
        }
        assertPaymentPermission(attempt.getReservation(), principal);
        PaymentMethod method = resolveMethod(attempt, null);
        return new PaymentStatusResponse(
                attempt.getId(),
                attempt.getReservation().getId(),
                attempt.getStatus(),
                attempt.getReservation().getStatus(),
                attempt.getAmount(),
                method.label(),
                providerLabel(attempt),
                attempt.getProviderReference(),
                flowLabel(attempt, method),
                attempt.getGatewayStatus(),
                attempt.getGatewayMessage(),
                attempt.getCreatedAt(),
                attempt.getReservation().getExpiresAt()
        );
    }

    @Transactional(readOnly = true)
    public PagedResponse<PaymentHistoryItemResponse> history(AuthUserPrincipal principal, int page, int size) {
        return history(principal, page, size, null);
    }

    @Transactional(readOnly = true)
    public PagedResponse<PaymentHistoryItemResponse> history(
            AuthUserPrincipal principal,
            int page,
            int size,
            PaymentStatus status
    ) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), clampSize(size), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<PaymentHistoryItemResponse> mapped;
        if (hasPrivilegedRole(principal)) {
            mapped = (status == null
                    ? paymentAttemptRepository.findAllByOrderByCreatedAtDesc(pageable)
                    : paymentAttemptRepository.findByStatusOrderByCreatedAtDesc(status, pageable))
                    .map(this::toHistory);
        } else {
            mapped = (status == null
                    ? paymentAttemptRepository.findByReservationUserIdOrderByCreatedAtDesc(principal.getId(), pageable)
                    : paymentAttemptRepository.findByReservationUserIdAndStatusOrderByCreatedAtDesc(
                    principal.getId(),
                    status,
                    pageable
            )).map(this::toHistory);
        }
        return PagedResponse.from(mapped);
    }

    @Transactional(readOnly = true)
    public PagedResponse<CashPendingPaymentResponse> listCashPending(AuthUserPrincipal principal, int page, int size) {
        requirePrivileged(principal);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), clampSize(size), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<CashPendingPaymentResponse> mapped;
        if (warehouseAccessService.isAdmin(principal)) {
            mapped = paymentAttemptRepository.findOfflineCashPending(PaymentStatus.PENDING, pageable).map(this::toCashPending);
        } else {
            java.util.Set<Long> warehouseIds = warehouseAccessService.assignedWarehouseIds(principal);
            if (warehouseIds.isEmpty()) {
                return new PagedResponse<>(List.of(), pageable.getPageNumber(), pageable.getPageSize(), 0, 0, false, false);
            }
            mapped = paymentAttemptRepository
                    .findOfflineCashPendingByWarehouses(PaymentStatus.PENDING, warehouseIds, pageable)
                    .map(this::toCashPending);
        }
        return PagedResponse.from(mapped);
    }

    @Transactional
    public PaymentIntentResponse approveCashPayment(Long paymentIntentId, String providerReference, String reason, AuthUserPrincipal principal) {
        requirePrivileged(principal);
        PaymentAttempt attempt = requirePending(paymentIntentId);
        assertPaymentPermission(attempt.getReservation(), principal);
        PaymentMethod method = resolveMethod(attempt, null);
        if (!(method == PaymentMethod.COUNTER || method == PaymentMethod.CASH)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_NOT_CASH", "Este pago no es de caja.");
        }
        String ref = normalizeRef(attempt, providerReference, method.label());
        attempt.confirm(ref);
        attempt.registerGatewayOutcome("OFFLINE_CONFIRMED_BY_OPERATOR", defaultReason(reason, "Pago en caja confirmado por operador."));
        reservationService.markPaymentConfirmed(attempt.getReservation().getId(), method.label());
        return toIntentResponse(attempt, "OFFLINE", method.label(), "OFFLINE_CONFIRMED_BY_OPERATOR", defaultReason(reason, "Pago confirmado."), null);
    }

    @Transactional
    public PaymentIntentResponse rejectCashPayment(Long paymentIntentId, String providerReference, String reason, AuthUserPrincipal principal) {
        requirePrivileged(principal);
        PaymentAttempt attempt = requirePending(paymentIntentId);
        assertPaymentPermission(attempt.getReservation(), principal);
        PaymentMethod method = resolveMethod(attempt, null);
        if (!(method == PaymentMethod.COUNTER || method == PaymentMethod.CASH)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_NOT_CASH", "Este pago no es de caja.");
        }
        String ref = normalizeRef(attempt, providerReference, method.label());
        String message = defaultReason(reason, "Pago en caja rechazado por operador.");
        attempt.fail(ref);
        attempt.registerGatewayOutcome("OFFLINE_REJECTED_BY_OPERATOR", message);
        List<User> rejectOperators = userRepository.findActiveByAnyRoleAndWarehouseId(Set.of(Role.OPERATOR, Role.CITY_SUPERVISOR), attempt.getReservation().getWarehouse().getId());
        if (rejectOperators != null) {
            for (User user : rejectOperators) {
                notificationService.emitSilentRealtimeEvent(user.getId(), "PAYMENT_SYNC", java.util.Map.of("reservationId", attempt.getReservation().getId()));
            }
        }
        notificationService.notifyPaymentRejected(attempt.getReservation().getUser().getId(), attempt.getReservation().getId(), attempt.getReservation().getQrCode(), message);
        return toIntentResponse(attempt, "OFFLINE", method.label(), "OFFLINE_REJECTED_BY_OPERATOR", message, null);
    }

    @Transactional
    public PaymentIntentResponse refund(Long paymentIntentId, String reason, AuthUserPrincipal principal) {
        PaymentAttempt attempt = paymentAttemptRepository.findById(paymentIntentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Pago no encontrado."));
        assertPaymentPermission(attempt.getReservation(), principal);

        if (!attempt.isConfirmed()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "PAYMENT_REFUND_NOT_ALLOWED",
                    "Solo se pueden reembolsar pagos confirmados."
            );
        }

        PaymentMethod method = resolveMethod(attempt, null);
        if (!method.isDigitalOnline()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "PAYMENT_REFUND_NOT_REQUIRED",
                    "Este pago no requiere reembolso digital. Puedes cancelarlo por flujo operativo."
            );
        }

        BigDecimal amount = normalizeMoney(attempt.getAmount());
        BigDecimal fee = calculateRefundFee(amount, attempt.getCreatedAt(), Instant.now());
        BigDecimal refundAmount = amount.subtract(fee).setScale(2, RoundingMode.HALF_UP);
        if (refundAmount.signum() < 0) {
            refundAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        String normalizedReason = defaultReason(reason, "Reembolso solicitado por operacion.");
        String providerReference = attempt.getProviderReference();
        String providerMessage = "Reembolso aplicado.";
        String flow = "REFUND_EXECUTED";

        if ("culqi".equals(paymentProvider) && culqiGatewayClient.isConfigured()) {
            String chargeId = resolveCulqiChargeId(providerReference);
            if (chargeId != null) {
                CulqiGatewayClient.CulqiRefundResult result = culqiGatewayClient.createRefund(
                        new CulqiGatewayClient.CulqiRefundRequest(
                                chargeId,
                                toCents(refundAmount),
                                normalizedReason
                        )
                );
                providerMessage = firstNonBlank(result.message(), providerMessage);
                flow = "REFUND_EXECUTED_CULQI";
            } else {
                flow = "REFUND_EXECUTED_INTERNAL";
                providerMessage = "Reembolso aplicado internamente (sin chargeId directo de Culqi).";
            }
        } else {
            flow = "REFUND_EXECUTED_INTERNAL";
            providerMessage = "Reembolso aplicado en modo interno.";
        }

        String summaryMessage = buildRefundSummary(providerMessage, refundAmount, fee);
        attempt.refund(providerReference, refundAmount, fee, normalizedReason);
        attempt.registerGatewayOutcome("REFUNDED", summaryMessage);

        String cancelReason = "Reserva cancelada por reembolso. " + summaryMessage;
        reservationService.cancel(
                attempt.getReservation().getId(),
                new CancelReservationRequest(cancelReason),
                principal
        );

        notificationService.notifyUser(
                attempt.getReservation().getUser().getId(),
                "PAYMENT_REFUNDED",
                "Reembolso aplicado",
                summaryMessage,
                Map.of(
                        "reservationId", attempt.getReservation().getId(),
                        "paymentIntentId", attempt.getId(),
                        "refundAmount", refundAmount,
                        "refundFee", fee
                )
        );

        return toIntentResponse(
                attempt,
                providerLabel(attempt),
                method.label(),
                flow,
                summaryMessage,
                null
        );
    }

    @Transactional
    public PaymentWebhookResponse processCulqiWebhook(String rawPayload, String signature) {
        String safePayload = rawPayload == null ? "" : rawPayload;
        JsonNode payload;
        try {
            payload = objectMapper.readTree(safePayload);
        } catch (Exception ex) {
            return new PaymentWebhookResponse(
                    false,
                    false,
                    null,
                    null,
                    null,
                    null,
                    "Payload webhook invalido.",
                    null,
                    null
            );
        }

        String provider = "culqi";
        String eventType = firstNonBlank(textAt(payload, "type"), textAt(payload, "event_type"), "unknown");
        String eventId = firstNonBlank(textAt(payload, "id"), textAt(payload, "event_id"));
        if (!StringUtils.hasText(eventId)) {
            eventId = "sha256:" + culqiGatewayClient.sha256Hex(safePayload);
        }

        String providerReference = firstNonBlank(
                textAt(payload, "data.object.id"),
                textAt(payload, "data.id"),
                textAt(payload, "data.object.order_id"),
                textAt(payload, "data.object.charge_id"),
                textAt(payload, "data.object.metadata.providerReference"),
                textAt(payload, "data.object.metadata.paymentReference")
        );

        String providerStatus = firstNonBlank(
                textAt(payload, "data.object.outcome.type"),
                textAt(payload, "data.object.status"),
                textAt(payload, "data.object.state"),
                textAt(payload, "status"),
                eventType
        );

        String providerMessage = firstNonBlank(
                textAt(payload, "data.object.outcome.user_message"),
                textAt(payload, "data.object.user_message"),
                textAt(payload, "data.object.response_message"),
                textAt(payload, "message"),
                "Webhook procesado."
        );

        PaymentWebhookEvent existing = paymentWebhookEventRepository.findByProviderAndEventId(provider, eventId).orElse(null);
        if (existing != null) {
            return new PaymentWebhookResponse(
                    existing.isProcessed(),
                    true,
                    existing.getEventId(),
                    existing.getEventType(),
                    existing.getProviderReference(),
                    existing.getProcessingStatus().name(),
                    "Evento webhook ya procesado.",
                    existing.getPaymentAttemptId(),
                    existing.getReservationId()
            );
        }

        PaymentWebhookEvent event = paymentWebhookEventRepository.save(PaymentWebhookEvent.received(
                provider,
                eventId,
                eventType,
                providerReference,
                safePayload
        ));

        if (!culqiGatewayClient.validateWebhookSignature(safePayload, signature)) {
            event.markFailed("Firma webhook invalida.", null, null);
            return new PaymentWebhookResponse(
                    false,
                    false,
                    eventId,
                    eventType,
                    providerReference,
                    "INVALID_SIGNATURE",
                    "Firma webhook invalida.",
                    null,
                    null
            );
        }

        try {
            PaymentAttempt attempt = resolveAttemptForWebhook(payload, providerReference);
            if (attempt == null) {
                event.markIgnored("No se encontro intento de pago para webhook.", null, null);
                return new PaymentWebhookResponse(
                        true,
                        false,
                        eventId,
                        eventType,
                        providerReference,
                        "IGNORED",
                        "No existe intento de pago asociado.",
                        null,
                        null
                );
            }

            Long paymentIntentId = attempt.getId();
            Long reservationId = attempt.getReservation().getId();
            String effectiveReference = StringUtils.hasText(providerReference) ? providerReference : attempt.getProviderReference();

            if (!attempt.isPending()) {
                event.markIgnored("Intento de pago ya procesado.", paymentIntentId, reservationId);
                return new PaymentWebhookResponse(
                        true,
                        false,
                        eventId,
                        eventType,
                        effectiveReference,
                        "ALREADY_PROCESSED",
                        "El intento de pago ya no esta pendiente.",
                        paymentIntentId,
                        reservationId
                );
            }

            if (webhookApproved(eventType, providerStatus, payload)) {
                attempt.confirm(effectiveReference);
                attempt.registerGatewayOutcome(firstNonBlank(providerStatus, eventType), providerMessage);
                reservationService.markPaymentConfirmed(reservationId, resolveMethod(attempt, null).label());
                event.markProcessed(paymentIntentId, reservationId);
                return new PaymentWebhookResponse(
                        true,
                        false,
                        eventId,
                        eventType,
                        effectiveReference,
                        firstNonBlank(providerStatus, "APPROVED"),
                        "Pago confirmado por webhook.",
                        paymentIntentId,
                        reservationId
                );
            }

            if (webhookRejected(eventType, providerStatus, payload)) {
                attempt.fail(effectiveReference);
                attempt.registerGatewayOutcome(firstNonBlank(providerStatus, eventType), providerMessage);
                notificationService.notifyPaymentRejected(
                        attempt.getReservation().getUser().getId(),
                        reservationId,
                        attempt.getReservation().getQrCode(),
                        providerMessage
                );
                event.markProcessed(paymentIntentId, reservationId);
                return new PaymentWebhookResponse(
                        true,
                        false,
                        eventId,
                        eventType,
                        effectiveReference,
                        firstNonBlank(providerStatus, "REJECTED"),
                        "Pago rechazado por webhook.",
                        paymentIntentId,
                        reservationId
                );
            }

            event.markIgnored("Evento webhook no mapea a estado final.", paymentIntentId, reservationId);
            return new PaymentWebhookResponse(
                    true,
                    false,
                    eventId,
                    eventType,
                    effectiveReference,
                    firstNonBlank(providerStatus, "IGNORED"),
                    "Evento webhook ignorado.",
                    paymentIntentId,
                    reservationId
            );
        } catch (Exception ex) {
            event.markFailed(ex.getMessage(), null, null);
            return new PaymentWebhookResponse(
                    false,
                    false,
                    eventId,
                    eventType,
                    providerReference,
                    "FAILED",
                    firstNonBlank(ex.getMessage(), "No se pudo procesar el webhook."),
                    null,
                    null
            );
        }
    }

    private PaymentIntentResponse processOffline(
            PaymentAttempt attempt,
            PaymentMethod method,
            boolean approved,
            String providerReference,
            AuthUserPrincipal principal
    ) {
        String reference = normalizeRef(attempt, providerReference, method.label());
        if (!approved) {
            attempt.fail(reference);
            attempt.registerGatewayOutcome("OFFLINE_REJECTED", "Pago en caja rechazado.");
            notificationService.notifyPaymentRejected(
                    attempt.getReservation().getUser().getId(),
                    attempt.getReservation().getId(),
                    attempt.getReservation().getQrCode(),
                    "Pago en caja rechazado."
            );
            return toIntentResponse(attempt, "OFFLINE", method.label(), "OFFLINE_REJECTED", "Pago rechazado.", null);
        }

        if (hasPrivilegedRole(principal)) {
            attempt.confirm(reference);
            attempt.registerGatewayOutcome("OFFLINE_CONFIRMED_BY_OPERATOR", "Pago en caja confirmado por operador.");
            reservationService.markPaymentConfirmed(attempt.getReservation().getId(), method.label());
            return toIntentResponse(
                    attempt,
                    "OFFLINE",
                    method.label(),
                    "OFFLINE_CONFIRMED_BY_OPERATOR",
                    "Pago confirmado por operador.",
                    null
            );
        }

        attempt.registerProviderReference(reference);
        attempt.registerGatewayOutcome("WAITING_OFFLINE_VALIDATION", "Pago en caja pendiente de validacion por operador.");
        notificationService.notifyPaymentPendingCashValidation(
                attempt.getReservation().getUser().getId(),
                attempt.getReservation().getId(),
                attempt.getReservation().getQrCode()
        );
        notifyOperationalCashPending(attempt);
        Map<String, Object> nextAction = new LinkedHashMap<>();
        nextAction.put("type", "WAIT_FOR_OPERATOR");
        nextAction.put("reservationId", attempt.getReservation().getId());
        nextAction.put("paymentIntentId", attempt.getId());
        return toIntentResponse(
                attempt,
                "OFFLINE",
                method.label(),
                "WAITING_OFFLINE_VALIDATION",
                "Tu pago en caja quedo pendiente de validacion.",
                nextAction
        );
    }

    private void notifyOperationalCashPending(PaymentAttempt attempt) {
        Reservation reservation = attempt.getReservation();
        Long warehouseId = reservation.getWarehouse().getId();
        String warehouseName = reservation.getWarehouse().getName();
        Long reservationId = reservation.getId();
        Long paymentIntentId = attempt.getId();
        Set<Long> notifiedUserIds = new HashSet<>();

        List<User> scopedOperators = userRepository.findActiveByAnyRoleAndWarehouseId(
                Set.of(Role.OPERATOR, Role.CITY_SUPERVISOR),
                warehouseId
        );
        for (User user : scopedOperators) {
            if (!notifiedUserIds.add(user.getId())) {
                continue;
            }
            notificationService.notifyUser(
                    user.getId(),
                    "PAYMENT_PENDING_CASH_VALIDATION_FOR_WAREHOUSE",
                    "Pago en caja pendiente",
                    "Hay un pago en caja pendiente de validacion para la reserva " + reservation.getQrCode() + ".",
                    Map.of(
                            "reservationId", reservationId,
                            "paymentIntentId", paymentIntentId,
                            "warehouseId", warehouseId,
                            "warehouseName", warehouseName,
                            "route", "/operator/cash-payments"
                    )
            );
        }

        List<User> scopedCouriers = userRepository.findActiveByAnyRoleAndWarehouseId(
                Set.of(Role.COURIER),
                warehouseId
        );
        for (User user : scopedCouriers) {
            if (!notifiedUserIds.add(user.getId())) {
                continue;
            }
            notificationService.notifyUser(
                    user.getId(),
                    "PAYMENT_PENDING_CASH_VALIDATION_FOR_WAREHOUSE",
                    "Pago en caja pendiente",
                    "Hay un pago en caja pendiente de validacion para la reserva " + reservation.getQrCode() + ".",
                    Map.of(
                            "reservationId", reservationId,
                            "paymentIntentId", paymentIntentId,
                            "warehouseId", warehouseId,
                            "warehouseName", warehouseName,
                            "route", "/courier/services"
                    )
            );
        }

        List<User> admins = userRepository.findActiveByAnyRole(Set.of(Role.ADMIN));
        for (User user : admins) {
            if (!notifiedUserIds.add(user.getId())) {
                continue;
            }
            notificationService.notifyUser(
                    user.getId(),
                    "PAYMENT_PENDING_CASH_VALIDATION_FOR_WAREHOUSE",
                    "Pago en caja pendiente",
                    "Hay un pago en caja pendiente de validacion para la reserva " + reservation.getQrCode() + ".",
                    Map.of(
                            "reservationId", reservationId,
                            "paymentIntentId", paymentIntentId,
                            "warehouseId", warehouseId,
                            "warehouseName", warehouseName,
                            "route", "/admin/cash-payments"
                    )
            );
        }
    }

    private PaymentIntentResponse confirmCulqiCharge(
            PaymentAttempt attempt,
            PaymentMethod method,
            ConfirmPaymentRequest request,
            AuthUserPrincipal principal
    ) {
        String email = firstNonBlank(
                request.customerEmail(),
                attempt.getReservation().getUser().getEmail(),
                "cliente@inkavoy.pe"
        );
        if (!StringUtils.hasText(request.sourceTokenId())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PAYMENT_SOURCE_TOKEN_REQUIRED",
                    "Debe enviar sourceTokenId para pago con tarjeta o yape."
            );
        }

        String[] antifraudNames = splitName(attempt.getReservation().getUser().getFullName());
        CulqiGatewayClient.AntifraudDetails antifraud = new CulqiGatewayClient.AntifraudDetails(
                firstNonBlank(request.customerFirstName(), antifraudNames[0]),
                firstNonBlank(request.customerLastName(), antifraudNames[1]),
                firstNonBlank(request.customerPhone(), attempt.getReservation().getUser().getPhone()),
                request.customerDocument(),
                null,
                null,
                "PE"
        );

        CulqiGatewayClient.CulqiChargeResult result = culqiGatewayClient.createCharge(
                new CulqiGatewayClient.CulqiChargeRequest(
                        toCents(attempt.getAmount()),
                        currencyCode,
                        email,
                        request.sourceTokenId(),
                        "Reserva TravelBox #" + attempt.getReservation().getId(),
                        metadata(attempt, method, principal),
                        antifraud
                )
        );

        String reference = firstNonBlank(result.providerPaymentId(), normalizeRef(attempt, request.providerReference(), method.label()));
        attempt.registerGatewayOutcome(result.providerStatus(), result.message());
        if (result.approved()) {
            attempt.confirm(reference);
            reservationService.markPaymentConfirmed(attempt.getReservation().getId(), method.label());
            return toIntentResponse(
                    attempt,
                    "CULQI",
                    method.label(),
                    "DIRECT_CHARGE",
                    firstNonBlank(result.message(), "Pago confirmado."),
                    null
            );
        }

        if (result.requires3ds()) {
            attempt.registerProviderReference(reference);
            attempt.registerGatewayOutcome("REQUIRES_3DS_AUTH", firstNonBlank(result.message(), "Pago pendiente de autenticacion 3DS."));
            Map<String, Object> nextAction = new LinkedHashMap<>();
            nextAction.put("type", "AUTHENTICATE_3DS");
            nextAction.put("provider", "CULQI");
            nextAction.put("paymentIntentId", attempt.getId());
            nextAction.put("reservationId", attempt.getReservation().getId());
            if (result.actionData() != null && !result.actionData().isEmpty()) {
                nextAction.put("providerPayload", result.actionData());
            }
            return toIntentResponse(
                    attempt,
                    "CULQI",
                    method.label(),
                    "REQUIRES_3DS_AUTH",
                    firstNonBlank(result.message(), "Pago pendiente de autenticacion 3DS."),
                    nextAction
            );
        }

        attempt.fail(reference);
        notificationService.notifyPaymentRejected(
                attempt.getReservation().getUser().getId(),
                attempt.getReservation().getId(),
                attempt.getReservation().getQrCode(),
                firstNonBlank(result.message(), "Pago rechazado por pasarela.")
        );
        return toIntentResponse(
                attempt,
                "CULQI",
                method.label(),
                "DIRECT_CHARGE_REJECTED",
                firstNonBlank(result.message(), "Pago rechazado."),
                null
        );
    }

    private PaymentIntentResponse createCulqiOrder(
            PaymentAttempt attempt,
            PaymentMethod method,
            ConfirmPaymentRequest request,
            AuthUserPrincipal principal
    ) {
        String[] names = splitName(attempt.getReservation().getUser().getFullName());
        String customerFirstName = firstNonBlank(request.customerFirstName(), names[0], "Cliente");
        String customerLastName = firstNonBlank(request.customerLastName(), names[1], "TravelBox");
        String customerEmail = firstNonBlank(request.customerEmail(), attempt.getReservation().getUser().getEmail(), "cliente@inkavoy.pe");
        String customerPhone = firstNonBlank(request.customerPhone(), attempt.getReservation().getUser().getPhone(), "999999999");

        String orderNumber = "TBX-" + attempt.getReservation().getId() + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
        CulqiGatewayClient.CulqiOrderResult result = culqiGatewayClient.createOrder(
                new CulqiGatewayClient.CulqiOrderRequest(
                        toCents(attempt.getAmount()),
                        currencyCode,
                        "Reserva TravelBox #" + attempt.getReservation().getId(),
                        orderNumber,
                        culqiGatewayClient.defaultOrderExpirationEpochSeconds(),
                        customerFirstName,
                        customerLastName,
                        customerEmail,
                        customerPhone,
                        metadata(attempt, method, principal)
                )
        );

        String orderId = firstNonBlank(result.orderId(), normalizeRef(attempt, request.providerReference(), method.label()));
        attempt.registerProviderReference(orderId);
        attempt.registerGatewayOutcome(firstNonBlank(result.providerStatus(), "ORDER_CREATED"), result.message());

        Map<String, Object> nextAction = new LinkedHashMap<>();
        nextAction.put("type", "OPEN_CULQI_CHECKOUT");
        nextAction.put("orderId", orderId);
        nextAction.put("publicKey", result.publicKey());
        nextAction.put("amountInCents", toCents(attempt.getAmount()));
        nextAction.put("currencyCode", currencyCode);
        nextAction.put("reservationId", attempt.getReservation().getId());
        return toIntentResponse(
                attempt,
                "CULQI",
                method.label(),
                "ORDER_CHECKOUT",
                firstNonBlank(result.message(), "Orden creada. Completa el pago en checkout."),
                nextAction
        );
    }

    private PaymentAttempt resolveAttempt(ConfirmPaymentRequest request, AuthUserPrincipal principal) {
        if (request.paymentIntentId() != null) {
            PaymentAttempt attempt = paymentAttemptRepository.findById(request.paymentIntentId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Pago no encontrado."));
            assertPaymentPermission(attempt.getReservation(), principal);
            return attempt;
        }
        if (request.reservationId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_IDENTIFIER_REQUIRED", "Debes enviar paymentIntentId o reservationId.");
        }

        Reservation reservation = reservationService.requireReservation(request.reservationId());
        assertPaymentPermission(reservation, principal);
        if (reservation.getStatus() != ReservationStatus.PENDING_PAYMENT) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_PAYMENT_STATE", "La reserva no esta pendiente de pago.");
        }
        return paymentAttemptRepository
                .findFirstByReservationIdAndStatusOrderByCreatedAtDesc(reservation.getId(), PaymentStatus.PENDING)
                .orElseGet(() -> paymentAttemptRepository.save(PaymentAttempt.pending(reservation, reservation.getTotalPrice())));
    }

    private PaymentAttempt resolveAttemptForWebhook(JsonNode payload, String providerReference) {
        if (StringUtils.hasText(providerReference)) {
            PaymentAttempt byReference = paymentAttemptRepository.findByProviderReference(providerReference).orElse(null);
            if (byReference != null) {
                return byReference;
            }
        }

        Long paymentIntentId = longAt(payload, "data.object.metadata.paymentIntentId");
        if (paymentIntentId == null) {
            paymentIntentId = longAt(payload, "data.object.metadata.payment_intent_id");
        }
        if (paymentIntentId != null) {
            return paymentAttemptRepository.findById(paymentIntentId).orElse(null);
        }

        Long reservationId = longAt(payload, "data.object.metadata.reservationId");
        if (reservationId == null) {
            reservationId = longAt(payload, "data.object.metadata.reservation_id");
        }
        if (reservationId != null) {
            PaymentAttempt pending = paymentAttemptRepository
                    .findFirstByReservationIdAndStatusOrderByCreatedAtDesc(reservationId, PaymentStatus.PENDING)
                    .orElse(null);
            if (pending != null) {
                return pending;
            }
            return paymentAttemptRepository.findFirstByReservationIdOrderByCreatedAtDesc(reservationId).orElse(null);
        }
        return null;
    }

    private PaymentMethod resolveMethod(PaymentAttempt attempt, String requestedPaymentMethod) {
        PaymentMethod method = PaymentMethod.from(requestedPaymentMethod);
        if (method != PaymentMethod.UNKNOWN) {
            return method;
        }

        String ref = attempt.getProviderReference() == null ? "" : attempt.getProviderReference().toLowerCase(Locale.ROOT);
        if (ref.contains("counter")) {
            return PaymentMethod.COUNTER;
        }
        if (ref.contains("cash")) {
            return PaymentMethod.CASH;
        }
        if (ref.contains("wallet")) {
            return PaymentMethod.WALLET;
        }
        if (ref.contains("plin")) {
            return PaymentMethod.PLIN;
        }
        if (ref.contains("yape")) {
            return PaymentMethod.YAPE;
        }
        return PaymentMethod.CARD;
    }

    private PaymentIntentResponse toIntentResponse(
            PaymentAttempt attempt,
            String provider,
            String paymentMethod,
            String paymentFlow,
            String message,
            Map<String, Object> nextAction
    ) {
        return new PaymentIntentResponse(
                attempt.getId(),
                attempt.getReservation().getId(),
                attempt.getAmount(),
                attempt.getStatus(),
                attempt.getProviderReference(),
                attempt.getCreatedAt(),
                provider,
                paymentMethod,
                paymentFlow,
                firstNonBlank(message, attempt.getGatewayMessage()),
                nextAction
        );
    }

    private PaymentHistoryItemResponse toHistory(PaymentAttempt attempt) {
        PaymentMethod method = resolveMethod(attempt, null);
        return new PaymentHistoryItemResponse(
                attempt.getId(),
                attempt.getReservation().getId(),
                attempt.getReservation().getUser().getId(),
                attempt.getReservation().getUser().getEmail(),
                attempt.getAmount(),
                attempt.getStatus(),
                attempt.getReservation().getStatus(),
                method.label(),
                providerLabel(attempt),
                flowLabel(attempt, method),
                attempt.getProviderReference(),
                attempt.getGatewayStatus(),
                attempt.getGatewayMessage(),
                attempt.getCreatedAt()
        );
    }

    private CashPendingPaymentResponse toCashPending(PaymentAttempt attempt) {
        PaymentMethod method = resolveMethod(attempt, null);
        return new CashPendingPaymentResponse(
                attempt.getId(),
                attempt.getReservation().getId(),
                attempt.getReservation().getUser().getId(),
                attempt.getReservation().getUser().getEmail(),
                attempt.getReservation().getUser().getFullName(),
                attempt.getAmount(),
                method.label(),
                attempt.getProviderReference(),
                attempt.getCreatedAt(),
                attempt.getReservation().getStartAt(),
                attempt.getReservation().getEndAt()
        );
    }

    private PaymentAttempt requirePending(Long paymentIntentId) {
        PaymentAttempt attempt = paymentAttemptRepository.findById(paymentIntentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Pago no encontrado."));
        if (!attempt.isPending()) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_ALREADY_PROCESSED", "El pago ya fue procesado.");
        }
        return attempt;
    }

    private String providerLabel(PaymentAttempt attempt) {
        String reference = attempt.getProviderReference() == null ? "" : attempt.getProviderReference().toLowerCase(Locale.ROOT);
        if (reference.startsWith("offline-")) {
            return "OFFLINE";
        }
        if (reference.startsWith("mock-")) {
            return "MOCK";
        }
        if ("culqi".equals(paymentProvider)) {
            return "CULQI";
        }
        return paymentProvider.toUpperCase(Locale.ROOT);
    }

    private String flowLabel(PaymentAttempt attempt, PaymentMethod method) {
        if (StringUtils.hasText(attempt.getGatewayStatus())) {
            return attempt.getGatewayStatus();
        }
        if (attempt.getStatus() == PaymentStatus.PENDING && (method == PaymentMethod.COUNTER || method == PaymentMethod.CASH)) {
            return "WAITING_OFFLINE_VALIDATION";
        }
        if (attempt.getStatus() == PaymentStatus.PENDING && method.isCheckoutOrderFlow()) {
            return "ORDER_CHECKOUT";
        }
        if (attempt.getStatus() == PaymentStatus.REFUNDED) {
            return "REFUND_EXECUTED";
        }
        if (attempt.getStatus() == PaymentStatus.CONFIRMED) {
            return "DIRECT_CONFIRMATION";
        }
        if (attempt.getStatus() == PaymentStatus.FAILED) {
            return "DECLINED";
        }
        return "UNKNOWN";
    }

    private String normalizeRef(PaymentAttempt attempt, String requestedReference, String method) {
        if (StringUtils.hasText(requestedReference)) {
            return requestedReference.trim();
        }
        String methodCode = method == null ? "GENERIC" : method.trim().toUpperCase(Locale.ROOT);
        if ("counter".equalsIgnoreCase(method) || "cash".equalsIgnoreCase(method)) {
            return "OFFLINE-" + methodCode + "-" + attempt.getId();
        }
        if ("culqi".equals(paymentProvider)) {
            return "CULQI-" + methodCode + "-" + attempt.getId();
        }
        return "MOCK-" + methodCode + "-" + attempt.getId();
    }

    private String defaultReason(String reason, String fallback) {
        return StringUtils.hasText(reason) ? reason.trim() : fallback;
    }

    private int clampSize(int requestedSize) {
        if (requestedSize <= 0) {
            return 20;
        }
        return Math.min(requestedSize, MAX_PAGE_SIZE);
    }

    private void assertPaymentPermission(Reservation reservation, AuthUserPrincipal principal) {
        if (warehouseAccessService.isAdmin(principal)) {
            return;
        }
        if (warehouseAccessService.isOperatorOrCitySupervisor(principal)) {
            if (warehouseAccessService.canAccessWarehouse(principal, reservation.getWarehouse().getId())) {
                return;
            }
            throw new ApiException(HttpStatus.FORBIDDEN, "PAYMENT_FORBIDDEN", "No tienes permiso sobre este pago.");
        }
        if (!reservation.belongsTo(principal.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PAYMENT_FORBIDDEN", "No tienes permiso sobre este pago.");
        }
    }

    private boolean hasPrivilegedRole(AuthUserPrincipal principal) {
        return warehouseAccessService.isAdmin(principal) || warehouseAccessService.isOperatorOrCitySupervisor(principal);
    }

    private void requirePrivileged(AuthUserPrincipal principal) {
        if (!hasPrivilegedRole(principal)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PAYMENT_FORBIDDEN", "Requiere rol operador o admin.");
        }
    }

    private long toCents(BigDecimal amount) {
        if (amount == null) {
            return 0L;
        }
        return amount.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValue();
    }

    private BigDecimal normalizePercent(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal normalized = value.max(BigDecimal.ZERO);
        return normalized.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizeMoney(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal normalized = value.max(BigDecimal.ZERO);
        return normalized.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateRefundFee(
            BigDecimal totalAmount,
            Instant paymentCreatedAt,
            Instant refundRequestedAt
    ) {
        BigDecimal amount = normalizeMoney(totalAmount);
        if (amount.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (paymentCreatedAt == null || refundRequestedAt == null) {
            return normalizeMoney(refundMinimumFee).min(amount);
        }

        long minutesElapsed = Math.max(
                0L,
                Duration.between(paymentCreatedAt, refundRequestedAt).toMinutes()
        );
        if (minutesElapsed <= refundCommissionGraceMinutes) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal percentFee = amount
                .multiply(refundCommissionPercentAfterGrace)
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        BigDecimal fee = percentFee.max(normalizeMoney(refundMinimumFee));
        fee = fee.setScale(2, RoundingMode.HALF_UP);
        if (fee.compareTo(amount) > 0) {
            return amount;
        }
        return fee;
    }

    private String resolveCulqiChargeId(String providerReference) {
        if (!StringUtils.hasText(providerReference)) {
            return null;
        }
        String value = providerReference.trim();
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("chr_") || lower.startsWith("ch_")) {
            return value;
        }
        if (lower.contains("chr_")) {
            int start = lower.indexOf("chr_");
            return safeTokenSlice(value, start);
        }
        if (lower.contains("ch_")) {
            int start = lower.indexOf("ch_");
            return safeTokenSlice(value, start);
        }
        return null;
    }

    private String safeTokenSlice(String value, int startIndex) {
        if (value == null || startIndex < 0 || startIndex >= value.length()) {
            return null;
        }
        int end = value.length();
        for (int i = startIndex; i < value.length(); i++) {
            char current = value.charAt(i);
            if (Character.isWhitespace(current) || current == ',' || current == ';' || current == '"' || current == '\'') {
                end = i;
                break;
            }
        }
        String token = value.substring(startIndex, end).trim();
        return token.isEmpty() ? null : token;
    }

    private String buildRefundSummary(String providerMessage, BigDecimal refundAmount, BigDecimal fee) {
        BigDecimal normalizedRefund = normalizeMoney(refundAmount);
        BigDecimal normalizedFee = normalizeMoney(fee);
        return (firstNonBlank(providerMessage, "Reembolso aplicado.")
                + " Monto reembolsado: S/"
                + normalizedRefund
                + ". Comision: S/"
                + normalizedFee
                + ".").trim();
    }

    private Map<String, String> metadata(PaymentAttempt attempt, PaymentMethod method, AuthUserPrincipal principal) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("paymentIntentId", String.valueOf(attempt.getId()));
        metadata.put("reservationId", String.valueOf(attempt.getReservation().getId()));
        metadata.put("userId", String.valueOf(attempt.getReservation().getUser().getId()));
        metadata.put("paymentMethod", method.label());
        metadata.put("requestedBy", String.valueOf(principal.getId()));
        return metadata;
    }

    private String[] splitName(String fullName) {
        if (!StringUtils.hasText(fullName)) {
            return new String[]{"Cliente", "TravelBox"};
        }
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) {
            return new String[]{parts[0], "TravelBox"};
        }
        List<String> firstNames = new ArrayList<>();
        List<String> lastNames = new ArrayList<>();
        for (int i = 0; i < parts.length; i++) {
            if (i < parts.length / 2) {
                firstNames.add(parts[i]);
            } else {
                lastNames.add(parts[i]);
            }
        }
        return new String[]{String.join(" ", firstNames), String.join(" ", lastNames)};
    }

    private boolean webhookApproved(String eventType, String providerStatus, JsonNode payload) {
        String combined = (eventType + " " + providerStatus).toLowerCase(Locale.ROOT);
        if (combined.contains("paid")
                || combined.contains("succeed")
                || combined.contains("approved")
                || combined.contains("captured")
                || combined.contains("successful")) {
            return true;
        }
        String paidFlag = textAt(payload, "data.object.paid");
        return "true".equalsIgnoreCase(paidFlag);
    }

    private boolean webhookRejected(String eventType, String providerStatus, JsonNode payload) {
        String combined = (eventType + " " + providerStatus).toLowerCase(Locale.ROOT);
        if (combined.contains("failed")
                || combined.contains("declined")
                || combined.contains("canceled")
                || combined.contains("cancelled")
                || combined.contains("expired")
                || combined.contains("reject")) {
            return true;
        }
        String paidFlag = textAt(payload, "data.object.paid");
        return "false".equalsIgnoreCase(paidFlag) && combined.contains("charge");
    }

    private String textAt(JsonNode node, String dottedPath) {
        if (node == null || !StringUtils.hasText(dottedPath)) {
            return null;
        }
        JsonNode current = node;
        for (String part : dottedPath.split("\\.")) {
            if (current == null || current.isMissingNode() || current.isNull()) {
                return null;
            }
            current = current.path(part);
        }
        if (current == null || current.isMissingNode() || current.isNull()) {
            return null;
        }
        if (current.isTextual() || current.isNumber() || current.isBoolean()) {
            return current.asText();
        }
        return null;
    }

    private Long longAt(JsonNode node, String dottedPath) {
        String value = textAt(node, dottedPath);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
