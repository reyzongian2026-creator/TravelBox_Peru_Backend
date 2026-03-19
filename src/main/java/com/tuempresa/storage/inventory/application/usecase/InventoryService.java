package com.tuempresa.storage.inventory.application.usecase;

import com.tuempresa.storage.inventory.application.dto.CheckinRequest;
import com.tuempresa.storage.inventory.application.dto.CheckoutRequest;
import com.tuempresa.storage.inventory.application.dto.EvidenceRequest;
import com.tuempresa.storage.inventory.application.dto.InventoryActionResponse;
import com.tuempresa.storage.inventory.domain.CheckinRecord;
import com.tuempresa.storage.inventory.domain.CheckoutRecord;
import com.tuempresa.storage.inventory.domain.StoredItemEvidence;
import com.tuempresa.storage.inventory.infrastructure.out.persistence.CheckinRecordRepository;
import com.tuempresa.storage.inventory.infrastructure.out.persistence.CheckoutRecordRepository;
import com.tuempresa.storage.inventory.infrastructure.out.persistence.StoredItemEvidenceRepository;
import com.tuempresa.storage.notifications.application.usecase.NotificationService;
import com.tuempresa.storage.notifications.application.email.CustomerEmailService;
import com.tuempresa.storage.payments.domain.PaymentAttempt;
import com.tuempresa.storage.payments.domain.PaymentStatus;
import com.tuempresa.storage.payments.infrastructure.out.persistence.PaymentAttemptRepository;
import com.tuempresa.storage.reservations.application.usecase.ReservationService;
import com.tuempresa.storage.reservations.domain.Reservation;
import com.tuempresa.storage.reservations.domain.ReservationBagSize;
import com.tuempresa.storage.reservations.domain.ReservationStatus;
import com.tuempresa.storage.shared.domain.exception.ApiException;
import com.tuempresa.storage.shared.infrastructure.security.AuthUserPrincipal;
import com.tuempresa.storage.shared.infrastructure.security.WarehouseAccessService;
import com.tuempresa.storage.shared.infrastructure.storage.LocalFileStorageService;
import com.tuempresa.storage.shared.infrastructure.web.PublicUrlService;
import com.tuempresa.storage.users.domain.Role;
import com.tuempresa.storage.users.domain.User;
import com.tuempresa.storage.users.infrastructure.out.persistence.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class InventoryService {

    public static final String LUGGAGE_PHOTO_EVIDENCE_TYPE = "CHECKIN_BAG_PHOTO";
    public static final String CLIENT_HANDOFF_PHOTO_EVIDENCE_TYPE = "CLIENT_HANDOFF_PHOTO";
    private static final String LATE_PICKUP_REFERENCE_PREFIX = "OFFLINE-COUNTER-LATE-";

    private final CheckinRecordRepository checkinRecordRepository;
    private final CheckoutRecordRepository checkoutRecordRepository;
    private final StoredItemEvidenceRepository storedItemEvidenceRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final ReservationService reservationService;
    private final UserRepository userRepository;
    private final LocalFileStorageService localFileStorageService;
    private final PublicUrlService publicUrlService;
    private final WarehouseAccessService warehouseAccessService;
    private final NotificationService notificationService;
    private final CustomerEmailService customerEmailService;
    private final int officePickupLateGraceMinutes;

    public InventoryService(
            CheckinRecordRepository checkinRecordRepository,
            CheckoutRecordRepository checkoutRecordRepository,
            StoredItemEvidenceRepository storedItemEvidenceRepository,
            PaymentAttemptRepository paymentAttemptRepository,
            ReservationService reservationService,
            UserRepository userRepository,
            LocalFileStorageService localFileStorageService,
            PublicUrlService publicUrlService,
            WarehouseAccessService warehouseAccessService,
            NotificationService notificationService,
            CustomerEmailService customerEmailService,
            @Value("${app.inventory.office-pickup-late-grace-minutes:30}") int officePickupLateGraceMinutes
    ) {
        this.checkinRecordRepository = checkinRecordRepository;
        this.checkoutRecordRepository = checkoutRecordRepository;
        this.storedItemEvidenceRepository = storedItemEvidenceRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.reservationService = reservationService;
        this.userRepository = userRepository;
        this.localFileStorageService = localFileStorageService;
        this.publicUrlService = publicUrlService;
        this.warehouseAccessService = warehouseAccessService;
        this.notificationService = notificationService;
        this.customerEmailService = customerEmailService;
        this.officePickupLateGraceMinutes = Math.max(0, officePickupLateGraceMinutes);
    }

    @Transactional
    public InventoryActionResponse checkin(CheckinRequest request, AuthUserPrincipal principal) {
        return checkinInternal(request, principal, List.of());
    }

    @Transactional
    public InventoryActionResponse checkinWithBaggagePhotos(
            CheckinRequest request,
            List<MultipartFile> baggagePhotos,
            AuthUserPrincipal principal
    ) {
        return checkinInternal(
                request,
                principal,
                baggagePhotos == null ? List.of() : baggagePhotos
        );
    }

    private InventoryActionResponse checkinInternal(
            CheckinRequest request,
            AuthUserPrincipal principal,
            List<MultipartFile> baggagePhotos
    ) {
        Reservation reservation = reservationService.requireReservation(request.reservationId());
        assertOperationalWarehouseAccess(principal, reservation);
        User operator = loadUser(principal.getId());

        if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
            reservation.transitionTo(ReservationStatus.CHECKIN_PENDING);
        }
        if (reservation.getStatus() != ReservationStatus.CHECKIN_PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "CHECKIN_NOT_ALLOWED", "El check-in no esta permitido para el estado actual.");
        }
        reservation.transitionTo(ReservationStatus.STORED);
        reservation.getWarehouse().occupyOneSlot();
        CheckinRecord record = checkinRecordRepository.save(CheckinRecord.of(reservation, operator, request.notes()));
        saveBaggagePhotos(reservation, operator, baggagePhotos, request.notes());
        notifyCustomerReservationEvent(
                reservation,
                "RESERVATION_STORED",
                "Equipaje almacenado",
                "Tu reserva " + reservation.getQrCode() + " ya fue registrada en almacen."
        );
        notifyWarehouseAudience(
                reservation,
                "RESERVATION_STORED_FOR_WAREHOUSE",
                "Equipaje almacenado en sede",
                "La reserva " + reservation.getQrCode() + " ya quedo almacenada."
        );
        return new InventoryActionResponse(
                reservation.getId(),
                reservation.getStatus(),
                "CHECKIN_COMPLETED",
                operator.getId(),
                record.getCreatedAt(),
                null
        );
    }

    @Transactional
    public InventoryActionResponse checkout(CheckoutRequest request, AuthUserPrincipal principal) {
        Reservation reservation = reservationService.requireReservation(request.reservationId());
        assertOperationalWarehouseAccess(principal, reservation);
        User operator = loadUser(principal.getId());

        if (reservation.getStatus() == ReservationStatus.STORED) {
            reservation.transitionTo(ReservationStatus.READY_FOR_PICKUP);
            notifyCustomerReservationEvent(
                    reservation,
                    "RESERVATION_READY_FOR_PICKUP",
                    "Reserva lista para recojo",
                    "Tu reserva " + reservation.getQrCode() + " esta lista para recojo."
            );
            notifyWarehouseAudience(
                    reservation,
                    "RESERVATION_READY_FOR_PICKUP_FOR_WAREHOUSE",
                    "Reserva lista para recojo",
                    "La reserva " + reservation.getQrCode() + " quedo lista para recojo."
            );
            return new InventoryActionResponse(
                    reservation.getId(),
                    reservation.getStatus(),
                    "READY_FOR_PICKUP",
                    operator.getId(),
                    Instant.now(),
                    null
            );
        }
        if (reservation.getStatus() != ReservationStatus.READY_FOR_PICKUP
                && reservation.getStatus() != ReservationStatus.OUT_FOR_DELIVERY) {
            throw new ApiException(HttpStatus.CONFLICT, "CHECKOUT_NOT_ALLOWED", "El checkout no esta permitido para el estado actual.");
        }
        if (reservation.getStatus() == ReservationStatus.READY_FOR_PICKUP && !reservation.isDropoffRequested()) {
            enforceOfficePickupLateSurcharge(reservation);
        }
        reservation.transitionTo(ReservationStatus.COMPLETED);
        reservation.getWarehouse().releaseOneSlot();
        CheckoutRecord record = checkoutRecordRepository.save(CheckoutRecord.of(reservation, operator, request.notes()));
        notifyCustomerReservationEvent(
                reservation,
                "RESERVATION_COMPLETED",
                "Entrega completada",
                "Tu reserva " + reservation.getQrCode() + " fue entregada correctamente."
        );
        notifyWarehouseAudience(
                reservation,
                "RESERVATION_COMPLETED_FOR_WAREHOUSE",
                "Reserva finalizada",
                "La reserva " + reservation.getQrCode() + " fue marcada como completada."
        );
        customerEmailService.sendPickupThankYou(reservation.getUser(), reservation);
        return new InventoryActionResponse(
                reservation.getId(),
                reservation.getStatus(),
                "CHECKOUT_COMPLETED",
                operator.getId(),
                record.getCreatedAt(),
                null
        );
    }

    private void enforceOfficePickupLateSurcharge(Reservation reservation) {
        BigDecimal requiredSurcharge = calculateOfficePickupLateSurcharge(reservation, Instant.now());
        if (requiredSurcharge.signum() <= 0) {
            return;
        }
        BigDecimal confirmedCovered = confirmedLatePickupAmount(reservation);
        BigDecimal outstanding = requiredSurcharge.subtract(confirmedCovered).setScale(2, RoundingMode.HALF_UP);
        if (outstanding.signum() > 0) {
            PaymentAttempt pendingCharge = ensurePendingLatePickupCharge(reservation, outstanding);
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "LATE_PICKUP_SURCHARGE_REQUIRED",
                    "Recojo fuera del tiempo programado. Cargo adicional total S/"
                            + requiredSurcharge
                            + " (pendiente por pagar: S/"
                            + outstanding
                            + "). Valida en caja con paymentIntentId "
                            + pendingCharge.getId()
                            + "."
            );
        }
        reservation.applyLatePickupSurcharge(requiredSurcharge);
    }

    private BigDecimal calculateOfficePickupLateSurcharge(Reservation reservation, Instant now) {
        Instant reservationEndAt = reservation.getEndAt();
        if (reservationEndAt == null || now == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        Instant graceDeadline = reservationEndAt.plus(Duration.ofMinutes(officePickupLateGraceMinutes));
        if (!now.isAfter(graceDeadline)) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        long exceededMinutes = Math.max(1L, Duration.between(graceDeadline, now).toMinutes());
        long exceededHours = (long) Math.ceil(exceededMinutes / 60.0d);
        int bagUnits = Math.max(1, reservation.getEstimatedItems());
        BigDecimal hourlyRate = hourlyRateForReservation(reservation);
        return hourlyRate
                .multiply(BigDecimal.valueOf(bagUnits))
                .multiply(BigDecimal.valueOf(Math.max(1L, exceededHours)))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal hourlyRateForReservation(Reservation reservation) {
        ReservationBagSize bagSize = reservation.getBagSize();
        return switch (bagSize) {
            case SMALL -> reservation.getWarehouse().getPricePerHourSmall();
            case MEDIUM -> reservation.getWarehouse().getPricePerHourMedium();
            case LARGE -> reservation.getWarehouse().getPricePerHourLarge();
            case EXTRA_LARGE -> reservation.getWarehouse().getPricePerHourExtraLarge();
        };
    }

    private BigDecimal confirmedLatePickupAmount(Reservation reservation) {
        return paymentAttemptRepository.findByReservationIdOrderByCreatedAtDesc(reservation.getId())
                .stream()
                .filter(this::isLatePickupChargeAttempt)
                .filter(attempt -> attempt.getStatus() == PaymentStatus.CONFIRMED)
                .map(PaymentAttempt::getAmount)
                .filter(amount -> amount != null && amount.signum() > 0)
                .reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private PaymentAttempt ensurePendingLatePickupCharge(Reservation reservation, BigDecimal outstandingAmount) {
        BigDecimal normalizedOutstanding = outstandingAmount.setScale(2, RoundingMode.HALF_UP);
        List<PaymentAttempt> attempts = paymentAttemptRepository.findByReservationIdOrderByCreatedAtDesc(reservation.getId());
        PaymentAttempt pending = attempts.stream()
                .filter(this::isLatePickupChargeAttempt)
                .filter(PaymentAttempt::isPending)
                .findFirst()
                .orElse(null);

        if (pending != null && pending.getAmount() != null
                && pending.getAmount().setScale(2, RoundingMode.HALF_UP).compareTo(normalizedOutstanding) >= 0) {
            return pending;
        }
        if (pending != null) {
            pending.fail(pending.getProviderReference());
            pending.registerGatewayOutcome(
                    "LATE_PICKUP_SURCHARGE_RECALCULATED",
                    "Cargo por tardanza recalculado por mayor tiempo excedido."
            );
        }

        PaymentAttempt attempt = PaymentAttempt.pending(reservation, normalizedOutstanding);
        attempt.registerProviderReference(
                LATE_PICKUP_REFERENCE_PREFIX + reservation.getId() + "-" + System.currentTimeMillis()
        );
        attempt.registerGatewayOutcome(
                "WAITING_OFFLINE_VALIDATION",
                "Cargo adicional por tardanza en recojo en oficina."
        );
        return paymentAttemptRepository.save(attempt);
    }

    private boolean isLatePickupChargeAttempt(PaymentAttempt attempt) {
        if (attempt == null) {
            return false;
        }
        String providerReference = attempt.getProviderReference();
        if (providerReference == null || providerReference.isBlank()) {
            return false;
        }
        return providerReference.trim().toUpperCase().startsWith(LATE_PICKUP_REFERENCE_PREFIX);
    }

    @Transactional
    public InventoryActionResponse addEvidence(EvidenceRequest request, AuthUserPrincipal principal) {
        Reservation reservation = reservationService.requireReservation(request.reservationId());
        assertEvidencePermission(reservation, principal);
        assertEvidenceTypeAllowed(request.type(), reservation, principal);

        User actor = loadUser(principal.getId());
        String evidenceUrl = publicUrlService.absolute(request.url());
        StoredItemEvidence evidence = storedItemEvidenceRepository.save(
                StoredItemEvidence.of(
                        reservation,
                        actor,
                        request.type(),
                        evidenceUrl,
                        request.observation()
                )
        );
        return new InventoryActionResponse(
                reservation.getId(),
                reservation.getStatus(),
                "EVIDENCE_REGISTERED",
                actor.getId(),
                evidence.getCreatedAt() != null ? evidence.getCreatedAt() : Instant.now(),
                evidenceUrl
        );
    }

    @Transactional
    public InventoryActionResponse addEvidenceFile(
            Long reservationId,
            String type,
            String observation,
            MultipartFile file,
            AuthUserPrincipal principal
    ) {
        String fileUrl = localFileStorageService.saveEvidenceImage(file);
        return addEvidence(
                new EvidenceRequest(
                        reservationId,
                        type,
                        fileUrl,
                        observation
                ),
                principal
        );
    }

    @Transactional(readOnly = true)
    public boolean hasClientHandoffPhoto(Long reservationId) {
        if (reservationId == null) {
            return false;
        }
        return storedItemEvidenceRepository.existsByReservationIdAndTypeIgnoreCase(
                reservationId,
                CLIENT_HANDOFF_PHOTO_EVIDENCE_TYPE
        );
    }

    @Transactional(readOnly = true)
    public int countLuggagePhotos(Long reservationId) {
        if (reservationId == null) {
            return 0;
        }
        return storedItemEvidenceRepository
                .findByReservationIdAndTypeOrderByCreatedAtAsc(reservationId, LUGGAGE_PHOTO_EVIDENCE_TYPE)
                .size();
    }

    private void saveBaggagePhotos(
            Reservation reservation,
            User operator,
            List<MultipartFile> baggagePhotos,
            String observation
    ) {
        if (baggagePhotos == null || baggagePhotos.isEmpty()) {
            return;
        }
        if (storedItemEvidenceRepository.existsByReservationIdAndTypeIgnoreCase(
                reservation.getId(),
                LUGGAGE_PHOTO_EVIDENCE_TYPE
        )) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "LUGGAGE_PHOTOS_ALREADY_REGISTERED",
                    "Las fotos del equipaje ya fueron registradas para esta reserva."
            );
        }
        List<StoredItemEvidence> evidences = new ArrayList<>();
        for (int index = 0; index < baggagePhotos.size(); index++) {
            MultipartFile file = baggagePhotos.get(index);
            String fileUrl = localFileStorageService.saveEvidenceImage(file);
            evidences.add(
                    StoredItemEvidence.luggagePhoto(
                            reservation,
                            operator,
                            publicUrlService.absolute(fileUrl),
                            index + 1,
                            observation
                    )
            );
        }
        storedItemEvidenceRepository.saveAll(evidences);
    }

    private void assertEvidenceTypeAllowed(String type, Reservation reservation, AuthUserPrincipal principal) {
        String normalized = type == null ? "" : type.trim().toUpperCase();
        if (LUGGAGE_PHOTO_EVIDENCE_TYPE.equals(normalized)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "LUGGAGE_PHOTO_MUTATION_FORBIDDEN",
                    "Las fotos del equipaje solo pueden registrarse durante el ingreso al almacen."
            );
        }
        if (CLIENT_HANDOFF_PHOTO_EVIDENCE_TYPE.equals(normalized)) {
            if (!reservation.belongsTo(principal.getId())) {
                throw new ApiException(
                        HttpStatus.FORBIDDEN,
                        "CLIENT_HANDOFF_PHOTO_CLIENT_ONLY",
                        "La foto inicial del equipaje solo puede registrarla el cliente titular de la reserva."
                );
            }
            if (reservation.getStatus() != ReservationStatus.CONFIRMED
                    && reservation.getStatus() != ReservationStatus.CHECKIN_PENDING) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "CLIENT_HANDOFF_PHOTO_OUT_OF_FLOW",
                        "La foto inicial del equipaje solo puede registrarse antes del ingreso a almacen."
                );
            }
        }
    }

    private void assertEvidencePermission(Reservation reservation, AuthUserPrincipal principal) {
        if (reservation.belongsTo(principal.getId())) {
            return;
        }
        if (warehouseAccessService.isAdmin(principal)) {
            return;
        }
        if (warehouseAccessService.isOperatorOrCitySupervisor(principal)
                && warehouseAccessService.canAccessWarehouse(principal, reservation.getWarehouse().getId())) {
            return;
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "EVIDENCE_FORBIDDEN", "No tienes permiso para adjuntar evidencia.");
    }

    private void assertOperationalWarehouseAccess(AuthUserPrincipal principal, Reservation reservation) {
        if (warehouseAccessService.isAdmin(principal)) {
            return;
        }
        if (warehouseAccessService.isOperatorOrCitySupervisor(principal)
                && warehouseAccessService.canAccessWarehouse(principal, reservation.getWarehouse().getId())) {
            return;
        }
        throw new ApiException(
                HttpStatus.FORBIDDEN,
                "WAREHOUSE_SCOPE_FORBIDDEN",
                "No tienes acceso operativo a la sede de esta reserva."
        );
    }

    private User loadUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_INVALID", "Usuario invalido."));
    }

    private void notifyCustomerReservationEvent(
            Reservation reservation,
            String type,
            String title,
            String message
    ) {
        notificationService.notifyUser(
                reservation.getUser().getId(),
                type,
                title,
                message,
                Map.of(
                        "reservationId", reservation.getId(),
                        "warehouseId", reservation.getWarehouse().getId(),
                        "warehouseName", reservation.getWarehouse().getName(),
                        "reservationStatus", reservation.getStatus().name(),
                        "route", "/reservation/" + reservation.getId()
                )
        );
    }

    private void notifyWarehouseAudience(
            Reservation reservation,
            String type,
            String title,
            String message
    ) {
        Long warehouseId = reservation.getWarehouse().getId();
        LinkedHashMap<Long, User> audience = new LinkedHashMap<>();
        List<User> operators = userRepository.findActiveByAnyRoleAndWarehouseId(
                Set.of(Role.OPERATOR, Role.CITY_SUPERVISOR),
                warehouseId
        );
        List<User> couriers = userRepository.findActiveByAnyRoleAndWarehouseId(
                Set.of(Role.COURIER),
                warehouseId
        );
        List<User> admins = userRepository.findActiveByAnyRole(Set.of(Role.ADMIN));
        operators.forEach(user -> audience.put(user.getId(), user));
        couriers.forEach(user -> audience.put(user.getId(), user));
        admins.forEach(user -> audience.put(user.getId(), user));

        for (User user : audience.values()) {
            notificationService.notifyUser(
                    user.getId(),
                    type,
                    title,
                    message,
                    operationalPayload(user, reservation)
            );
        }
    }

    private Map<String, Object> operationalPayload(User user, Reservation reservation) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("reservationId", reservation.getId());
        payload.put("warehouseId", reservation.getWarehouse().getId());
        payload.put("warehouseName", reservation.getWarehouse().getName());
        payload.put("reservationStatus", reservation.getStatus().name());
        if (user.getRoles().contains(Role.ADMIN)) {
            payload.put("route", "/admin/reservations");
        } else if (user.getRoles().contains(Role.COURIER)) {
            payload.put("route", "/courier/services");
        } else {
            payload.put("route", "/operator/reservations");
        }
        return payload;
    }
}
