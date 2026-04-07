package com.tuempresa.storage.payments.application.usecase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuempresa.storage.notifications.application.usecase.NotificationService;
import com.tuempresa.storage.payments.application.dto.CancellationPreviewResponse;
import com.tuempresa.storage.payments.application.dto.CashPendingPaymentResponse;
import com.tuempresa.storage.payments.application.dto.ConfirmPaymentRequest;
import com.tuempresa.storage.payments.application.dto.CreatePaymentIntentRequest;
import com.tuempresa.storage.payments.application.dto.PaymentHistoryItemResponse;
import com.tuempresa.storage.payments.application.dto.PaymentIntentResponse;
import com.tuempresa.storage.payments.application.dto.PaymentStatusResponse;
import com.tuempresa.storage.payments.application.dto.PaymentWebhookResponse;
import com.tuempresa.storage.payments.application.dto.ValidateCheckoutResultRequest;
import com.tuempresa.storage.payments.domain.BookingType;
import com.tuempresa.storage.payments.domain.CancellationPolicyType;
import com.tuempresa.storage.payments.domain.CancellationRecord;
import com.tuempresa.storage.payments.domain.PaymentAttempt;
import com.tuempresa.storage.payments.domain.PaymentMethod;
import com.tuempresa.storage.payments.domain.PaymentStatus;
import com.tuempresa.storage.payments.domain.PaymentWebhookEvent;
import com.tuempresa.storage.payments.infrastructure.out.gateway.IzipayGatewayClient;
import com.tuempresa.storage.payments.infrastructure.out.persistence.CancellationRecordRepository;
import com.tuempresa.storage.payments.infrastructure.out.persistence.PaymentAttemptRepository;
import com.tuempresa.storage.payments.infrastructure.out.persistence.PaymentWebhookEventRepository;
import com.tuempresa.storage.reservations.application.dto.CancelReservationRequest;
import com.tuempresa.storage.reservations.application.usecase.ReservationService;
import com.tuempresa.storage.reservations.domain.Reservation;
import com.tuempresa.storage.reservations.domain.ReservationStatus;
import com.tuempresa.storage.shared.domain.exception.ApiException;
import com.tuempresa.storage.shared.application.PlatformSettingService;
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
import com.tuempresa.storage.payments.application.dto.PromoCodeResponse;
import com.tuempresa.storage.payments.domain.PromoCode;
import com.tuempresa.storage.payments.domain.SavedCard;
import com.tuempresa.storage.payments.domain.YapeReconciliationAudit;
import com.tuempresa.storage.payments.infrastructure.out.persistence.PromoCodeRepository;
import com.tuempresa.storage.payments.infrastructure.out.persistence.SavedCardRepository;
import com.tuempresa.storage.payments.infrastructure.out.persistence.YapeReconciliationAuditRepository;

@Service
public class PaymentService {

    private static final int MAX_PAGE_SIZE = 100;

    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentWebhookEventRepository paymentWebhookEventRepository;
    private final SavedCardRepository savedCardRepository;
    private final CancellationRecordRepository cancellationRecordRepository;
    private final ReservationService reservationService;
    private final IzipayGatewayClient izipayGatewayClient;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final WarehouseAccessService warehouseAccessService;
    private final UserRepository userRepository;
    private final WebhookEventInserter webhookEventInserter;
    private final RefundPolicyEngine refundPolicyEngine;
    private final PromoCodeRepository promoCodeRepository;
    private final YapeReconciliationAuditRepository reconciliationAuditRepository;
    private final PlatformSettingService platformSettingService;
    private final String paymentProvider;
    private final boolean forceCashOnly;
    private final boolean allowMockConfirmation;
    private final String currencyCode;
    private final int refundCommissionGraceMinutes;
    private final BigDecimal refundCommissionPercentAfterGrace;
    private final BigDecimal refundMinimumFee;
    private final String yapePhone;
    private final String yapeName;
    private final String yapeQrUrl;
    private final String plinPhone;
    private final String plinName;
    private final String plinQrUrl;
    private final String qrPhone;
    private final String qrName;
    private final String qrQrUrl;

    public PaymentService(
            PaymentAttemptRepository paymentAttemptRepository,
            PaymentWebhookEventRepository paymentWebhookEventRepository,
            SavedCardRepository savedCardRepository,
            CancellationRecordRepository cancellationRecordRepository,
            ReservationService reservationService,
            IzipayGatewayClient izipayGatewayClient,
            NotificationService notificationService,
            ObjectMapper objectMapper,
            WarehouseAccessService warehouseAccessService,
            UserRepository userRepository,
            WebhookEventInserter webhookEventInserter,
            RefundPolicyEngine refundPolicyEngine,
            PromoCodeRepository promoCodeRepository,
            YapeReconciliationAuditRepository reconciliationAuditRepository,
            PlatformSettingService platformSettingService,
            @Value("${app.payments.provider:izipay}") String paymentProvider,
            @Value("${app.payments.force-cash-only:false}") boolean forceCashOnly,
            @Value("${app.payments.allow-mock-confirmation:true}") boolean allowMockConfirmation,
            @Value("${app.payments.currency:PEN}") String currencyCode,
            @Value("${app.payments.refunds.commission-grace-minutes:60}") int refundCommissionGraceMinutes,
            @Value("${app.payments.refunds.commission-percent-after-grace:4.50}") BigDecimal refundCommissionPercentAfterGrace,
            @Value("${app.payments.refunds.minimum-fee:0.00}") BigDecimal refundMinimumFee,
            @Value("${app.payments.manual-transfer.yape-phone:}") String yapePhone,
            @Value("${app.payments.manual-transfer.yape-name:InkaVoy Peru}") String yapeName,
            @Value("${app.payments.manual-transfer.yape-qr-url:}") String yapeQrUrl,
            @Value("${app.payments.manual-transfer.plin-phone:}") String plinPhone,
            @Value("${app.payments.manual-transfer.plin-name:InkaVoy Peru}") String plinName,
            @Value("${app.payments.manual-transfer.plin-qr-url:}") String plinQrUrl,
            @Value("${app.payments.manual-transfer.qr-phone:}") String qrPhone,
            @Value("${app.payments.manual-transfer.qr-name:InkaVoy Peru}") String qrName,
            @Value("${app.payments.manual-transfer.qr-qr-url:}") String qrQrUrl) {
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.paymentWebhookEventRepository = paymentWebhookEventRepository;
        this.savedCardRepository = savedCardRepository;
        this.cancellationRecordRepository = cancellationRecordRepository;
        this.reservationService = reservationService;
        this.izipayGatewayClient = izipayGatewayClient;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
        this.warehouseAccessService = warehouseAccessService;
        this.userRepository = userRepository;
        this.webhookEventInserter = webhookEventInserter;
        this.refundPolicyEngine = refundPolicyEngine;
        this.promoCodeRepository = promoCodeRepository;
        this.reconciliationAuditRepository = reconciliationAuditRepository;
        this.platformSettingService = platformSettingService;
        this.paymentProvider = paymentProvider == null ? "izipay" : paymentProvider.trim().toLowerCase(Locale.ROOT);
        this.forceCashOnly = forceCashOnly;
        this.allowMockConfirmation = allowMockConfirmation;
        this.currencyCode = currencyCode == null ? "PEN" : currencyCode.trim().toUpperCase(Locale.ROOT);
        this.refundCommissionGraceMinutes = Math.max(0, refundCommissionGraceMinutes);
        this.refundCommissionPercentAfterGrace = normalizePercent(refundCommissionPercentAfterGrace);
        this.refundMinimumFee = normalizeMoney(refundMinimumFee);
        this.yapePhone = yapePhone;
        this.yapeName = yapeName;
        this.yapeQrUrl = yapeQrUrl;
        this.plinPhone = plinPhone;
        this.plinName = plinName;
        this.plinQrUrl = plinQrUrl;
        this.qrPhone = qrPhone;
        this.qrName = qrName;
        this.qrQrUrl = qrQrUrl;
    }

