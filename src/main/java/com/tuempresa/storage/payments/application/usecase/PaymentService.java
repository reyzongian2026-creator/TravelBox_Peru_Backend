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
import com.tuempresa.storage.payments.infrastructure.out.gateway.IzipayGatewayClient;
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

import com.tuempresa.storage.payments.application.dto.SavedCardResponse;
import com.tuempresa.storage.payments.domain.SavedCard;
import com.tuempresa.storage.payments.infrastructure.out.persistence.SavedCardRepository;

@Service
public class PaymentService {

    private static final int MAX_PAGE_SIZE = 100;

    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentWebhookEventRepository paymentWebhookEventRepository;
    private final SavedCardRepository savedCardRepository;
    private final ReservationService reservationService;
    private final IzipayGatewayClient izipayGatewayClient;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final WarehouseAccessService warehouseAccessService;
    private final UserRepository userRepository;
    private final WebhookEventInserter webhookEventInserter;
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
            SavedCardRepository savedCardRepository,
            ReservationService reservationService,
            IzipayGatewayClient izipayGatewayClient,
            NotificationService notificationService,
            ObjectMapper objectMapper,
            WarehouseAccessService warehouseAccessService,
            UserRepository userRepository,
            WebhookEventInserter webhookEventInserter,
            @Value("${app.payments.provider:izipay}") String paymentProvider,
            @Value("${app.payments.force-cash-only:false}") boolean forceCashOnly,
            @Value("${app.payments.allow-mock-confirmation:true}") boolean allowMockConfirmation,
            @Value("${app.payments.currency:PEN}") String currencyCode,
            @Value("${app.payments.refunds.commission-grace-minutes:60}") int refundCommissionGraceMinutes,
            @Value("${app.payments.refunds.commission-percent-after-grace:4.50}") BigDecimal refundCommissionPercentAfterGrace,
            @Value("${app.payments.refunds.minimum-fee:0.00}") BigDecimal refundMinimumFee
    ) {
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.paymentWebhookEventRepository = paymentWebhookEventRepository;
        this.savedCardRepository = savedCardRepository;
        this.reservationService = reservationService;
        this.izipayGatewayClient = izipayGatewayClient;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
        this.warehouseAccessService = warehouseAccessService;
        this.userRepository = userRepository;
        this.webhookEventInserter = webhookEventInserter;
        this.paymentProvider = paymentProvider == null ? "izipay" : paymentProvider.trim().toLowerCase(Locale.ROOT);
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

        if (!"izipay".equals(paymentProvider)) {
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

        if (method.isDigitalOnline()) {
            return openIzipayCheckout(attempt, method, request, principal);
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_METHOD_UNSUPPORTED", "Metodo no soportado.");
    }

    @Transactional(readOnly = true)
    public List<SavedCardResponse> listSavedCards(AuthUserPrincipal principal) {
        return savedCardRepository.findByUserIdAndActiveTrueOrderByLastUsedAtDesc(principal.getId())
                .stream()
                .map(card -> new SavedCardResponse(
                        card.getId(),
                        card.getCardAlias(),
                        card.getCardBrand(),
                        card.getLastFourDigits(),
                        card.getExpirationMonth(),
                        card.getExpirationYear(),
                        card.getCreatedAt(),
                        card.getLastUsedAt()
                ))
                .toList();
    }

    @Transactional
    public PaymentIntentResponse payWithSavedCard(Long reservationId, Long savedCardId, AuthUserPrincipal principal) {
        Reservation reservation = reservationService.requireReservation(reservationId);
        assertPaymentPermission(reservation, principal);
        
        SavedCard card = savedCardRepository.findById(savedCardId)
                .filter(c -> c.getUser().getId().equals(principal.getId()) && c.isActive())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CARD_NOT_FOUND", "Tarjeta no encontrada."));

        PaymentAttempt attempt = paymentAttemptRepository.save(PaymentAttempt.pending(reservation, reservation.getTotalPrice()));

        // Combine epoch-seconds (10 digits) + last 4 digits of attemptId to guarantee uniqueness per Izipay
        String transactionId = String.format("%010d%04d", Instant.now().getEpochSecond(), attempt.getId() % 10000);
        String orderNumber = transactionId.substring(0, 10);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("transactionId", transactionId);
        request.put("orderNumber", orderNumber);
        request.put("amount", formatIzipayAmount(attempt.getAmount()));
        request.put("token", card.getToken());
        request.put("merchantCode", izipayGatewayClient.merchantCode());

        try {
            JsonNode response = izipayGatewayClient.payWithToken(request);
            String code = textAt(response, "code");
            String message = textAt(response, "message");

            if ("00".equals(code) || "0".equals(code)) {
                attempt.confirm(transactionId);
                attempt.registerGatewayOutcome("APPROVED_ONE_CLICK", "Pago One-Click procesado exitosamente.");
                reservationService.markPaymentConfirmed(reservation.getId(), PaymentMethod.SAVED_CARD.label());
                card.setLastUsedAt(Instant.now());
                savedCardRepository.save(card);
                return toIntentResponse(attempt, "IZIPAY", PaymentMethod.SAVED_CARD.label(), "ONE_CLICK", "Pago exitoso.", null);
            } else {
                attempt.fail(transactionId);
                attempt.registerGatewayOutcome("REJECTED_ONE_CLICK", firstNonBlank(message, "Pago One-Click rechazado."));
                return toIntentResponse(attempt, "IZIPAY", PaymentMethod.SAVED_CARD.label(), "ONE_CLICK_REJECTED", message, null);
            }
        } catch (Exception ex) {
            attempt.fail(transactionId);
            attempt.registerGatewayOutcome("ERROR_ONE_CLICK", ex.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_ONE_CLICK_FAILED", "Error en pago One-Click: " + ex.getMessage());
        }
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

    @Transactional
    public PaymentIntentResponse syncStatus(Long paymentIntentId, AuthUserPrincipal principal) {
        PaymentAttempt attempt = paymentAttemptRepository.findById(paymentIntentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Pago no encontrado."));
        
        requirePrivileged(principal);

        if (!attempt.isPending()) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_NOT_PENDING", "Solo se pueden sincronizar pagos pendientes.");
        }

        String providerRef = attempt.getProviderReference();
        if ("izipay".equals(paymentProvider) && StringUtils.hasText(providerRef) && !providerRef.startsWith("OFFLINE")) {
            try {
                // Asumimos que la referencia guardada (transactionId) sirve como orderNumber o se puede consultar
                JsonNode statusResponse = izipayGatewayClient.checkOrderStatus(providerRef);
                String code = textAt(statusResponse, "code");
                String stateMessage = textAt(statusResponse, "message");
                
                if ("00".equals(code) || "0".equals(code)) {
                    attempt.confirm(providerRef);
                    attempt.registerGatewayOutcome("APPROVED_BY_SYNC", "Pago confirmado mediante sincronizacion manual.");
                    reservationService.markPaymentConfirmed(attempt.getReservation().getId(), resolveMethod(attempt, null).label());
                } else if (StringUtils.hasText(code) && code.startsWith("REJECT")) {
                    attempt.fail(providerRef);
                    attempt.registerGatewayOutcome("REJECTED_BY_SYNC", firstNonBlank(stateMessage, "Pago rechazado verificado manualmente."));
                }
            } catch (Exception ex) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "SYNC_FAILED", "No se pudo sincronizar con Izipay: " + ex.getMessage());
            }
        }

        return toIntentResponse(
                attempt,
                providerLabel(attempt),
                resolveMethod(attempt, null).label(),
                flowLabel(attempt, resolveMethod(attempt, null)),
                attempt.getGatewayMessage(),
                null
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
        String providerMessage = "Reembolso aplicado en modo interno.";
        String flow = "REFUND_EXECUTED_INTERNAL";

        // Si el proveedor es Izipay, ejecutamos el reembolso real a traves de su API
        if ("izipay".equals(paymentProvider) && StringUtils.hasText(providerReference)) {
            try {
                JsonNode refundResponse = izipayGatewayClient.refund(
                        providerReference,
                        formatIzipayAmount(refundAmount),
                        normalizedReason
                );
                // Verificar que Izipay confirmo el reembolso (code "00" o "0")
                String refundCode = textAt(refundResponse, "code");
                if (refundCode != null && !"00".equals(refundCode) && !"0".equals(refundCode)) {
                    String errorMsg = firstNonBlank(
                            textAt(refundResponse, "message"),
                            textAt(refundResponse, "answer.errorMessage"),
                            "Reembolso rechazado por Izipay (codigo: " + refundCode + ")."
                    );
                    throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_REFUND_PROVIDER_FAILED", errorMsg);
                }
                providerMessage = firstNonBlank(
                        textAt(refundResponse, "message"),
                        textAt(refundResponse, "response.message"),
                        "Reembolso procesado exitosamente por Izipay."
                );
                flow = "REFUND_EXECUTED_IZIPAY";
            } catch (ApiException ex) {
                throw ex;
            } catch (Exception ex) {
                // Si falla el reembolso real, lanzamos error para no marcarlo como reembolsado en BD
                throw new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        "PAYMENT_REFUND_PROVIDER_FAILED",
                        "No se pudo procesar el reembolso en Izipay: " + ex.getMessage()
                );
            }
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
    public PaymentWebhookResponse processIzipayWebhook(String rawPayload, String signature) {
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

        String provider = "izipay";
        String eventType = firstNonBlank(
                textAt(payload, "code"),
                textAt(payload, "response.order.0.stateMessage"),
                textAt(payload, "message"),
                "unknown"
        );
        String payloadHttp = firstNonBlank(textAt(payload, "payloadHttp"), safePayload);
        String eventId = "sha256:" + izipayGatewayClient.sha256Hex(payloadHttp);

        String providerReference = firstNonBlank(
                textAt(payload, "transactionId"),
                textAt(payload, "response.transactionId"),
                customFieldValue(payload, "field1")
        );

        String providerStatus = firstNonBlank(
                textAt(payload, "code"),
                textAt(payload, "response.order.0.stateMessage"),
                textAt(payload, "message"),
                eventType
        );

        String providerMessage = firstNonBlank(
                textAt(payload, "messageUser"),
                textAt(payload, "message"),
                textAt(payload, "response.order.0.stateMessage"),
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

        // insertOrGetExisting runs in REQUIRES_NEW: if a concurrent thread already holds
        // the same (provider, eventId) row, the inner tx rolls back cleanly and we get
        // back the existing record — the outer tx session is never poisoned.
        PaymentWebhookEvent event = webhookEventInserter.insertOrGetExisting(
                provider, eventId, eventType, providerReference, safePayload);

        if (event.getProcessingStatus() != com.tuempresa.storage.payments.domain.PaymentWebhookProcessingStatus.RECEIVED) {
            // Race condition: another thread already processed this event
            return new PaymentWebhookResponse(
                    event.isProcessed(),
                    true,
                    event.getEventId(),
                    event.getEventType(),
                    event.getProviderReference(),
                    event.getProcessingStatus().name(),
                    "Evento webhook ya procesado.",
                    event.getPaymentAttemptId(),
                    event.getReservationId()
            );
        }

        String effectiveSignature = firstNonBlank(signature, textAt(payload, "signature"));
        if (!izipayGatewayClient.validateWebhookSignature(payloadHttp, effectiveSignature)) {
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
            String resolvedMethod = resolveWebhookMethod(payload, attempt).label();

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
                reservationService.markPaymentConfirmed(reservationId, resolvedMethod);
                
                // Extraer y guardar token de tarjeta para One-Click
                String cardToken = textAt(payload, "response.token");
                if (StringUtils.hasText(cardToken)) {
                    saveCardToken(attempt.getReservation().getUser(), cardToken, payload);
                }
                
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

    private PaymentIntentResponse openIzipayCheckout(
            PaymentAttempt attempt,
            PaymentMethod method,
            ConfirmPaymentRequest request,
            AuthUserPrincipal principal
    ) {
        if (!izipayGatewayClient.isConfigured()) {
            throw new ApiException(
                    HttpStatus.PRECONDITION_REQUIRED,
                    "PAYMENT_PROVIDER_NOT_CONFIGURED",
                    "Falta configurar merchantCode/publicKey de Izipay."
            );
        }

        String[] customerNames = splitName(attempt.getReservation().getUser().getFullName());
        // Combine epoch-seconds (10 digits) + last 4 digits of attemptId to guarantee uniqueness per Izipay
        String transactionId = String.format("%010d%04d", Instant.now().getEpochSecond(), attempt.getId() % 10000);
        String orderNumber = transactionId.substring(0, 10);
        String merchantBuyerId = "TBX-" + attempt.getReservation().getId();

        IzipayGatewayClient.IzipaySessionResult session = izipayGatewayClient.createSession(
                new IzipayGatewayClient.IzipaySessionRequest(
                        transactionId,
                        orderNumber,
                        formatIzipayAmount(attempt.getAmount()),
                        izipayGatewayClient.requestSource()
                )
        );

        attempt.registerProviderReference(transactionId);
        attempt.registerGatewayOutcome("WAITING_IZIPAY_CHECKOUT", "Checkout Izipay listo para completar el pago.");

        Map<String, Object> order = new LinkedHashMap<>();
        order.put("orderNumber", orderNumber);
        order.put("currency", currencyCode);
        order.put("amount", formatIzipayAmount(attempt.getAmount()));
        order.put("payMethod", preferredIzipayPayMethod(method));
        order.put("processType", izipayGatewayClient.processType());
        order.put("merchantBuyerId", merchantBuyerId);
        order.put("dateTimeTransaction", transactionId);

        Map<String, Object> billing = buildIzipayPersonData(
                firstNonBlank(request.customerFirstName(), customerNames[0], "Cliente"),
                firstNonBlank(request.customerLastName(), customerNames[1], "TravelBox"),
                firstNonBlank(request.customerEmail(), attempt.getReservation().getUser().getEmail(), "cliente@inkavoy.pe"),
                firstNonBlank(request.customerPhone(), attempt.getReservation().getUser().getPhone(), "999999999"),
                request.customerDocument()
        );

        Map<String, Object> checkoutConfig = new LinkedHashMap<>();
        checkoutConfig.put("transactionId", transactionId);
        checkoutConfig.put("action", "pay");
        checkoutConfig.put("merchantCode", session.merchantCode());
        checkoutConfig.put("order", order);
        checkoutConfig.put("billing", billing);
        checkoutConfig.put("shipping", new LinkedHashMap<>(billing));
        checkoutConfig.put("appearance", izipayAppearance(method));
        checkoutConfig.put("customFields", izipayCustomFields(attempt, method, principal));
        
        // Habilitar registro de tarjeta para pagos One-Click
        checkoutConfig.put("tokenize", true);
        
        // Habilitar billeteras digitales (Yape, Plin) si el método seleccionado lo permite
        if (method == PaymentMethod.YAPE || method == PaymentMethod.PLIN || method == PaymentMethod.WALLET) {
            checkoutConfig.put("showWallet", true);
        }

        Map<String, Object> nextAction = new LinkedHashMap<>();
        nextAction.put("type", "OPEN_IZIPAY_CHECKOUT");
        nextAction.put("provider", "IZIPAY");
        nextAction.put("paymentIntentId", attempt.getId());
        nextAction.put("reservationId", attempt.getReservation().getId());
        nextAction.put("providerReference", transactionId);
        nextAction.put("authorization", session.token());
        nextAction.put("keyRSA", session.keyRsa());
        nextAction.put("scriptUrl", session.checkoutScriptUrl());
        nextAction.put("checkoutConfig", checkoutConfig);
        nextAction.put("providerPayload", Map.of(
                "transactionId", transactionId,
                "orderNumber", orderNumber,
                "merchantBuyerId", merchantBuyerId,
                "requires3DS", true // Forzar manejo de 3DS en el frontend
        ));

        return toIntentResponse(
                attempt,
                "IZIPAY",
                method.label(),
                "OPEN_IZIPAY_CHECKOUT",
                "Continua el pago en Izipay para confirmar tu reserva.",
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

        Long paymentIntentId = longAt(payload, "paymentIntentId");
        if (paymentIntentId == null) {
            paymentIntentId = parseLong(customFieldValue(payload, "field1"));
        }
        if (paymentIntentId != null) {
            return paymentAttemptRepository.findById(paymentIntentId).orElse(null);
        }

        Long reservationId = longAt(payload, "reservationId");
        if (reservationId == null) {
            reservationId = parseLong(customFieldValue(payload, "field2"));
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
        if ("izipay".equals(paymentProvider)) {
            return "IZIPAY";
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
        if (attempt.getStatus() == PaymentStatus.PENDING && method.isDigitalOnline()) {
            return "OPEN_IZIPAY_CHECKOUT";
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
        if ("izipay".equals(paymentProvider)) {
            return "IZIPAY-" + methodCode + "-" + attempt.getId();
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
        String code = firstNonBlank(textAt(payload, "code"), providerStatus);
        if ("00".equals(code) || "0".equals(code)) {
            return true;
        }
        String combined = (eventType + " " + providerStatus).toLowerCase(Locale.ROOT);
        if (combined.contains("autorizado")
                || combined.contains("paid")
                || combined.contains("succeed")
                || combined.contains("approved")
                || combined.contains("captured")
                || combined.contains("successful")) {
            return true;
        }
        String stateMessage = textAt(payload, "response.order.0.stateMessage");
        return stateMessage != null && stateMessage.toLowerCase(Locale.ROOT).contains("autorizado");
    }

    private boolean webhookRejected(String eventType, String providerStatus, JsonNode payload) {
        // NOTE: do NOT reject purely based on code != "00" — intermediate Izipay codes (e.g. 40=review,
        // 97=3DS-pending) are non-zero but not final. The keyword check below is the reliable signal.
        String combined = (eventType + " " + providerStatus).toLowerCase(Locale.ROOT);
        if (combined.contains("rechazado")
                || combined.contains("failed")
                || combined.contains("declined")
                || combined.contains("canceled")
                || combined.contains("cancelled")
                || combined.contains("expired")
                || combined.contains("reject")) {
            return true;
        }
        String stateMessage = textAt(payload, "response.order.0.stateMessage");
        return stateMessage != null && stateMessage.toLowerCase(Locale.ROOT).contains("rechaz");
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
            if (current.isArray()) {
                try {
                    current = current.path(Integer.parseInt(part));
                    continue;
                } catch (NumberFormatException ignored) {
                    return null;
                }
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
        return parseLong(textAt(node, dottedPath));
    }

    private Long parseLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String formatIzipayAmount(BigDecimal amount) {
        return normalizeMoney(amount).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String preferredIzipayPayMethod(PaymentMethod method) {
        return switch (method) {
            case CARD -> "CARD";
            case YAPE -> "YAPE_CODE";
            case PLIN, WALLET -> "QR";
            default -> "ALL";
        };
    }

    private Map<String, Object> buildIzipayPersonData(
            String firstName,
            String lastName,
            String email,
            String phone,
            String document
    ) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("firstName", firstNonBlank(firstName, "Cliente"));
        data.put("lastName", firstNonBlank(lastName, "TravelBox"));
        data.put("email", firstNonBlank(email, "cliente@inkavoy.pe"));
        data.put("phoneNumber", firstNonBlank(phone, "999999999"));
        data.put("street", "Av. Jose Larco 123");
        data.put("city", "Lima");
        data.put("state", "Lima");
        data.put("country", "PE");
        data.put("postalCode", "15074");
        if (StringUtils.hasText(document)) {
            data.put("documentType", inferDocumentType(document));
            data.put("document", document.trim());
        }
        return data;
    }

    private Map<String, Object> izipayAppearance(PaymentMethod selectedMethod) {
        Map<String, Object> visibility = new LinkedHashMap<>();
        visibility.put("hideTestCards", true);
        visibility.put("hideResultScreen", true);

        Map<String, Object> customize = new LinkedHashMap<>();
        customize.put("visibility", visibility);
        customize.put("elements", izipayPaymentMethodOrder(selectedMethod));

        Map<String, Object> appearance = new LinkedHashMap<>();
        appearance.put("customize", customize);
        return appearance;
    }

    private List<Map<String, Object>> izipayPaymentMethodOrder(PaymentMethod selectedMethod) {
        List<Map<String, Object>> elements = new ArrayList<>();
        List<String> preferredOrder = switch (selectedMethod) {
            case CARD -> List.of("CARD", "YAPE_CODE", "QR");
            case YAPE -> List.of("YAPE_CODE", "CARD", "QR");
            case PLIN, WALLET -> List.of("QR", "CARD", "YAPE_CODE");
            default -> List.of("CARD", "YAPE_CODE", "QR");
        };
        int order = 1;
        for (String payMethod : preferredOrder) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("paymentMethod", payMethod);
            item.put("order", order++);
            elements.add(item);
        }
        return elements;
    }

    private List<Map<String, Object>> izipayCustomFields(
            PaymentAttempt attempt,
            PaymentMethod method,
            AuthUserPrincipal principal
    ) {
        List<Map<String, Object>> fields = new ArrayList<>();
        fields.add(customField("field1", String.valueOf(attempt.getId())));
        fields.add(customField("field2", String.valueOf(attempt.getReservation().getId())));
        fields.add(customField("field3", method.label()));
        fields.add(customField("field4", String.valueOf(principal.getId())));
        return fields;
    }

    private Map<String, Object> customField(String name, String value) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("name", name);
        field.put("value", value);
        return field;
    }

    private String customFieldValue(JsonNode payload, String fieldName) {
        JsonNode customFields = payload.path("response").path("customFields");
        if (!customFields.isArray()) {
            return null;
        }
        for (JsonNode field : customFields) {
            String name = textAt(field, "name");
            if (fieldName.equalsIgnoreCase(firstNonBlank(name, ""))) {
                return textAt(field, "value");
            }
        }
        return null;
    }

    private PaymentMethod resolveWebhookMethod(JsonNode payload, PaymentAttempt attempt) {
        PaymentMethod fromCustomField = PaymentMethod.from(customFieldValue(payload, "field3"));
        if (fromCustomField != PaymentMethod.UNKNOWN) {
            return fromCustomField;
        }
        PaymentMethod fromPayload = PaymentMethod.from(firstNonBlank(
                textAt(payload, "response.payMethod"),
                textAt(payload, "response.order.0.payMethodAuthorization")
        ));
        if (fromPayload != PaymentMethod.UNKNOWN) {
            return fromPayload;
        }
        return resolveMethod(attempt, null);
    }

    private void saveCardToken(User user, String token, JsonNode payload) {
        if (user == null || !StringUtils.hasText(token)) {
            return;
        }
        
        // Evitar duplicados
        if (savedCardRepository.findByUserIdAndTokenAndActiveTrue(user.getId(), token).isPresent()) {
            return;
        }

        String brand = textAt(payload, "response.brand");
        String pan = textAt(payload, "response.pan");
        String lastFour = "";
        if (StringUtils.hasText(pan) && pan.length() >= 4) {
            lastFour = pan.substring(pan.length() - 4);
        }
        
        String alias = (brand != null ? brand : "Tarjeta") + " **** " + lastFour;
        
        SavedCard card = SavedCard.of(
            user,
            token,
            alias,
            brand,
            lastFour,
            textAt(payload, "response.expirationMonth"),
            textAt(payload, "response.expirationYear")
        );
        
        savedCardRepository.save(card);
    }

    private String inferDocumentType(String document) {
        String normalized = document == null ? "" : document.trim();
        if (normalized.length() == 8) {
            return "DNI";
        }
        return "CE";
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
