package com.tuempresa.storage.ops.application.usecase;

import com.tuempresa.storage.delivery.domain.DeliveryOrder;
import com.tuempresa.storage.delivery.domain.DeliveryStatus;
import com.tuempresa.storage.delivery.infrastructure.out.persistence.DeliveryOrderRepository;
import com.tuempresa.storage.inventory.application.dto.CheckinRequest;
import com.tuempresa.storage.inventory.application.dto.CheckoutRequest;
import com.tuempresa.storage.inventory.application.usecase.InventoryService;
import com.tuempresa.storage.notifications.application.usecase.NotificationService;
import com.tuempresa.storage.ops.application.dto.OpsApprovalItemResponse;
import com.tuempresa.storage.ops.application.dto.OpsApprovalRequest;
import com.tuempresa.storage.ops.application.dto.OpsQrCaseResponse;
import com.tuempresa.storage.ops.domain.OpsWorkflowConstants;
import com.tuempresa.storage.ops.domain.QrHandoffApproval;
import com.tuempresa.storage.ops.domain.QrHandoffApprovalStatus;
import com.tuempresa.storage.ops.domain.QrHandoffCase;
import com.tuempresa.storage.ops.infrastructure.out.persistence.QrHandoffApprovalRepository;
import com.tuempresa.storage.ops.infrastructure.out.persistence.QrHandoffCaseRepository;
import com.tuempresa.storage.reservations.domain.Reservation;
import com.tuempresa.storage.reservations.domain.ReservationStatus;
import com.tuempresa.storage.reservations.infrastructure.out.persistence.ReservationRepository;
import com.tuempresa.storage.shared.domain.exception.ApiException;
import com.tuempresa.storage.shared.infrastructure.security.AuthUserPrincipal;
import com.tuempresa.storage.shared.infrastructure.security.WarehouseAccessService;
import com.tuempresa.storage.users.domain.Role;
import com.tuempresa.storage.users.domain.User;
import com.tuempresa.storage.users.infrastructure.out.persistence.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class OpsQrHandoffService {

    private static final Set<Role> PRIVILEGED_ROLES = Set.of(Role.ADMIN, Role.OPERATOR, Role.CITY_SUPERVISOR);

    private final ReservationRepository reservationRepository;
    private final QrHandoffCaseRepository qrCaseRepository;
    private final QrHandoffApprovalRepository approvalRepository;
    private final UserRepository userRepository;
    private final DeliveryOrderRepository deliveryOrderRepository;
    private final InventoryService inventoryService;
    private final NotificationService notificationService;
    private final WarehouseAccessService warehouseAccessService;
    private final PasswordEncoder passwordEncoder;
    private final OpsMessageTranslationService opsMessageTranslationService;
    private final int pinExpiryMinutes;
    private final int maxPinAttempts;
    private final int pinLockSeconds;
    private final String bagTagPrefix;

    public OpsQrHandoffService(
            ReservationRepository reservationRepository,
            QrHandoffCaseRepository qrCaseRepository,
            QrHandoffApprovalRepository approvalRepository,
            UserRepository userRepository,
            DeliveryOrderRepository deliveryOrderRepository,
            InventoryService inventoryService,
            NotificationService notificationService,
            WarehouseAccessService warehouseAccessService,
            PasswordEncoder passwordEncoder,
            OpsMessageTranslationService opsMessageTranslationService,
            @Value("${app.ops.qr-pin.pin-expiry-minutes:15}") int pinExpiryMinutes,
            @Value("${app.ops.qr-pin.max-pin-attempts:5}") int maxPinAttempts,
            @Value("${app.ops.qr-pin.pin-lock-seconds:300}") int pinLockSeconds,
            @Value("${app.ops.qr-pin.bag-tag-prefix:BAG}") String bagTagPrefix
    ) {
        this.reservationRepository = reservationRepository;
        this.qrCaseRepository = qrCaseRepository;
        this.approvalRepository = approvalRepository;
        this.userRepository = userRepository;
        this.deliveryOrderRepository = deliveryOrderRepository;
        this.inventoryService = inventoryService;
        this.notificationService = notificationService;
        this.warehouseAccessService = warehouseAccessService;
        this.passwordEncoder = passwordEncoder;
        this.opsMessageTranslationService = opsMessageTranslationService;
        this.pinExpiryMinutes = Math.max(5, pinExpiryMinutes);
        this.maxPinAttempts = Math.max(2, maxPinAttempts);
        this.pinLockSeconds = Math.max(60, pinLockSeconds);
        this.bagTagPrefix = bagTagPrefix == null || bagTagPrefix.isBlank() ? "BAG" : bagTagPrefix.trim().toUpperCase(Locale.ROOT);
    }

    @Transactional
    public OpsQrCaseResponse scan(String scannedValue, String customerLanguage, AuthUserPrincipal principal) {
        Reservation reservation = resolveReservationByScan(scannedValue);
        assertOpsAccess(reservation, principal, true);
        QrHandoffCase handoff = loadOrCreateCase(reservation, customerLanguage);
        handoff.markQrValidated(customerLanguage);
        qrCaseRepository.save(handoff);
        emitRealtimeSync(
                reservation,
                "OPS_QR_SCANNED_SYNC",
                Map.of(
                        "reservationId", reservation.getId(),
                        "route", OpsWorkflowConstants.OPS_QR_ROUTE
                )
        );
        return toCaseResponse(handoff, principal, null);
    }

    @Transactional(readOnly = true)
    public OpsQrCaseResponse detail(Long reservationId, AuthUserPrincipal principal) {
        Reservation reservation = requireReservation(reservationId);
        assertOpsAccess(reservation, principal, true);
        QrHandoffCase handoff = qrCaseRepository.findByReservationId(reservationId)
                .orElseGet(() -> QrHandoffCase.createForReservation(reservation, reservation.getUser().getPreferredLanguage()));
        return toCaseResponse(handoff, principal, null);
    }

    @Transactional(readOnly = true)
    public List<OpsQrCaseResponse> batchDetail(List<Long> reservationIds, AuthUserPrincipal principal) {
        if (reservationIds == null || reservationIds.isEmpty()) {
            return List.of();
        }
        List<Long> ids = reservationIds.stream().distinct().limit(200).toList();
        List<QrHandoffCase> cases = qrCaseRepository.findByReservationIdIn(ids);
        return cases.stream()
                .map(c -> toCaseResponse(c, principal, null))
                .toList();
    }

    @Transactional
    public OpsQrCaseResponse tagLuggage(Long reservationId, Integer bagUnits, AuthUserPrincipal principal) {
        assertPrivileged(principal);
        Reservation reservation = requireReservation(reservationId);
        if (reservation.getStatus() != ReservationStatus.CONFIRMED
                && reservation.getStatus() != ReservationStatus.CHECKIN_PENDING) {
            throw api(
                    HttpStatus.CONFLICT,
                    "BAG_TAG_NOT_ALLOWED",
                    "El ID de maleta solo puede generarse antes del ingreso a almacen."
            );
        }
        QrHandoffCase handoff = loadOrCreateCase(reservation, reservation.getUser().getPreferredLanguage());
        String bagTagId = handoff.getBagTagId() == null || handoff.getBagTagId().isBlank()
                ? generateBagTag(reservation.getQrCode())
                : handoff.getBagTagId();
        handoff.assignBagTag(bagTagId, bagUnits == null ? reservation.getEstimatedItems() : bagUnits);
        qrCaseRepository.save(handoff);
        emitRealtimeSync(
                reservation,
                "OPS_QR_BAG_TAGGED_SYNC",
                Map.of(
                        "reservationId", reservation.getId(),
                        "bagTagId", bagTagId,
                        "route", OpsWorkflowConstants.OPS_QR_ROUTE
                )
        );
        return toCaseResponse(handoff, principal, null);
    }

    @Transactional
    public OpsQrCaseResponse markStored(Long reservationId, String notes, AuthUserPrincipal principal) {
        assertPrivileged(principal);
        throw api(
                HttpStatus.CONFLICT,
                "STORE_WITH_PHOTOS_REQUIRED",
                "Debes registrar en almacen usando fotos por bulto en /store-with-photos."
        );
    }

    @Transactional
    public OpsQrCaseResponse markStoredWithPhotos(
            Long reservationId,
            String notes,
            List<MultipartFile> bagPhotos,
            AuthUserPrincipal principal
    ) throws Exception {
        assertPrivileged(principal);
        Reservation reservation = requireReservation(reservationId);
        QrHandoffCase handoff = loadOrCreateCase(reservation, reservation.getUser().getPreferredLanguage());
        if (handoff.getBagTagId() == null || handoff.getBagTagId().isBlank()) {
            throw api(HttpStatus.CONFLICT, "BAG_TAG_REQUIRED", "Primero debes generar el ID de maleta antes de registrar en almacen.");
        }
        int expectedPhotos = Math.max(1, handoff.getBagUnits());
        if (bagPhotos == null || bagPhotos.size() != expectedPhotos) {
            throw api(
                    HttpStatus.BAD_REQUEST,
                    "LUGGAGE_PHOTOS_COUNT_MISMATCH",
                    "Debes registrar exactamente " + expectedPhotos + " foto(s) del equipaje para esta reserva."
            );
        }
        if (reservation.getStatus() != ReservationStatus.CONFIRMED
                && reservation.getStatus() != ReservationStatus.CHECKIN_PENDING) {
            throw api(
                    HttpStatus.CONFLICT,
                    "STORE_NOT_ALLOWED",
                    "La reserva no puede registrarse en almacen en su estado actual."
            );
        }
        if (reservation.isPickupRequested() && !inventoryService.hasClientHandoffPhoto(reservationId)) {
            throw api(
                    HttpStatus.CONFLICT,
                    "CLIENT_HANDOFF_PHOTO_REQUIRED",
                    "Falta la foto inicial del equipaje tomada por el cliente antes del ingreso a almacen."
            );
        }
        inventoryService.checkinWithBaggagePhotos(
                new CheckinRequest(reservationId, notesOrDefault(notes, "Ingreso a almacen con fotos por bulto.")),
                bagPhotos,
                principal
        );
        handoff.markStoredAtWarehouse();
        qrCaseRepository.save(handoff);
        notifyPrivilegedUsers(
                "RESERVATION_STORED",
                "Ingreso confirmado en almacen",
                "La reserva " + reservation.getQrCode() + " ya fue registrada en almacen con fotos del equipaje.",
                Map.of("reservationId", reservation.getId())
        );
        emitRealtimeSync(
                reservation,
                "OPS_QR_STORED_SYNC",
                Map.of(
                        "reservationId", reservation.getId(),
                        "route", OpsWorkflowConstants.OPS_QR_ROUTE
                )
        );
        return toCaseResponse(handoff, principal, null);
    }

    @Transactional
    public OpsQrCaseResponse markReadyForPickup(Long reservationId, String notes, AuthUserPrincipal principal) {
        assertPrivileged(principal);
        Reservation reservation = requireReservation(reservationId);
        QrHandoffCase handoff = loadOrCreateCase(reservation, reservation.getUser().getPreferredLanguage());
        if (handoff.getBagTagId() == null || handoff.getBagTagId().isBlank()) {
            throw api(HttpStatus.CONFLICT, "BAG_TAG_REQUIRED", "Primero debes generar el ID de maleta antes de emitir PIN.");
        }
        int expectedPhotos = Math.max(1, handoff.getBagUnits());
        int registeredPhotos = inventoryService.countLuggagePhotos(reservationId);
        if (registeredPhotos < expectedPhotos) {
            throw api(
                    HttpStatus.CONFLICT,
                    "LUGGAGE_PHOTOS_REQUIRED",
                    "Aun faltan fotos del equipaje en almacen: " + registeredPhotos + "/" + expectedPhotos + "."
            );
        }
        if (reservation.getStatus() == ReservationStatus.STORED) {
            inventoryService.checkout(new CheckoutRequest(reservationId, notesOrDefault(notes, "Lista para recojo con PIN.")), principal);
        } else if (reservation.getStatus() != ReservationStatus.READY_FOR_PICKUP) {
            throw api(
                    HttpStatus.CONFLICT,
                    "READY_FOR_PICKUP_NOT_ALLOWED",
                    "No se puede generar PIN de recojo para el estado actual."
            );
        }
        String pin = generatePin();
        handoff.markReadyForPickup(passwordEncoder.encode(pin), pin, Instant.now().plus(pinExpiryMinutes, ChronoUnit.MINUTES));
        qrCaseRepository.save(handoff);
        Map<String, Object> customerPayload = new java.util.LinkedHashMap<>();
        customerPayload.put("reservationId", reservationId);
        customerPayload.put("pickupPin", pin);
        if (handoff.getBagTagId() != null && !handoff.getBagTagId().isBlank()) {
            customerPayload.put("bagTagId", handoff.getBagTagId());
        }
        if (handoff.getBagTagQrPayload() != null && !handoff.getBagTagQrPayload().isBlank()) {
            customerPayload.put("bagTagQrPayload", handoff.getBagTagQrPayload());
        }
        notificationService.notifyUser(
                reservation.getUser().getId(),
                "RESERVATION_READY_FOR_PICKUP",
                "Reserva lista para recojo",
                "Tu reserva " + reservation.getQrCode() + " ya esta lista para recojo. PIN: " + pin + ".",
                customerPayload
        );
        notifyPrivilegedUsers(
                "RESERVATION_READY_FOR_PICKUP",
                "PIN de recojo generado",
                "Se genero PIN para la reserva " + reservation.getQrCode() + ".",
                Map.of("reservationId", reservation.getId())
        );
        emitRealtimeSync(
                reservation,
                "OPS_QR_READY_FOR_PICKUP_SYNC",
                Map.of(
                        "reservationId", reservation.getId(),
                        "route", OpsWorkflowConstants.OPS_QR_ROUTE
                )
        );
        return toCaseResponse(handoff, principal, pin);
    }

    @Transactional
    public OpsQrCaseResponse validatePickupPin(Long reservationId, String pin, String notes, AuthUserPrincipal principal) {
        assertPrivileged(principal);
        Reservation reservation = requireReservation(reservationId);
        assertPickupPinFlow(reservation);
        QrHandoffCase handoff = requireCase(reservationId);
        validatePinOrThrow(handoff, pin);
        handoff.markPickupPinValidated();
        inventoryService.checkout(new CheckoutRequest(reservationId, notesOrDefault(notes, "Entrega presencial validada con PIN.")), principal);
        qrCaseRepository.save(handoff);
        emitRealtimeSync(
                reservation,
                "OPS_QR_PICKUP_VALIDATED_SYNC",
                Map.of(
                        "reservationId", reservation.getId(),
                        "route", OpsWorkflowConstants.OPS_QR_ROUTE
                )
        );
        return toCaseResponse(handoff, principal, null);
    }

    @Transactional
    public OpsQrCaseResponse setDeliveryIdentity(Long reservationId, boolean value, AuthUserPrincipal principal) {
        Reservation reservation = requireReservation(reservationId);
        assertOpsAccess(reservation, principal, true);
        QrHandoffCase handoff = loadOrCreateCase(reservation, reservation.getUser().getPreferredLanguage());
        if (value) {
            assertDeliveryValidationFlow(reservation);
        }
        handoff.setDeliveryIdentityValidated(value);
        qrCaseRepository.save(handoff);
        emitRealtimeSync(
                reservation,
                "OPS_QR_IDENTITY_SYNC",
                Map.of(
                        "reservationId", reservation.getId(),
                        "identityValidated", value,
                        "route", OpsWorkflowConstants.OPS_QR_ROUTE
                )
        );
        return toCaseResponse(handoff, principal, null);
    }

    @Transactional
    public OpsQrCaseResponse setDeliveryLuggage(Long reservationId, boolean value, AuthUserPrincipal principal) {
        Reservation reservation = requireReservation(reservationId);
        assertOpsAccess(reservation, principal, true);
        QrHandoffCase handoff = loadOrCreateCase(reservation, reservation.getUser().getPreferredLanguage());
        if (value) {
            assertDeliveryValidationFlow(reservation);
            if (!handoff.isIdentityValidated()) {
                throw api(
                        HttpStatus.CONFLICT,
                        OpsWorkflowConstants.ERROR_DELIVERY_STEP_ORDER_INVALID,
                        "Primero debes validar la identidad antes de validar el equipaje."
                );
            }
        }
        handoff.setLuggageMatched(value);
        qrCaseRepository.save(handoff);
        emitRealtimeSync(
                reservation,
                "OPS_QR_LUGGAGE_SYNC",
                Map.of(
                        "reservationId", reservation.getId(),
                        "luggageMatched", value,
                        "route", OpsWorkflowConstants.OPS_QR_ROUTE
                )
        );
        return toCaseResponse(handoff, principal, null);
    }

    @Transactional
    public OpsQrCaseResponse requestApproval(Long reservationId, OpsApprovalRequest request, AuthUserPrincipal principal) {
        Reservation reservation = requireReservation(reservationId);
        assertOpsAccess(reservation, principal, true);
        assertDeliveryValidationFlow(reservation);
        User requester = requireUser(principal.getId());
        QrHandoffCase handoff = loadOrCreateCase(reservation, request.customerLanguage());
        assertDeliveryStepsReadyForApproval(handoff);
        if (approvalRepository.existsByReservationIdAndStatus(reservationId, QrHandoffApprovalStatus.PENDING)) {
            throw api(
                    HttpStatus.CONFLICT,
                    OpsWorkflowConstants.ERROR_DELIVERY_APPROVAL_ALREADY_PENDING,
                    "Ya existe una solicitud de aprobacion pendiente para esta reserva."
            );
        }
        String translated = opsMessageTranslationService.translateFromSpanish(
                request.messageForCustomerSpanish(),
                handoff.getCustomerLanguage()
        );
        handoff.markApprovalRequested(request.messageForCustomerSpanish(), translated);
        QrHandoffApproval approval = QrHandoffApproval.pending(
                reservation,
                requester,
                request.messageForOperator(),
                request.messageForCustomerSpanish(),
                translated
        );
        approvalRepository.save(approval);
        qrCaseRepository.save(handoff);
        notifyApprovers(reservation, requester.getId(), approval.getId());
        emitRealtimeSync(
                reservation,
                "OPS_QR_APPROVAL_REQUESTED_SYNC",
                Map.of(
                        "reservationId", reservation.getId(),
                        "route", OpsWorkflowConstants.OPS_QR_ROUTE
                )
        );
        return toCaseResponse(handoff, principal, null);
    }

    @Transactional(readOnly = true)
    public List<OpsApprovalItemResponse> listApprovals(QrHandoffApprovalStatus status, Long reservationId, AuthUserPrincipal principal) {
        assertPrivileged(principal);
        if (reservationId != null) {
            return approvalRepository.findTop100ByReservationIdOrderByCreatedAtDesc(reservationId).stream().map(this::toApproval).toList();
        }
        QrHandoffApprovalStatus effective = status == null ? QrHandoffApprovalStatus.PENDING : status;
        return approvalRepository.findTop100ByStatusOrderByCreatedAtDesc(effective).stream().map(this::toApproval).toList();
    }

    @Transactional
    public OpsQrCaseResponse approve(Long approvalId, String requestedPin, AuthUserPrincipal principal) {
        assertPrivileged(principal);
        QrHandoffApproval approval = approvalRepository.findByIdAndStatus(approvalId, QrHandoffApprovalStatus.PENDING)
                .orElseThrow(() -> api(HttpStatus.NOT_FOUND, "APPROVAL_NOT_FOUND", "La solicitud de aprobacion no existe o ya fue procesada."));
        User approver = requireUser(principal.getId());
        assertDeliveryValidationFlow(approval.getReservation());
        String pin = requestedPin == null || requestedPin.isBlank() ? generatePin() : requestedPin.trim();
        QrHandoffCase handoff = loadOrCreateCase(approval.getReservation(), approval.getReservation().getUser().getPreferredLanguage());
        assertDeliveryStepsReadyForApproval(handoff);
        handoff.markApprovalGranted(passwordEncoder.encode(pin), pin, Instant.now().plus(pinExpiryMinutes, ChronoUnit.MINUTES));
        approval.approve(approver);
        approvalRepository.save(approval);
        qrCaseRepository.save(handoff);
        notificationService.notifyUser(
                approval.getReservation().getUser().getId(),
                "DELIVERY_APPROVAL_GRANTED",
                "Delivery aprobado",
                "Tu reserva " + approval.getReservation().getQrCode() + " fue aprobada para entrega. PIN: " + pin + ".",
                Map.of(
                        "reservationId", approval.getReservation().getId(),
                        "pickupPin", pin
                )
        );
        notifyPrivilegedUsers(
                "DELIVERY_APPROVAL_GRANTED_FOR_WAREHOUSE",
                "Aprobacion de entrega concedida",
                "La reserva " + approval.getReservation().getQrCode() + " ya tiene aprobacion y PIN para entrega.",
                Map.of(
                        "reservationId", approval.getReservation().getId(),
                        "approvalId", approval.getId(),
                        "route", OpsWorkflowConstants.OPS_QR_ROUTE
                )
        );
        emitRealtimeSync(
                approval.getReservation(),
                "OPS_QR_APPROVAL_GRANTED_SYNC",
                Map.of(
                        "reservationId", approval.getReservation().getId(),
                        "route", OpsWorkflowConstants.OPS_QR_ROUTE
                )
        );
        return toCaseResponse(handoff, principal, pin);
    }

    @Transactional
    public OpsApprovalItemResponse reject(Long approvalId, AuthUserPrincipal principal) {
        assertPrivileged(principal);
        QrHandoffApproval approval = approvalRepository.findByIdAndStatus(approvalId, QrHandoffApprovalStatus.PENDING)
                .orElseThrow(() -> api(HttpStatus.NOT_FOUND, "APPROVAL_NOT_FOUND", "La solicitud de aprobacion no existe o ya fue procesada."));
        approval.reject(requireUser(principal.getId()));
        approvalRepository.save(approval);
        notifyPrivilegedUsers(
                "DELIVERY_APPROVAL_REJECTED_FOR_WAREHOUSE",
                "Aprobacion de entrega rechazada",
                "La solicitud de entrega de la reserva " + approval.getReservation().getQrCode() + " fue rechazada.",
                Map.of(
                        "reservationId", approval.getReservation().getId(),
                        "approvalId", approval.getId(),
                        "route", OpsWorkflowConstants.OPS_QR_ROUTE
                )
        );
        return toApproval(approval);
    }

    @Transactional
    public OpsQrCaseResponse completeDelivery(Long reservationId, String pin, String notes, AuthUserPrincipal principal) {
        Reservation reservation = requireReservation(reservationId);
        assertOpsAccess(reservation, principal, true);
        assertDeliveryValidationFlow(reservation);
        QrHandoffCase handoff = requireCase(reservationId);
        if (!handoff.isIdentityValidated() || !handoff.isLuggageMatched() || !handoff.isOperatorApprovalGranted()) {
            throw api(HttpStatus.CONFLICT, "DELIVERY_VALIDATION_REQUIRED", "Faltan validaciones previas para completar la entrega.");
        }
        validatePinOrThrow(handoff, pin);
        inventoryService.checkout(new CheckoutRequest(reservationId, notesOrDefault(notes, "Delivery completado con PIN.")), principal);
        handoff.markDeliveryCompleted();
        qrCaseRepository.save(handoff);
        notificationService.notifyUser(reservation.getUser().getId(), "DELIVERY_COMPLETED", "Entrega completada", "Tu equipaje fue entregado.", Map.of("reservationId", reservationId));
        notifyPrivilegedUsers(
                "DELIVERY_COMPLETED",
                "Entrega completada",
                "La reserva " + reservation.getQrCode() + " fue marcada como completada.",
                Map.of("reservationId", reservationId)
        );
        emitRealtimeSync(
                reservation,
                "OPS_QR_DELIVERY_COMPLETED_SYNC",
                Map.of(
                        "reservationId", reservation.getId(),
                        "route", OpsWorkflowConstants.OPS_QR_ROUTE
                )
        );
        return toCaseResponse(handoff, principal, null);
    }

    private Reservation resolveReservationByScan(String scannedValue) {
        if (scannedValue == null || scannedValue.isBlank()) {
            throw api(HttpStatus.BAD_REQUEST, "QR_VALUE_REQUIRED", "Debes enviar un valor de QR valido.");
        }
        String raw = scannedValue.trim();
        String upper = raw.toUpperCase(Locale.ROOT);
        if (upper.startsWith("TRAVELBOX|BAG|")) {
            String[] p = raw.split("\\|");
            if (p.length >= 3) {
                return qrCaseRepository.findByBagTagIdIgnoreCase(p[2].trim()).map(QrHandoffCase::getReservation)
                        .orElseThrow(() -> api(HttpStatus.NOT_FOUND, "QR_NOT_FOUND", "No se encontro una reserva asociada al QR escaneado."));
            }
        }
        String code = raw;
        if (upper.startsWith("TRAVELBOX|RESERVATION|")) {
            String[] p = raw.split("\\|");
            if (p.length >= 3) code = p[2].trim();
        }
        return reservationRepository.findByQrCodeIgnoreCase(code)
                .orElseThrow(() -> api(HttpStatus.NOT_FOUND, "QR_NOT_FOUND", "No se encontro una reserva asociada al QR escaneado."));
    }

    private void assertPickupPinFlow(Reservation reservation) {
        if (!OpsWorkflowConstants.PICKUP_PIN_ALLOWED_STATUSES.contains(reservation.getStatus())) {
            throw api(
                    HttpStatus.CONFLICT,
                    OpsWorkflowConstants.ERROR_PICKUP_FLOW_REQUIRED,
                    "La validacion de PIN presencial solo aplica para reservas listas para recojo."
            );
        }
    }

    private void assertDeliveryValidationFlow(Reservation reservation) {
        if (!reservation.isDropoffRequested()
                || !OpsWorkflowConstants.DELIVERY_VALIDATION_ALLOWED_STATUSES.contains(reservation.getStatus())) {
            throw api(
                    HttpStatus.CONFLICT,
                    OpsWorkflowConstants.ERROR_DELIVERY_FLOW_REQUIRED,
                    "Las validaciones de delivery solo aplican para reservas en salida de entrega."
            );
        }
        DeliveryOrder latestOrder = deliveryOrderRepository
                .findFirstByReservationIdOrderByCreatedAtDesc(reservation.getId())
                .orElse(null);
        if (latestOrder == null
                || !"DELIVERY".equalsIgnoreCase(latestOrder.getType())
                || latestOrder.getStatus() == DeliveryStatus.CANCELLED
                || latestOrder.getStatus() == DeliveryStatus.DELIVERED) {
            throw api(
                    HttpStatus.CONFLICT,
                    OpsWorkflowConstants.ERROR_DELIVERY_FLOW_REQUIRED,
                    "No existe un servicio de entrega activo para esta reserva."
            );
        }
    }

    private void assertDeliveryStepsReadyForApproval(QrHandoffCase handoff) {
        if (!handoff.isIdentityValidated()) {
            throw api(
                    HttpStatus.CONFLICT,
                    OpsWorkflowConstants.ERROR_DELIVERY_STEP_ORDER_INVALID,
                    "Primero debes validar identidad antes de solicitar aprobacion."
            );
        }
        if (!handoff.isLuggageMatched()) {
            throw api(
                    HttpStatus.CONFLICT,
                    OpsWorkflowConstants.ERROR_DELIVERY_STEP_ORDER_INVALID,
                    "Primero debes validar equipaje antes de solicitar aprobacion."
            );
        }
    }

    private void validatePinOrThrow(QrHandoffCase handoff, String pin) {
        if (pin == null || pin.isBlank()) throw api(HttpStatus.BAD_REQUEST, "PIN_REQUIRED", "Debes ingresar un PIN valido.");
        Instant now = Instant.now();
        if (handoff.isPinLocked(now)) throw api(HttpStatus.TOO_MANY_REQUESTS, "PIN_LOCKED", "PIN bloqueado temporalmente por demasiados intentos.");
        if (handoff.getPinExpiresAt() == null || handoff.getPinExpiresAt().isBefore(now)) throw api(HttpStatus.CONFLICT, "PIN_EXPIRED", "El PIN ya expiro.");
        if (handoff.getPickupPinHash() == null || !passwordEncoder.matches(pin.trim(), handoff.getPickupPinHash())) {
            handoff.registerFailedPinAttempt(now, maxPinAttempts, pinLockSeconds);
            qrCaseRepository.save(handoff);
            throw api(HttpStatus.BAD_REQUEST, "PIN_INVALID", "El PIN ingresado no es correcto.");
        }
    }

    private void notifyApprovers(Reservation reservation, Long requesterId, Long approvalId) {
        for (User user : scopedOpsAudience(reservation)) {
            notificationService.notifyUser(
                    user.getId(),
                    "DELIVERY_APPROVAL_REQUESTED",
                    "Solicitud de aprobacion de entrega",
                    "Reserva " + reservation.getQrCode() + " requiere aprobacion.",
                    Map.of("reservationId", reservation.getId(), "approvalId", approvalId, "requesterId", requesterId)
            );
        }
    }

    private void notifyPrivilegedUsers(
            String type,
            String title,
            String message,
            Map<String, ?> payload
    ) {
        Long reservationId = payload != null && payload.get("reservationId") instanceof Number
                ? ((Number) payload.get("reservationId")).longValue()
                : null;
        Reservation reservation = reservationId == null ? null : reservationRepository.findById(reservationId).orElse(null);
        List<User> audience = reservation == null
                ? userRepository.findActiveByAnyRole(Set.of(Role.ADMIN))
                : scopedOpsAudience(reservation);
        for (User user : audience) {
            notificationService.notifyUser(user.getId(), type, title, message, payload);
        }
    }

    private void emitRealtimeSync(
            Reservation reservation,
            String type,
            Map<String, ?> payload
    ) {
        if (reservation == null) {
            return;
        }
        java.util.LinkedHashMap<String, Object> eventPayload = new java.util.LinkedHashMap<>();
        if (payload != null) {
            payload.forEach(eventPayload::put);
        }
        eventPayload.putIfAbsent("reservationId", reservation.getId());
        eventPayload.putIfAbsent("warehouseId", reservation.getWarehouse().getId());
        eventPayload.putIfAbsent("warehouseName", reservation.getWarehouse().getName());
        eventPayload.putIfAbsent("route", OpsWorkflowConstants.OPS_QR_ROUTE);
        for (User user : scopedOpsAudience(reservation)) {
            notificationService.emitSilentRealtimeEvent(user.getId(), type, eventPayload);
        }
    }

    private OpsQrCaseResponse toCaseResponse(QrHandoffCase c, AuthUserPrincipal principal, String pinPreview) {
        List<OpsApprovalItemResponse> approvals = approvalRepository.findTop100ByReservationIdOrderByCreatedAtDesc(c.getReservation().getId()).stream().map(this::toApproval).toList();
        String visiblePin = canViewPin(principal, c.getReservation())
                ? (pinPreview == null || pinPreview.isBlank() ? c.getPickupPinPreview() : pinPreview)
                : null;
        return new OpsQrCaseResponse(
                c.getReservation().getId(), c.getReservation().getQrCode(), c.getReservation().getStatus(), c.getReservation().getUser().getId(),
                c.getCustomerLanguage(), c.getCustomerQrPayload(), c.getStage(), c.getBagTagId(), c.getBagTagQrPayload(), c.getBagUnits(),
                c.isIdentityValidated(), c.isLuggageMatched(), c.isOperatorApprovalRequested(), c.isOperatorApprovalGranted(), c.isDeliveryCompleted(),
                c.getPinExpiresAt(), c.isPinLocked(Instant.now()), c.getPinLockedUntil(), c.getLatestMessageForCustomer(), c.getLatestMessageTranslated(),
                visiblePin, c.getUpdatedAt(), approvals
        );
    }

    private boolean canViewPin(AuthUserPrincipal principal, Reservation reservation) {
        if (principal == null || reservation == null) {
            return false;
        }
        if (warehouseAccessService.isAdmin(principal)) {
            return true;
        }
        if (reservation.belongsTo(principal.getId())) {
            return true;
        }
        if (warehouseAccessService.isCourier(principal)
                && isAssignedCourierForReservation(reservation, principal)
                && warehouseAccessService.canAccessWarehouse(principal, reservation.getWarehouse().getId())) {
            return true;
        }
        return warehouseAccessService.isOperatorOrCitySupervisor(principal)
                && warehouseAccessService.canAccessWarehouse(principal, reservation.getWarehouse().getId());
    }

    private OpsApprovalItemResponse toApproval(QrHandoffApproval a) {
        return new OpsApprovalItemResponse(
                a.getId(), a.getReservation().getId(), a.getReservation().getQrCode(), a.getStatus(),
                a.getMessageForOperator(), a.getMessageForCustomer(), a.getMessageForCustomerTranslated(),
                a.getRequestedByUser() == null ? null : a.getRequestedByUser().getId(),
                a.getApprovedByUser() == null ? null : a.getApprovedByUser().getId(),
                a.getCreatedAt(), a.getApprovedAt()
        );
    }

    private QrHandoffCase loadOrCreateCase(Reservation reservation, String language) {
        return qrCaseRepository.findByReservationId(reservation.getId())
                .orElseGet(() -> qrCaseRepository.save(QrHandoffCase.createForReservation(reservation, language)));
    }

    private QrHandoffCase requireCase(Long reservationId) {
        return qrCaseRepository.findByReservationId(reservationId)
                .orElseThrow(() -> api(HttpStatus.NOT_FOUND, "QR_CASE_NOT_FOUND", "No existe un caso QR/PIN para esta reserva."));
    }

    private Reservation requireReservation(Long reservationId) {
        return reservationRepository.findById(reservationId)
                .orElseThrow(() -> api(HttpStatus.NOT_FOUND, "RESERVATION_NOT_FOUND", "Reserva no encontrada."));
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> api(HttpStatus.UNAUTHORIZED, "AUTH_INVALID", "Usuario invalido."));
    }

    private void assertPrivileged(AuthUserPrincipal principal) {
        if (!hasAnyRole(principal, PRIVILEGED_ROLES)) {
            throw api(HttpStatus.FORBIDDEN, "OPS_FORBIDDEN", "No tienes permisos para esta operacion.");
        }
    }

    private void assertOpsAccess(Reservation reservation, AuthUserPrincipal principal, boolean allowCourier) {
        if (warehouseAccessService.isAdmin(principal)) {
            return;
        }
        if (warehouseAccessService.isOperatorOrCitySupervisor(principal)
                && warehouseAccessService.canAccessWarehouse(principal, reservation.getWarehouse().getId())) {
            return;
        }
        if (allowCourier && principal.roleNames().contains(Role.COURIER.name())) {
            if (isAssignedCourierForReservation(reservation, principal)
                    && warehouseAccessService.canAccessWarehouse(principal, reservation.getWarehouse().getId())) {
                return;
            }
        }
        throw api(HttpStatus.FORBIDDEN, "OPS_FORBIDDEN", "No tienes acceso a esta reserva.");
    }

    private boolean hasAnyRole(AuthUserPrincipal principal, Set<Role> roles) {
        for (Role role : roles) if (principal.roleNames().contains(role.name())) return true;
        return false;
    }

    private boolean isAssignedCourierForReservation(Reservation reservation, AuthUserPrincipal principal) {
        DeliveryOrder order = deliveryOrderRepository.findFirstByReservationIdOrderByCreatedAtDesc(reservation.getId()).orElse(null);
        return order != null
                && order.getAssignedCourier() != null
                && order.getAssignedCourier().getId().equals(principal.getId());
    }

    private List<User> scopedOpsAudience(Reservation reservation) {
        List<User> admins = userRepository.findActiveByAnyRole(Set.of(Role.ADMIN));
        List<User> scopedOps = userRepository.findActiveByAnyRoleAndWarehouseId(
                Set.of(Role.OPERATOR, Role.CITY_SUPERVISOR),
                reservation.getWarehouse().getId()
        );
        List<User> scopedCouriers = userRepository.findActiveByAnyRoleAndWarehouseId(
                Set.of(Role.COURIER),
                reservation.getWarehouse().getId()
        );
        java.util.LinkedHashMap<Long, User> unique = new java.util.LinkedHashMap<>();
        admins.forEach(user -> unique.put(user.getId(), user));
        scopedOps.forEach(user -> unique.put(user.getId(), user));
        scopedCouriers.forEach(user -> unique.put(user.getId(), user));
        return List.copyOf(unique.values());
    }

    private String generatePin() {
        return String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1_000_000));
    }

    private String generateBagTag(String reservationCode) {
        String clean = reservationCode == null ? "RES" : reservationCode.replaceAll("[^A-Za-z0-9]", "");
        String suffix = clean.length() > 6 ? clean.substring(clean.length() - 6) : clean;
        return (bagTagPrefix + "-" + suffix + "-" + String.format("%04d", ThreadLocalRandom.current().nextInt(0, 10_000))).toUpperCase(Locale.ROOT);
    }

    private String notesOrDefault(String notes, String fallback) {
        return notes == null || notes.isBlank() ? fallback : notes.trim();
    }

    private ApiException api(HttpStatus status, String code, String message) {
        return new ApiException(status, code, message);
    }
}