    @Transactional
    public PaymentIntentResponse createIntent(CreatePaymentIntentRequest request, AuthUserPrincipal principal) {
        Reservation reservation = reservationService.requireReservation(request.reservationId());
        assertPaymentPermission(reservation, principal);
        if (reservation.getStatus() != ReservationStatus.PENDING_PAYMENT
                && reservation.getStatus() != ReservationStatus.EXPIRED) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_PAYMENT_STATE",
                    "La reserva no esta pendiente de pago.");
        }
        // Verificar si ya existe un pago confirmado para esta reserva (evita doble
        // cobro)
        paymentAttemptRepository
                .findFirstByReservationIdAndStatusForUpdate(reservation.getId(), PaymentStatus.CONFIRMED)
                .ifPresent(confirmed -> {
                    throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_ALREADY_CONFIRMED",
                            "Esta reserva ya tiene un pago confirmado.");
                });

        BigDecimal originalAmount = reservation.getTotalPrice();
        BigDecimal discountAmount = BigDecimal.ZERO;
        PromoCode promoCode = null;

        if (StringUtils.hasText(request.promoCode())) {
            promoCode = promoCodeRepository.findByCodeIgnoreCase(request.promoCode().trim())
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "PROMO_CODE_INVALID",
                            "El codigo promocional no existe."));
            if (!promoCode.isUsable(Instant.now())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "PROMO_CODE_EXPIRED",
                        "El codigo promocional ha expirado o no esta disponible.");
            }
            if (!promoCode.meetsMinimum(originalAmount)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "PROMO_CODE_MIN_NOT_MET",
                        "El monto minimo para este codigo es S/ " + promoCode.getMinOrderAmount());
            }
            discountAmount = promoCode.calculateDiscount(originalAmount);
        }

        BigDecimal finalAmount = originalAmount.subtract(discountAmount);
        if (finalAmount.compareTo(BigDecimal.ZERO) < 0) {
            finalAmount = BigDecimal.ZERO;
        }

        // Wallet credit deduction
        BigDecimal walletUsed = BigDecimal.ZERO;
        User walletUser = null;
        if (request.walletAmount() != null && request.walletAmount().compareTo(BigDecimal.ZERO) > 0) {
            walletUser = userRepository.findById(principal.getId())
                    .orElseThrow(
                            () -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Usuario no encontrado."));
            BigDecimal requested = request.walletAmount();
            if (requested.compareTo(walletUser.getWalletBalance()) > 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INSUFFICIENT_WALLET",
                        "Saldo insuficiente en la billetera.");
            }
            walletUsed = requested.min(finalAmount);
            finalAmount = finalAmount.subtract(walletUsed);
            walletUser.deductWalletBalance(walletUsed);
        }

        final PromoCode appliedPromo = promoCode;
        final BigDecimal appliedDiscount = discountAmount;
        final BigDecimal chargeAmount = finalAmount;

        PaymentAttempt attempt = paymentAttemptRepository
                .findFirstByReservationIdAndStatusForUpdate(reservation.getId(), PaymentStatus.PENDING)
                .orElseGet(() -> {
                    PaymentAttempt a = PaymentAttempt.pending(reservation, chargeAmount);
                    if (appliedPromo != null) {
                        a.setPromoCode(appliedPromo);
                        a.setDiscountAmount(appliedDiscount);
                        appliedPromo.incrementUses();
                    }
                    return paymentAttemptRepository.save(a);
                });
        attempt.registerGatewayOutcome("INTENT_CREATED", "Intento de pago creado.");
        return toIntentResponse(attempt, providerLabel(attempt), "unknown", "INTENT_CREATED", "Intento de pago creado.",
                null);
    }

    @Transactional(readOnly = true)
    public PromoCodeResponse validatePromoCode(String code, BigDecimal orderAmount) {
        if (!StringUtils.hasText(code)) {
            return PromoCodeResponse.invalid("Ingrese un codigo promocional.");
        }
        return promoCodeRepository.findByCodeIgnoreCase(code.trim())
                .map(promo -> {
                    if (!promo.isUsable(Instant.now())) {
                        return PromoCodeResponse.invalid("El codigo promocional ha expirado o no esta disponible.");
                    }
                    if (!promo.meetsMinimum(orderAmount)) {
                        return PromoCodeResponse.invalid(
                                "El monto minimo para este codigo es S/ " + promo.getMinOrderAmount());
                    }
                    BigDecimal discount = promo.calculateDiscount(orderAmount);
                    return PromoCodeResponse.valid(
                            promo.getCode(),
                            promo.getDescription(),
                            promo.getDiscountType().name(),
                            promo.getDiscountValue(),
                            discount);
                })
                .orElse(PromoCodeResponse.invalid("El codigo promocional no existe."));
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
        if (method.isManualTransfer()) {
            return processManualTransfer(attempt, method);
        }
        if (!approved) {
            String ref = normalizeRef(attempt, request.providerReference(), method.label());
            attempt.fail(ref);
            attempt.registerGatewayOutcome("DECLINED", "Pago rechazado por solicitud del cliente.");
            List<User> operators = userRepository.findActiveByAnyRoleAndWarehouseId(
                    Set.of(Role.OPERATOR, Role.CITY_SUPERVISOR), attempt.getReservation().getWarehouse().getId());
            if (operators != null) {
                for (User user : operators) {
                    notificationService.emitSilentRealtimeEvent(user.getId(), "PAYMENT_SYNC",
                            java.util.Map.of("reservationId", attempt.getReservation().getId()));
                }
            }
            notificationService.notifyPaymentRejected(attempt.getReservation().getUser().getId(),
                    attempt.getReservation().getId(), attempt.getReservation().getQrCode(), "Rechazado por cliente");
            return toIntentResponse(attempt, providerLabel(attempt), method.label(), "DECLINED", "Pago rechazado.",
                    null);
        }

        if (!"izipay".equals(paymentProvider)) {
            if (!allowMockConfirmation) {
                throw new ApiException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "PAYMENT_PROVIDER_UNAVAILABLE",
                        "Proveedor de pagos no habilitado para confirmacion mock en este entorno.");
            }
            String ref = normalizeRef(attempt, request.providerReference(), method.label());
            attempt.confirm(ref);
            attempt.registerGatewayOutcome("MOCK_CONFIRMED", "Pago confirmado en modo mock.");
            reservationService.markPaymentConfirmed(attempt.getReservation().getId(), method.label());
            return toIntentResponse(attempt, "MOCK", method.label(), "DIRECT_CONFIRMATION",
                    "Pago confirmado en modo mock.", null);
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
                        card.getLastUsedAt()))
                .toList();
    }

    @Transactional
    public PaymentIntentResponse payWithSavedCard(Long reservationId, Long savedCardId, AuthUserPrincipal principal) {
        Reservation reservation = reservationService.requireReservation(reservationId);
        assertPaymentPermission(reservation, principal);

        if (reservation.getStatus() != ReservationStatus.PENDING_PAYMENT
                && reservation.getStatus() != ReservationStatus.EXPIRED) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_PAYMENT_STATE",
                    "La reserva no esta pendiente de pago.");
        }
        paymentAttemptRepository
                .findFirstByReservationIdAndStatusOrderByCreatedAtDesc(reservation.getId(), PaymentStatus.CONFIRMED)
                .ifPresent(confirmed -> {
                    throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_ALREADY_CONFIRMED",
                            "Esta reserva ya tiene un pago confirmado.");
                });

        SavedCard card = savedCardRepository.findById(savedCardId)
                .filter(c -> c.getUser().getId().equals(principal.getId()) && c.isActive())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CARD_NOT_FOUND", "Tarjeta no encontrada."));

        PaymentAttempt attempt = paymentAttemptRepository
                .save(PaymentAttempt.pending(reservation, reservation.getTotalPrice()));

        // Combine epoch-seconds (10 digits) + last 4 digits of attemptId to guarantee
        // uniqueness per Izipay
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
                return toIntentResponse(attempt, "IZIPAY", PaymentMethod.SAVED_CARD.label(), "ONE_CLICK",
                        "Pago exitoso.", null);
            } else {
                attempt.fail(transactionId);
                attempt.registerGatewayOutcome("REJECTED_ONE_CLICK",
                        firstNonBlank(message, "Pago One-Click rechazado."));
                return toIntentResponse(attempt, "IZIPAY", PaymentMethod.SAVED_CARD.label(), "ONE_CLICK_REJECTED",
                        message, null);
            }
        } catch (Exception ex) {
            attempt.fail(transactionId);
            attempt.registerGatewayOutcome("ERROR_ONE_CLICK", ex.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_ONE_CLICK_FAILED",
                    "Error en pago One-Click: " + ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public PaymentStatusResponse status(Long paymentIntentId, Long reservationId, AuthUserPrincipal principal) {
        PaymentAttempt attempt;
        if (paymentIntentId != null) {
            attempt = paymentAttemptRepository.findById(paymentIntentId)
                    .orElseThrow(
                            () -> new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Pago no encontrado."));
        } else if (reservationId != null) {
            attempt = paymentAttemptRepository.findFirstByReservationIdOrderByCreatedAtDesc(reservationId)
                    .orElseThrow(
                            () -> new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Pago no encontrado."));
        } else {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_IDENTIFIER_REQUIRED",
                    "Debes enviar paymentIntentId o reservationId.");
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
                attempt.getReservation().getExpiresAt());
    }

    @Transactional
    public PaymentIntentResponse syncStatus(Long paymentIntentId, AuthUserPrincipal principal) {
        PaymentAttempt attempt = paymentAttemptRepository.findById(paymentIntentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Pago no encontrado."));

        requirePrivileged(principal);

        if (!attempt.isPending()) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_NOT_PENDING",
                    "Solo se pueden sincronizar pagos pendientes.");
        }

        String providerRef = attempt.getProviderReference();
        if ("izipay".equals(paymentProvider) && StringUtils.hasText(providerRef)
                && !providerRef.startsWith("OFFLINE")) {
            try {
                // Asumimos que la referencia guardada (transactionId) sirve como orderNumber o
                // se puede consultar
                JsonNode statusResponse = izipayGatewayClient.checkOrderStatus(providerRef);
                String code = textAt(statusResponse, "code");
                String stateMessage = textAt(statusResponse, "message");

                if ("00".equals(code) || "0".equals(code)) {
                    attempt.confirm(providerRef);
                    attempt.registerGatewayOutcome("APPROVED_BY_SYNC",
                            "Pago confirmado mediante sincronizacion manual.");
                    reservationService.markPaymentConfirmed(attempt.getReservation().getId(),
                            resolveMethod(attempt, null).label());
                } else if (StringUtils.hasText(code) && code.startsWith("REJECT")) {
                    attempt.fail(providerRef);
                    attempt.registerGatewayOutcome("REJECTED_BY_SYNC",
                            firstNonBlank(stateMessage, "Pago rechazado verificado manualmente."));
                }
            } catch (Exception ex) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "SYNC_FAILED",
                        "No se pudo sincronizar con Izipay: " + ex.getMessage());
            }
        }

        return toIntentResponse(
                attempt,
                providerLabel(attempt),
                resolveMethod(attempt, null).label(),
                flowLabel(attempt, resolveMethod(attempt, null)),
                attempt.getGatewayMessage(),
                null);
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
            PaymentStatus status) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), clampSize(size),
                Sort.by(Sort.Direction.DESC, "createdAt"));
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
                            pageable))
                    .map(this::toHistory);
        }
        return PagedResponse.from(mapped);
    }

    @Transactional(readOnly = true)
    public PagedResponse<CashPendingPaymentResponse> listCashPending(AuthUserPrincipal principal, int page, int size) {
        requirePrivileged(principal);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), clampSize(size),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<CashPendingPaymentResponse> mapped;
        if (warehouseAccessService.isAdmin(principal)) {
            mapped = paymentAttemptRepository.findOfflineCashPending(PaymentStatus.PENDING, pageable)
                    .map(this::toCashPending);
        } else {
            java.util.Set<Long> warehouseIds = warehouseAccessService.assignedWarehouseIds(principal);
            if (warehouseIds.isEmpty()) {
                return new PagedResponse<>(List.of(), pageable.getPageNumber(), pageable.getPageSize(), 0, 0, false,
                        false);
            }
            mapped = paymentAttemptRepository
                    .findOfflineCashPendingByWarehouses(PaymentStatus.PENDING, warehouseIds, pageable)
                    .map(this::toCashPending);
        }
        return PagedResponse.from(mapped);
    }

    @Transactional
    public PaymentIntentResponse approveCashPayment(Long paymentIntentId, String providerReference, String reason,
            AuthUserPrincipal principal) {
        requirePrivileged(principal);
        PaymentAttempt attempt = requirePending(paymentIntentId);
        assertPaymentPermission(attempt.getReservation(), principal);
        PaymentMethod method = resolveMethod(attempt, null);
        if (!(method == PaymentMethod.COUNTER || method == PaymentMethod.CASH || method.isManualTransfer())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_NOT_CASH",
                    "Este pago no es de caja ni transferencia manual.");
        }

        // Daily cash approval limit per operator: max 20 per day (UTC)
        Instant dayStart = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.DAYS);
        long todayApprovals = paymentAttemptRepository.countCashApprovalsBy(
                principal.getId().toString(), dayStart);
        if (todayApprovals >= 20) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "CASH_DAILY_LIMIT_EXCEEDED",
                    "Se ha alcanzado el limite diario de 20 aprobaciones en efectivo por operador.");
        }

        String ref = normalizeRef(attempt, providerReference, method.label());
        attempt.confirm(ref);
        attempt.registerGatewayOutcome("OFFLINE_CONFIRMED_BY_OPERATOR",
                defaultReason(reason, "Pago en caja confirmado por operador."));
        reservationService.markPaymentConfirmed(attempt.getReservation().getId(), method.label());
        return toIntentResponse(attempt, "OFFLINE", method.label(), "OFFLINE_CONFIRMED_BY_OPERATOR",
                defaultReason(reason, "Pago confirmado."), null);
    }

    @Transactional
    public PaymentIntentResponse rejectCashPayment(Long paymentIntentId, String providerReference, String reason,
            AuthUserPrincipal principal) {
        requirePrivileged(principal);
        PaymentAttempt attempt = requirePending(paymentIntentId);
        assertPaymentPermission(attempt.getReservation(), principal);
        PaymentMethod method = resolveMethod(attempt, null);
        if (!(method == PaymentMethod.COUNTER || method == PaymentMethod.CASH || method.isManualTransfer())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_NOT_CASH",
                    "Este pago no es de caja ni transferencia manual.");
        }
        String ref = normalizeRef(attempt, providerReference, method.label());
        String message = defaultReason(reason, "Pago en caja rechazado por operador.");
        attempt.fail(ref);
        attempt.registerGatewayOutcome("OFFLINE_REJECTED_BY_OPERATOR", message);
        List<User> rejectOperators = userRepository.findActiveByAnyRoleAndWarehouseId(
                Set.of(Role.OPERATOR, Role.CITY_SUPERVISOR), attempt.getReservation().getWarehouse().getId());
        if (rejectOperators != null) {
            for (User user : rejectOperators) {
                notificationService.emitSilentRealtimeEvent(user.getId(), "PAYMENT_SYNC",
                        java.util.Map.of("reservationId", attempt.getReservation().getId()));
            }
        }
        notificationService.notifyPaymentRejected(attempt.getReservation().getUser().getId(),
                attempt.getReservation().getId(), attempt.getReservation().getQrCode(), message);
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
                    "Solo se pueden reembolsar pagos confirmados.");
        }

        PaymentMethod method = resolveMethod(attempt, null);

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

        if (method.isManualTransfer()) {
            // Transferencias manuales (Yape/Plin/QR): el admin confirma que ya devolvio
            // el dinero manualmente. No hay API de reembolso automatico.
            providerMessage = "Reembolso manual confirmado por operador. "
                    + "Metodo original: " + method.label().toUpperCase(Locale.ROOT) + ". "
                    + "El operador confirma haber devuelto S/" + refundAmount.toPlainString()
                    + " al cliente.";
            flow = "REFUND_EXECUTED_MANUAL_TRANSFER";
        } else if ("izipay".equals(paymentProvider) && StringUtils.hasText(providerReference)
                && method.isDigitalOnline()) {
            // Si el proveedor es Izipay, ejecutamos el reembolso real a traves de su API
            try {
                JsonNode refundResponse = izipayGatewayClient.refund(
                        providerReference,
                        formatIzipayAmount(refundAmount),
                        normalizedReason);
                // Verificar que Izipay confirmo el reembolso (code "00" o "0")
                String refundCode = textAt(refundResponse, "code");
                if (refundCode != null && !"00".equals(refundCode) && !"0".equals(refundCode)) {
                    String errorMsg = firstNonBlank(
                            textAt(refundResponse, "message"),
                            textAt(refundResponse, "answer.errorMessage"),
                            "Reembolso rechazado por Izipay (codigo: " + refundCode + ").");
                    throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_REFUND_PROVIDER_FAILED", errorMsg);
                }
                providerMessage = firstNonBlank(
                        textAt(refundResponse, "message"),
                        textAt(refundResponse, "response.message"),
                        "Reembolso procesado exitosamente por Izipay.");
                flow = "REFUND_EXECUTED_IZIPAY";
            } catch (ApiException ex) {
                throw ex;
            } catch (Exception ex) {
                // Si falla el reembolso real, lanzamos error para no marcarlo como reembolsado
                // en BD
                throw new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        "PAYMENT_REFUND_PROVIDER_FAILED",
                        "No se pudo procesar el reembolso en Izipay: " + ex.getMessage());
            }
        }

        String summaryMessage = buildRefundSummary(providerMessage, refundAmount, fee);
        attempt.refund(providerReference, refundAmount, fee, normalizedReason);
        attempt.registerGatewayOutcome("REFUNDED", summaryMessage);
        // Persist refund state immediately — if Izipay already processed it,
        // we must record it in DB before any cascading operation can fail.
        paymentAttemptRepository.save(attempt);

        String cancelReason = "Reserva cancelada por reembolso. " + summaryMessage;
        reservationService.cancel(
                attempt.getReservation().getId(),
                new CancelReservationRequest(cancelReason),
                principal);

        notificationService.notifyUser(
                attempt.getReservation().getUser().getId(),
                "PAYMENT_REFUNDED",
                "Reembolso aplicado",
                summaryMessage,
                Map.of(
                        "reservationId", attempt.getReservation().getId(),
                        "paymentIntentId", attempt.getId(),
                        "refundAmount", refundAmount,
                        "refundFee", fee));

        return toIntentResponse(
                attempt,
                providerLabel(attempt),
                method.label(),
                flow,
                summaryMessage,
                null);
    }

    // â”€â”€ Cancellation Preview & Confirm
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Transactional(readOnly = true)
    public CancellationPreviewResponse cancellationPreview(Long reservationId, AuthUserPrincipal principal) {
        Reservation reservation = reservationService.requireReservation(reservationId);
        assertPaymentPermission(reservation, principal);

        PaymentAttempt attempt = paymentAttemptRepository
                .findFirstByReservationIdAndStatusOrderByCreatedAtDesc(reservationId, PaymentStatus.CONFIRMED)
                .orElse(null);

        // Si no hay pago confirmado, se puede cancelar sin reembolso
        if (attempt == null) {
            return new CancellationPreviewResponse(
                    reservationId, null,
                    BookingType.IMMEDIATE, CancellationPolicyType.FULL_REFUND,
                    "Sin pago digital registrado. Cancelacion directa sin reembolso.",
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, false, false, true, null);
        }

        PaymentMethod method = resolveMethod(attempt, null);
        boolean requiresRefund = method.isDigitalOnline() || method.isManualTransfer();

        if (!requiresRefund) {
            return new CancellationPreviewResponse(
                    reservationId, attempt.getId(),
                    BookingType.IMMEDIATE, CancellationPolicyType.FULL_REFUND,
                    "Pago en efectivo/mostrador. Cancelacion sin reembolso digital.",
                    attempt.getAmount(), BigDecimal.ZERO, BigDecimal.ZERO, attempt.getAmount(),
                    BigDecimal.ZERO, false, false, true, null);
        }

        // Verificar si ya tiene un reembolso pendiente o exitoso
        if (attempt.isRefunded() || attempt.isRefundPending()) {
            return new CancellationPreviewResponse(
                    reservationId, attempt.getId(),
                    BookingType.IMMEDIATE, CancellationPolicyType.NO_REFUND,
                    "Este pago ya fue reembolsado o tiene un reembolso en proceso.",
                    attempt.getAmount(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, false, false, false,
                    "REFUND_ALREADY_PROCESSED");
        }

        // Daily refund limit preview: max 2 per user per day (UTC)
        Instant dayStart = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.DAYS);
        Instant dayEnd = dayStart.plus(1, java.time.temporal.ChronoUnit.DAYS);
        long todayRefunds = cancellationRecordRepository.countByActorUserIdAndRequestedAtBetweenAndStatusIn(
                principal.getId(), dayStart, dayEnd,
                List.of(CancellationRecord.CancellationStatus.PENDING,
                        CancellationRecord.CancellationStatus.REFUND_EXECUTED,
                        CancellationRecord.CancellationStatus.COMPLETED));
        if (todayRefunds >= 2) {
            return new CancellationPreviewResponse(
                    reservationId, attempt.getId(),
                    BookingType.IMMEDIATE, CancellationPolicyType.NO_REFUND,
                    "Has alcanzado el limite de 2 reembolsos por dia. Intenta nuevamente manana.",
                    attempt.getAmount(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, false, false, false,
                    "DAILY_REFUND_LIMIT_REACHED");
        }

        Instant confirmedAt = attempt.getConfirmedAt() != null ? attempt.getConfirmedAt() : attempt.getCreatedAt();
        RefundPolicyEngine.RefundCalculation calc = refundPolicyEngine.calculate(
                attempt.getAmount(), confirmedAt, reservation.getStartAt(), Instant.now(), BigDecimal.ZERO);

        return new CancellationPreviewResponse(
                reservationId, attempt.getId(),
                calc.bookingType(), calc.policyType(),
                buildPolicyDescription(calc),
                calc.grossPaidAmount(), calc.cancellationPenaltyAmount(), calc.refundAmountToCustomer(),
                calc.retainedAmountByBusiness(),
                calc.providerFeeAmount(), calc.providerFeeAmount().signum() > 0,
                true, calc.policyType() != CancellationPolicyType.NO_REFUND, null);
    }

    @Transactional
    public PaymentIntentResponse cancellationConfirm(Long reservationId, String reason, AuthUserPrincipal principal) {
        Reservation reservation = reservationService.requireReservation(reservationId);
        assertPaymentPermission(reservation, principal);

        // Verificar que la reserva esta en un estado cancelable
        if (reservation.getStatus() == ReservationStatus.CANCELLED
                || reservation.getStatus() == ReservationStatus.COMPLETED) {
            throw new ApiException(HttpStatus.CONFLICT, "RESERVATION_NOT_CANCELLABLE",
                    "La reserva ya esta en estado terminal: " + reservation.getStatus());
        }

        PaymentAttempt attempt = paymentAttemptRepository
                .findFirstByReservationIdAndStatusOrderByCreatedAtDesc(reservationId, PaymentStatus.CONFIRMED)
                .orElse(null);

        // Si no hay pago digital confirmado, cancelar directamente
        if (attempt == null) {
            String cancelReason = defaultReason(reason, "Cancelacion sin pago digital.");
            reservationService.cancel(reservationId, new CancelReservationRequest(cancelReason), principal);
            return null; // caller debe re-fetch status
        }

        PaymentMethod method = resolveMethod(attempt, null);
        if (!method.isDigitalOnline()) {
            String cancelReason = defaultReason(reason, "Cancelacion de pago no-digital.");
            reservationService.cancel(reservationId, new CancelReservationRequest(cancelReason), principal);
            return toIntentResponse(attempt, providerLabel(attempt), method.label(),
                    "CANCELLED_NO_REFUND_REQUIRED", cancelReason, null);
        }

        if (attempt.isRefunded() || attempt.isRefundPending()) {
            throw new ApiException(HttpStatus.CONFLICT, "REFUND_ALREADY_PROCESSED",
                    "Ya existe un reembolso procesado o en progreso para este pago.");
        }

        // Idempotency: check for existing cancellation record
        boolean alreadyExists = cancellationRecordRepository.existsByReservationIdAndStatusIn(
                reservationId,
                List.of(CancellationRecord.CancellationStatus.PENDING,
                        CancellationRecord.CancellationStatus.REFUND_EXECUTED,
                        CancellationRecord.CancellationStatus.COMPLETED));
        if (alreadyExists) {
            throw new ApiException(HttpStatus.CONFLICT, "CANCELLATION_ALREADY_IN_PROGRESS",
                    "Ya existe una cancelacion en progreso o completada para esta reserva.");
        }

        // Daily refund limit: max 2 per user per day (UTC)
        Instant dayStart = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.DAYS);
        Instant dayEnd = dayStart.plus(1, java.time.temporal.ChronoUnit.DAYS);
        long todayRefunds = cancellationRecordRepository.countByActorUserIdAndRequestedAtBetweenAndStatusIn(
                principal.getId(), dayStart, dayEnd,
                List.of(CancellationRecord.CancellationStatus.PENDING,
                        CancellationRecord.CancellationStatus.REFUND_EXECUTED,
                        CancellationRecord.CancellationStatus.COMPLETED));
        if (todayRefunds >= 2) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "DAILY_REFUND_LIMIT_REACHED",
                    "Has alcanzado el limite de 2 reembolsos por dia. Intenta nuevamente manana.");
        }

        Instant confirmedAt = attempt.getConfirmedAt() != null ? attempt.getConfirmedAt() : attempt.getCreatedAt();
        RefundPolicyEngine.RefundCalculation calc = refundPolicyEngine.calculate(
                attempt.getAmount(), confirmedAt, reservation.getStartAt(), Instant.now(), BigDecimal.ZERO);

        String normalizedReason = defaultReason(reason, "Cancelacion con reembolso segun politica.");
        String previousReservationStatus = reservation.getStatus().name();
        String previousPaymentStatus = attempt.getStatus().name();

        // Create audit record
        CancellationRecord record = CancellationRecord.create(
                reservationId, attempt.getId(),
                calc.bookingType(), calc.policyType(), calc.policyWindow(),
                calc.grossPaidAmount(), calc.cancellationPenaltyAmount(), calc.refundAmountToCustomer(),
                calc.retainedAmountByBusiness(),
                calc.providerFeeAmount(), calc.providerFeeRefundable(),
                BigDecimal.ZERO, calc.netBusinessLoss(),
                normalizedReason, principal.getId(), String.join(",", principal.roleNames()),
                reservation.getStartAt(), confirmedAt,
                previousReservationStatus, previousPaymentStatus);

        // Mark payment as refund pending
        attempt.markRefundPending();
        paymentAttemptRepository.save(attempt);

        // Execute refund
        String providerReference = attempt.getProviderReference();
        String providerMessage;
        String flow;

        if (calc.policyType() == CancellationPolicyType.NO_REFUND) {
            // No refund, just cancel
            record.markNoRefund();
            cancellationRecordRepository.save(record);

            attempt.refundWithPolicy(providerReference,
                    BigDecimal.ZERO, calc.cancellationPenaltyAmount(), calc.providerFeeAmount(),
                    normalizedReason, calc.bookingType(), calc.policyType());
            paymentAttemptRepository.save(attempt);

            reservationService.cancel(reservationId, new CancelReservationRequest(
                    "Cancelacion sin reembolso. Politica: " + calc.policyType()), principal);

            notificationService.notifyUser(
                    reservation.getUser().getId(), "RESERVATION_CANCELLED",
                    "Reserva cancelada",
                    "Tu reserva fue cancelada. Politica aplicada: sin reembolso.",
                    Map.of("reservationId", reservationId));

            return toIntentResponse(attempt, providerLabel(attempt), method.label(),
                    "CANCELLED_NO_REFUND", "Cancelado sin reembolso.", null);
        }

        BigDecimal refundAmount = calc.refundAmountToCustomer();
        if (refundAmount.signum() <= 0) {
            refundAmount = BigDecimal.ZERO;
        }

        try {
            if ("izipay".equals(paymentProvider) && StringUtils.hasText(providerReference)) {
                JsonNode refundResponse = izipayGatewayClient.refund(
                        providerReference, formatIzipayAmount(refundAmount), normalizedReason);
                String refundCode = textAt(refundResponse, "code");
                if (refundCode != null && !"00".equals(refundCode) && !"0".equals(refundCode)) {
                    String errorMsg = firstNonBlank(
                            textAt(refundResponse, "message"),
                            textAt(refundResponse, "answer.errorMessage"),
                            "Reembolso rechazado por Izipay (codigo: " + refundCode + ").");
                    throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_REFUND_PROVIDER_FAILED", errorMsg);
                }
                providerMessage = firstNonBlank(
                        textAt(refundResponse, "message"),
                        textAt(refundResponse, "response.message"),
                        "Reembolso procesado exitosamente por Izipay.");
                flow = "CANCELLATION_REFUND_IZIPAY";
                record.markRefundExecuted(providerReference, providerMessage);
            } else {
                providerMessage = "Reembolso aplicado internamente.";
                flow = "CANCELLATION_REFUND_INTERNAL";
                record.markRefundExecuted(providerReference, providerMessage);
            }

            record.markCompleted();
            cancellationRecordRepository.save(record);

            attempt.refundWithPolicy(providerReference,
                    refundAmount, calc.cancellationPenaltyAmount(), calc.providerFeeAmount(),
                    normalizedReason, calc.bookingType(), calc.policyType());
            paymentAttemptRepository.save(attempt);

            String summaryMessage = buildRefundSummary(providerMessage, refundAmount, calc.cancellationPenaltyAmount());
            reservationService.cancel(reservationId, new CancelReservationRequest(
                    "Cancelacion con reembolso. " + summaryMessage), principal);

            notificationService.notifyUser(
                    reservation.getUser().getId(), "PAYMENT_REFUNDED",
                    "Reembolso aplicado",
                    summaryMessage,
                    Map.of("reservationId", reservationId,
                            "paymentIntentId", attempt.getId(),
                            "refundAmount", refundAmount,
                            "refundFee", calc.cancellationPenaltyAmount()));

            return toIntentResponse(attempt, providerLabel(attempt), method.label(),
                    flow, summaryMessage, null);

        } catch (ApiException ex) {
            record.markFailed(ex.getMessage());
            cancellationRecordRepository.save(record);
            attempt.markRefundFailed(ex.getMessage());
            paymentAttemptRepository.save(attempt);
            throw ex;
        } catch (Exception ex) {
            String errorMsg = "Error inesperado al procesar reembolso: " + ex.getMessage();
            record.markFailed(errorMsg);
            cancellationRecordRepository.save(record);
            attempt.markRefundFailed(errorMsg);
            paymentAttemptRepository.save(attempt);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_REFUND_PROVIDER_FAILED", errorMsg);
        }
    }

    private String buildPolicyDescription(RefundPolicyEngine.RefundCalculation calc) {
        return switch (calc.policyType()) {
            case FULL_REFUND -> "Reembolso completo de S/" + calc.refundAmountToCustomer() + ".";
            case PARTIAL_REFUND -> "Reembolso parcial de S/" + calc.refundAmountToCustomer()
                    + " (comision de cancelacion: S/" + calc.cancellationPenaltyAmount() + ").";
            case NO_REFUND -> "Sin reembolso. La reserva esta fuera del periodo de cancelacion gratuita.";
            case MANUAL_REVIEW -> "Requiere revision manual por el equipo de soporte.";
        };
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
                    null);
        }

        String provider = "izipay";
        // V4 Krypton IPN kr-answer: orderStatus, transactions.0.status/uuid
        String eventType;
        String eventId;
        try {
            eventType = firstNonBlank(
                    textAt(payload, "code"),
                    textAt(payload, "orderStatus"),
                    textAt(payload, "transactions.0.status"),
                    textAt(payload, "response.order.0.stateMessage"),
                    textAt(payload, "message"),
                    "unknown");
            String payloadHttp = firstNonBlank(textAt(payload, "payloadHttp"), safePayload);
            eventId = "sha256:" + izipayGatewayClient.sha256Hex(payloadHttp);

            String providerReference = firstNonBlank(
                    textAt(payload, "transactionId"),
                    textAt(payload, "transactions.0.uuid"),
                    textAt(payload, "response.transactionId"),
                    customFieldValue(payload, "field1"));

            String providerStatus = firstNonBlank(
                    textAt(payload, "code"),
                    textAt(payload, "orderStatus"),
                    textAt(payload, "transactions.0.status"),
                    textAt(payload, "transactions.0.detailedStatus"),
                    textAt(payload, "response.order.0.stateMessage"),
                    textAt(payload, "message"),
                    eventType);

            String providerMessage = firstNonBlank(
                    textAt(payload, "messageUser"),
                    textAt(payload, "message"),
                    textAt(payload, "transactions.0.detailedStatus"),
                    textAt(payload, "response.order.0.stateMessage"),
                    "Webhook procesado.");

            PaymentWebhookEvent existing = paymentWebhookEventRepository.findByProviderAndEventId(provider, eventId)
                    .orElse(null);
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
                        existing.getReservationId());
            }

            // Validate signature BEFORE persisting event — reject forgeries without DB
            // write
            String effectiveSignature = firstNonBlank(signature, textAt(payload, "signature"));
            if (!izipayGatewayClient.validateWebhookSignature(payloadHttp, effectiveSignature)) {
                return new PaymentWebhookResponse(
                        false,
                        false,
                        eventId,
                        eventType,
                        providerReference,
                        "INVALID_SIGNATURE",
                        "Firma webhook invalida.",
                        null,
                        null);
            }

            // insertOrGetExisting runs in REQUIRES_NEW: if a concurrent thread already
            // holds
            // the same (provider, eventId) row, the inner tx rolls back cleanly and we get
            // back the existing record â€” the outer tx session is never poisoned.
            PaymentWebhookEvent event = webhookEventInserter.insertOrGetExisting(
                    provider, eventId, eventType, providerReference, safePayload);

            if (event
                    .getProcessingStatus() != com.tuempresa.storage.payments.domain.PaymentWebhookProcessingStatus.RECEIVED) {
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
                        event.getReservationId());
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
                            null);
                }

                Long paymentIntentId = attempt.getId();
                Long reservationId = attempt.getReservation().getId();
                String effectiveReference = StringUtils.hasText(providerReference) ? providerReference
                        : attempt.getProviderReference();
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
                            reservationId);
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
                            reservationId);
                }

                if (webhookRejected(eventType, providerStatus, payload)) {
                    attempt.fail(effectiveReference);
                    attempt.registerGatewayOutcome(firstNonBlank(providerStatus, eventType), providerMessage);
                    notificationService.notifyPaymentRejected(
                            attempt.getReservation().getUser().getId(),
                            reservationId,
                            attempt.getReservation().getQrCode(),
                            providerMessage);
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
                            reservationId);
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
                        reservationId);
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
                        null);
            }
        } catch (Exception outerEx) {
            // Catch-all: prevent 500 errors from escaping to the HTTP layer.
            // Possible causes: DB connectivity, event insertion race, hash computation.
            return new PaymentWebhookResponse(
                    false,
                    false,
                    null,
                    null,
                    null,
                    "INTERNAL_ERROR",
                    firstNonBlank(outerEx.getMessage(), "Error interno procesando webhook."),
                    null,
                    null);
        }
    }

    @Transactional
    public PaymentIntentResponse validateCheckoutResult(ValidateCheckoutResultRequest request,
            AuthUserPrincipal principal) {
        // 1. Validate the hash (HMAC-SHA256 of kr-answer with hash key)
        if (!izipayGatewayClient.hasHashKey()) {
            throw new ApiException(HttpStatus.PRECONDITION_REQUIRED, "PAYMENT_HASH_KEY_MISSING",
                    "No se puede validar el pago: falta la clave de hash.");
        }
        String computedHash = izipayGatewayClient.generateWebhookSignature(request.krAnswer());
        if (computedHash == null || !java.security.MessageDigest.isEqual(
                computedHash.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                request.krHash().getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_HASH_INVALID",
                    "La firma del pago no es valida.");
        }

        // 2. Parse the kr-answer JSON
        JsonNode clientAnswer;
        try {
            clientAnswer = objectMapper.readTree(request.krAnswer());
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_ANSWER_INVALID",
                    "La respuesta del pago no es valida.");
        }

        // 3. Find the payment attempt
        String orderStatus = textAt(clientAnswer, "orderStatus");
        String transactionId = firstNonBlank(
                textAt(clientAnswer, "transactions.0.uuid"),
                textAt(clientAnswer, "transactions.0.transactionDetails.cardDetails.legacyTransId"));
        String orderId = textAt(clientAnswer, "orderDetails.orderId");

        PaymentAttempt attempt = null;
        if (request.paymentIntentId() != null) {
            attempt = paymentAttemptRepository.findById(request.paymentIntentId()).orElse(null);
        }
        if (attempt == null && request.reservationId() != null) {
            attempt = paymentAttemptRepository
                    .findFirstByReservationIdAndStatusOrderByCreatedAtDesc(request.reservationId(),
                            PaymentStatus.PENDING)
                    .orElse(null);
        }
        if (attempt == null && StringUtils.hasText(orderId)) {
            attempt = paymentAttemptRepository.findByProviderReference(orderId).orElse(null);
            if (attempt == null) {
                attempt = paymentAttemptRepository
                        .findFirstByProviderReferenceStartingWithOrderByCreatedAtDesc(orderId)
                        .orElse(null);
            }
        }
        if (attempt == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND",
                    "No se encontro el intento de pago.");
        }

        assertPaymentPermission(attempt.getReservation(), principal);

        if (!attempt.isPending()) {
            // Already processed â€” return current state
            return toIntentResponse(attempt, "IZIPAY", resolveMethod(attempt, null).label(),
                    "ALREADY_PROCESSED", "El pago ya fue procesado.", null);
        }

        String effectiveReference = StringUtils.hasText(transactionId)
                ? transactionId
                : StringUtils.hasText(orderId) ? orderId : attempt.getProviderReference();

        // 4. Process based on orderStatus
        if ("PAID".equalsIgnoreCase(orderStatus)) {
            attempt.confirm(effectiveReference);
            attempt.registerGatewayOutcome("APPROVED_CLIENT_VALIDATION",
                    "Pago confirmado por validacion del cliente.");
            String resolvedMethod = resolveMethod(attempt, null).label();
            reservationService.markPaymentConfirmed(attempt.getReservation().getId(), resolvedMethod);

            // Save card token if present
            String cardToken = textAt(clientAnswer, "transactions.0.paymentMethodToken");
            if (StringUtils.hasText(cardToken)) {
                try {
                    saveCardToken(attempt.getReservation().getUser(), cardToken, clientAnswer);
                } catch (Exception ignored) {
                    // Non-critical: don't fail payment because of token save
                }
            }

            return toIntentResponse(attempt, "IZIPAY", resolvedMethod,
                    "CHECKOUT_VALIDATED", "Pago confirmado exitosamente.", null);
        }

        if ("UNPAID".equalsIgnoreCase(orderStatus) || "REFUSED".equalsIgnoreCase(orderStatus)
                || "ERROR".equalsIgnoreCase(orderStatus) || "ABANDONED".equalsIgnoreCase(orderStatus)) {
            attempt.fail(effectiveReference);
            String errorMsg = firstNonBlank(
                    textAt(clientAnswer, "transactions.0.errorMessage"),
                    textAt(clientAnswer, "transactions.0.detailedErrorMessage"),
                    "Pago rechazado: " + orderStatus);
            attempt.registerGatewayOutcome("REJECTED_CLIENT_VALIDATION", errorMsg);
            notificationService.notifyPaymentRejected(
                    attempt.getReservation().getUser().getId(),
                    attempt.getReservation().getId(),
                    attempt.getReservation().getQrCode(),
                    errorMsg);
            return toIntentResponse(attempt, "IZIPAY", resolveMethod(attempt, null).label(),
                    "CHECKOUT_REJECTED", errorMsg, null);
        }

        // Unknown status â€” leave as pending
        return toIntentResponse(attempt, "IZIPAY", resolveMethod(attempt, null).label(),
                "CHECKOUT_PENDING", "El pago esta en proceso. orderStatus=" + orderStatus, null);
    }

    private PaymentIntentResponse processOffline(
            PaymentAttempt attempt,
            PaymentMethod method,
            boolean approved,
            String providerReference,
            AuthUserPrincipal principal) {
        String reference = normalizeRef(attempt, providerReference, method.label());
        if (!approved) {
            attempt.fail(reference);
            attempt.registerGatewayOutcome("OFFLINE_REJECTED", "Pago en caja rechazado.");
            notificationService.notifyPaymentRejected(
                    attempt.getReservation().getUser().getId(),
                    attempt.getReservation().getId(),
                    attempt.getReservation().getQrCode(),
                    "Pago en caja rechazado.");
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
                    null);
        }

        attempt.registerProviderReference(reference);
        attempt.registerGatewayOutcome("WAITING_OFFLINE_VALIDATION",
                "Pago en caja pendiente de validacion por operador.");
        notificationService.notifyPaymentPendingCashValidation(
                attempt.getReservation().getUser().getId(),
                attempt.getReservation().getId(),
                attempt.getReservation().getQrCode());
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
                nextAction);
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
                warehouseId);
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
                            "route", "/operator/cash-payments"));
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
                    "PAYMENT_PENDING_CASH_VALIDATION_FOR_WAREHOUSE",
                    "Pago en caja pendiente",
                    "Hay un pago en caja pendiente de validacion para la reserva " + reservation.getQrCode() + ".",
                    Map.of(
                            "reservationId", reservationId,
                            "paymentIntentId", paymentIntentId,
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
                    "PAYMENT_PENDING_CASH_VALIDATION_FOR_WAREHOUSE",
                    "Pago en caja pendiente",
                    "Hay un pago en caja pendiente de validacion para la reserva " + reservation.getQrCode() + ".",
                    Map.of(
                            "reservationId", reservationId,
                            "paymentIntentId", paymentIntentId,
                            "warehouseId", warehouseId,
                            "warehouseName", warehouseName,
                            "route", "/admin/cash-payments"));
        }
    }

    private PaymentIntentResponse processManualTransfer(PaymentAttempt attempt, PaymentMethod method) {
        // --- Anti-fraud: max 3 pending manual transfers per user ---
        Long userId = attempt.getReservation().getUser().getId();
        long pendingCount = paymentAttemptRepository
                .countPendingManualTransfersByUserId(userId);
        if (pendingCount >= 3) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_PENDING_TRANSFERS",
                    "Tienes demasiadas transferencias pendientes. Espera a que un operador las verifique.");
        }

        String phone;
        String recipientName;
        String qrUrl;
        switch (method) {
            case YAPE -> {
                phone = platformSettingService.getOrDefault("payments.yape.phone", yapePhone);
                recipientName = platformSettingService.getOrDefault("payments.yape.name", yapeName);
                qrUrl = platformSettingService.getOrDefault("payments.yape.qr_url", yapeQrUrl);
            }
            case PLIN -> {
                phone = platformSettingService.getOrDefault("payments.plin.phone", plinPhone);
                recipientName = platformSettingService.getOrDefault("payments.plin.name", plinName);
                qrUrl = platformSettingService.getOrDefault("payments.plin.qr_url", plinQrUrl);
            }
            default -> {
                phone = platformSettingService.getOrDefault("payments.qr.phone", qrPhone);
                recipientName = platformSettingService.getOrDefault("payments.qr.name", qrName);
                qrUrl = platformSettingService.getOrDefault("payments.qr.qr_url", qrQrUrl);
            }
        }

        // Snapshot identity of the payer for reconciliation matching
        var payer = attempt.getReservation().getUser();
        attempt.setExpectedCustomerEmail(payer.getEmail());
        attempt.setExpectedCustomerName(
                ((payer.getFirstName() != null ? payer.getFirstName() : "") + " "
                        + (payer.getLastName() != null ? payer.getLastName() : "")).strip());
        attempt.setExpectedMethod(method.label());
        attempt.setManualTransferRequestedAt(Instant.now());

        String reference = "TRANSFER-" + method.label().toUpperCase(Locale.ROOT) + "-" + attempt.getId();
        attempt.registerProviderReference(reference);
        attempt.registerGatewayOutcome("WAITING_MANUAL_TRANSFER",
                "Pago por " + method.label() + " pendiente de verificacion.");

        notificationService.notifyPaymentPendingCashValidation(
                attempt.getReservation().getUser().getId(),
                attempt.getReservation().getId(),
                attempt.getReservation().getQrCode());
        notifyOperationalCashPending(attempt);

        Map<String, Object> nextAction = new LinkedHashMap<>();
        nextAction.put("type", "SHOW_TRANSFER_QR");
        nextAction.put("method", method.label());
        nextAction.put("phone", phone);
        nextAction.put("recipientName", recipientName);
        nextAction.put("qrUrl", qrUrl);
        nextAction.put("amount", attempt.getAmount().toPlainString());
        nextAction.put("currency", currencyCode);
        nextAction.put("reservationId", attempt.getReservation().getId());
        nextAction.put("paymentIntentId", attempt.getId());
        nextAction.put("instructions", "Transfiere S/ " + attempt.getAmount().toPlainString()
                + " a " + recipientName + " (" + phone + ") por " + method.label().toUpperCase(Locale.ROOT)
                + ". Un operador verificara tu pago.");

        return toIntentResponse(
                attempt,
                "MANUAL_TRANSFER",
                method.label(),
                "WAITING_MANUAL_TRANSFER",
                "Realiza la transferencia y un operador verificara tu pago.",
                nextAction);
    }

    private PaymentIntentResponse openIzipayCheckout(
            PaymentAttempt attempt,
            PaymentMethod method,
            ConfirmPaymentRequest request,
            AuthUserPrincipal principal) {
        if (!izipayGatewayClient.isConfigured()) {
            throw new ApiException(
                    HttpStatus.PRECONDITION_REQUIRED,
                    "PAYMENT_PROVIDER_NOT_CONFIGURED",
                    "Falta configurar merchantCode/publicKey de Izipay.");
        }

        String[] customerNames = splitName(attempt.getReservation().getUser().getFullName());
        // Combine epoch-seconds (10 digits) + last 4 digits of attemptId to guarantee
        // uniqueness per Izipay
        String transactionId = String.format("%010d%04d", Instant.now().getEpochSecond(), attempt.getId() % 10000);
        String orderNumber = transactionId.substring(0, 10);
        String merchantBuyerId = "TBX-" + attempt.getReservation().getId();

        IzipayGatewayClient.IzipaySessionResult session = izipayGatewayClient.createSession(
                new IzipayGatewayClient.IzipaySessionRequest(
                        transactionId,
                        orderNumber,
                        formatIzipayAmount(attempt.getAmount()),
                        izipayGatewayClient.requestSource(),
                        List.of())); // empty = show all methods the merchant has enabled

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
                firstNonBlank(request.customerEmail(), attempt.getReservation().getUser().getEmail(),
                        "cliente@inkavoy.pe"),
                firstNonBlank(request.customerPhone(), attempt.getReservation().getUser().getPhone(), "999999999"),
                request.customerDocument());

        Map<String, Object> checkoutConfig = new LinkedHashMap<>();
        checkoutConfig.put("transactionId", transactionId);
        checkoutConfig.put("action", "pay");
        checkoutConfig.put("merchantCode", session.merchantCode());
        checkoutConfig.put("order", order);
        checkoutConfig.put("billing", billing);
        checkoutConfig.put("shipping", new LinkedHashMap<>(billing));
        checkoutConfig.put("appearance", izipayAppearance(method));
        checkoutConfig.put("customFields", izipayCustomFields(attempt, method, principal));
        checkoutConfig.put("render", Map.of("typeForm", "pop-up"));

        Map<String, Object> nextAction = new LinkedHashMap<>();
        nextAction.put("type", "OPEN_IZIPAY_CHECKOUT");
        nextAction.put("provider", "IZIPAY");
        nextAction.put("paymentIntentId", attempt.getId());
        nextAction.put("reservationId", attempt.getReservation().getId());
        nextAction.put("providerReference", transactionId);
        nextAction.put("authorization", session.token());
        nextAction.put("publicKey", session.publicKey());
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
                nextAction);
    }

    private PaymentAttempt resolveAttempt(ConfirmPaymentRequest request, AuthUserPrincipal principal) {
        if (request.paymentIntentId() != null) {
            PaymentAttempt attempt = paymentAttemptRepository.findByIdForUpdate(request.paymentIntentId())
                    .orElseThrow(
                            () -> new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Pago no encontrado."));
            assertPaymentPermission(attempt.getReservation(), principal);
            Reservation res = attempt.getReservation();
            if (res.getStatus() != ReservationStatus.PENDING_PAYMENT
                    && res.getStatus() != ReservationStatus.EXPIRED) {
                throw new ApiException(HttpStatus.CONFLICT, "INVALID_PAYMENT_STATE",
                        "La reserva no esta pendiente de pago (estado actual: " + res.getStatus() + ").");
            }
            return attempt;
        }
        if (request.reservationId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_IDENTIFIER_REQUIRED",
                    "Debes enviar paymentIntentId o reservationId.");
        }

        Reservation reservation = reservationService.requireReservation(request.reservationId());
        assertPaymentPermission(reservation, principal);
        if (reservation.getStatus() != ReservationStatus.PENDING_PAYMENT
                && reservation.getStatus() != ReservationStatus.EXPIRED) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_PAYMENT_STATE",
                    "La reserva no esta pendiente de pago.");
        }
        return paymentAttemptRepository
                .findFirstByReservationIdAndStatusForUpdate(reservation.getId(), PaymentStatus.PENDING)
                .orElseGet(() -> paymentAttemptRepository
                        .save(PaymentAttempt.pending(reservation, reservation.getTotalPrice())));
    }

    private PaymentAttempt resolveAttemptForWebhook(JsonNode payload, String providerReference) {
        if (StringUtils.hasText(providerReference)) {
            PaymentAttempt byReference = paymentAttemptRepository.findByProviderReference(providerReference)
                    .orElse(null);
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

        // V4 Krypton IPN: orderId = orderNumber = first 10 chars of providerReference
        // (transactionId)
        String orderId = firstNonBlank(
                textAt(payload, "orderDetails.orderId"),
                textAt(payload, "orderId"));
        if (StringUtils.hasText(orderId)) {
            PaymentAttempt byOrderId = paymentAttemptRepository
                    .findFirstByProviderReferenceStartingWithOrderByCreatedAtDesc(orderId)
                    .orElse(null);
            if (byOrderId != null) {
                return byOrderId;
            }
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

        String ref = attempt.getProviderReference() == null ? ""
                : attempt.getProviderReference().toLowerCase(Locale.ROOT);
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
            Map<String, Object> nextAction) {
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
                nextAction);
    }

    private PaymentHistoryItemResponse toHistory(PaymentAttempt attempt) {
        PaymentMethod method = resolveMethod(attempt, null);
        PaymentHistoryItemResponse.ReconciliationInfo reconciliation =
                reconciliationAuditRepository.findTopByPaymentAttemptIdOrderByCreatedAtDesc(attempt.getId())
                        .map(a -> new PaymentHistoryItemResponse.ReconciliationInfo(
                                a.getOutcome(),
                                a.getMatchReason(),
                                a.getMatchedFields(),
                                a.getSenderName(),
                                a.getSenderEmail(),
                                a.getTxDateTimeRaw(),
                                a.getReceivedAt(),
                                a.getMessageId()))
                        .orElse(null);
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
                attempt.getCreatedAt(),
                reconciliation);
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
                attempt.getGatewayStatus(),
                attempt.getGatewayMessage(),
                attempt.getCreatedAt(),
                attempt.getReservation().getStartAt(),
                attempt.getReservation().getEndAt());
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
        String reference = attempt.getProviderReference() == null ? ""
                : attempt.getProviderReference().toLowerCase(Locale.ROOT);
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
        if (attempt.getStatus() == PaymentStatus.PENDING
                && (method == PaymentMethod.COUNTER || method == PaymentMethod.CASH)) {
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
        return warehouseAccessService.isAdmin(principal)
                || warehouseAccessService.isOperatorOrCitySupervisor(principal);
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
            Instant refundRequestedAt) {
        BigDecimal amount = normalizeMoney(totalAmount);
        if (amount.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (paymentCreatedAt == null || refundRequestedAt == null) {
            return normalizeMoney(refundMinimumFee).min(amount);
        }

        long minutesElapsed = Math.max(
                0L,
                Duration.between(paymentCreatedAt, refundRequestedAt).toMinutes());
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
            return new String[] { "Cliente", "TravelBox" };
        }
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) {
            return new String[] { parts[0], "TravelBox" };
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
        return new String[] { String.join(" ", firstNames), String.join(" ", lastNames) };
    }

    private boolean webhookApproved(String eventType, String providerStatus, JsonNode payload) {
        String code = firstNonBlank(textAt(payload, "code"), providerStatus);
        if ("00".equals(code) || "0".equals(code)) {
            return true;
        }
        // V4 Krypton IPN: orderStatus=PAID, transactions[0].status=PAID
        String orderStatus = firstNonBlank(textAt(payload, "orderStatus"), "");
        if ("PAID".equalsIgnoreCase(orderStatus)) {
            return true;
        }
        String txStatus = firstNonBlank(textAt(payload, "transactions.0.status"), "");
        if ("PAID".equalsIgnoreCase(txStatus) || "ACCEPTED".equalsIgnoreCase(txStatus)) {
            return true;
        }
        String combined = (eventType + " " + providerStatus).toLowerCase(Locale.ROOT);
        if (combined.contains("autorizado")
                || combined.contains("paid")
                || combined.contains("succeed")
                || combined.contains("approved")
                || combined.contains("captured")
                || combined.contains("successful")
                || combined.contains("authorised")) {
            return true;
        }
        String stateMessage = textAt(payload, "response.order.0.stateMessage");
        return stateMessage != null && stateMessage.toLowerCase(Locale.ROOT).contains("autorizado");
    }

    private boolean webhookRejected(String eventType, String providerStatus, JsonNode payload) {
        // NOTE: do NOT reject purely based on code != "00" â€” intermediate Izipay
        // codes
        // (e.g. 40=review,
        // 97=3DS-pending) are non-zero but not final. The keyword check below is the
        // reliable signal.
        // V4 Krypton IPN: orderStatus=UNPAID + transactions[0].status=REFUSED/ERROR
        String orderStatus = firstNonBlank(textAt(payload, "orderStatus"), "");
        if ("UNPAID".equalsIgnoreCase(orderStatus)) {
            String txStatus = firstNonBlank(textAt(payload, "transactions.0.status"), "");
            if ("REFUSED".equalsIgnoreCase(txStatus) || "ERROR".equalsIgnoreCase(txStatus)) {
                return true;
            }
        }
        String txDetailed = firstNonBlank(textAt(payload, "transactions.0.detailedStatus"), "");
        if ("REFUSED".equalsIgnoreCase(txDetailed) || "CANCELLED".equalsIgnoreCase(txDetailed)) {
            return true;
        }
        String combined = (eventType + " " + providerStatus).toLowerCase(Locale.ROOT);
        if (combined.contains("rechazado")
                || combined.contains("failed")
                || combined.contains("declined")
                || combined.contains("canceled")
                || combined.contains("cancelled")
                || combined.contains("expired")
                || combined.contains("reject")
                || combined.contains("refused")) {
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
            String document) {
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
        // All Izipay-supported methods: CARD, YAPE_CODE, QR, PAYPAL, E_WALLET.
        // The merchant may not have all enabled, but ordering them does no harm.
        List<String> preferredOrder = switch (selectedMethod) {
            case CARD -> List.of("CARD", "YAPE_CODE", "QR", "PAYPAL", "E_WALLET");
            case YAPE -> List.of("YAPE_CODE", "CARD", "QR", "PAYPAL", "E_WALLET");
            case PLIN, WALLET -> List.of("QR", "CARD", "YAPE_CODE", "PAYPAL", "E_WALLET");
            default -> List.of("CARD", "YAPE_CODE", "QR", "PAYPAL", "E_WALLET");
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
            AuthUserPrincipal principal) {
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
                textAt(payload, "response.order.0.payMethodAuthorization")));
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
                textAt(payload, "response.expirationYear"));

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
