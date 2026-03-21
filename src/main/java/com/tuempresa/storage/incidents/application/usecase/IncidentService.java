package com.tuempresa.storage.incidents.application.usecase;

import com.tuempresa.storage.ops.application.usecase.OpsMessageTranslationService;
import com.tuempresa.storage.incidents.application.dto.CreateIncidentRequest;
import com.tuempresa.storage.incidents.application.dto.IncidentResponse;
import com.tuempresa.storage.incidents.application.dto.IncidentSummaryResponse;
import com.tuempresa.storage.incidents.application.dto.ResolveIncidentRequest;
import com.tuempresa.storage.incidents.domain.Incident;
import com.tuempresa.storage.incidents.domain.IncidentStatus;
import com.tuempresa.storage.incidents.infrastructure.out.persistence.IncidentRepository;
import com.tuempresa.storage.notifications.application.usecase.NotificationService;
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
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class IncidentService {

    private static final int MAX_PAGE_SIZE = 100;

    private final IncidentRepository incidentRepository;
    private final ReservationService reservationService;
    private final UserRepository userRepository;
    private final WarehouseAccessService warehouseAccessService;
    private final NotificationService notificationService;
    private final OpsMessageTranslationService opsMessageTranslationService;

    public IncidentService(
            IncidentRepository incidentRepository,
            ReservationService reservationService,
            UserRepository userRepository,
            WarehouseAccessService warehouseAccessService,
            NotificationService notificationService,
            OpsMessageTranslationService opsMessageTranslationService
    ) {
        this.incidentRepository = incidentRepository;
        this.reservationService = reservationService;
        this.userRepository = userRepository;
        this.warehouseAccessService = warehouseAccessService;
        this.notificationService = notificationService;
        this.opsMessageTranslationService = opsMessageTranslationService;
    }

    @Transactional(readOnly = true)
    public List<IncidentSummaryResponse> list(AuthUserPrincipal principal, IncidentStatus status, String query) {
        return list(principal, status, query, null);
    }

    @Transactional(readOnly = true)
    public List<IncidentSummaryResponse> list(
            AuthUserPrincipal principal,
            IncidentStatus status,
            String query,
            Long reservationId
    ) {
        return listFiltered(principal, status, query, reservationId);
    }

    @Transactional(readOnly = true)
    public PagedResponse<IncidentSummaryResponse> listPage(
            AuthUserPrincipal principal,
            int page,
            int size,
            IncidentStatus status,
            String query,
            Long reservationId
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = clampSize(size);
        List<IncidentSummaryResponse> filtered = listFiltered(principal, status, query, reservationId);

        long totalElements = filtered.size();
        int totalPages = totalElements == 0
                ? 0
                : (int) Math.ceil(totalElements / (double) safeSize);
        int fromIndex = safePage * safeSize;
        if (fromIndex >= filtered.size()) {
            return new PagedResponse<>(
                    List.of(),
                    safePage,
                    safeSize,
                    totalElements,
                    totalPages,
                    false,
                    safePage > 0 && totalPages > 0
            );
        }

        int toIndex = Math.min(fromIndex + safeSize, filtered.size());
        List<IncidentSummaryResponse> pageItems = filtered.subList(fromIndex, toIndex);
        boolean hasNext = toIndex < filtered.size();
        boolean hasPrevious = safePage > 0;
        return new PagedResponse<>(
                pageItems,
                safePage,
                safeSize,
                totalElements,
                totalPages,
                hasNext,
                hasPrevious
        );
    }

    private List<IncidentSummaryResponse> listFiltered(
            AuthUserPrincipal principal,
            IncidentStatus status,
            String query,
            Long reservationId
    ) {
        boolean admin = warehouseAccessService.isAdmin(principal);
        boolean scopedSupport = warehouseAccessService.isSupport(principal);
        boolean scopedOps = warehouseAccessService.isOperatorOrCitySupervisor(principal);
        boolean warehouseScoped = scopedSupport || scopedOps;
        Set<Long> scopedWarehouseIds = warehouseScoped ? warehouseAccessService.assignedWarehouseIds(principal) : Set.of();
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        Long normalizedReservationId = reservationId != null && reservationId > 0 ? reservationId : null;
        User viewer = loadUser(principal.getId());
        boolean internalViewer = hasPrivilegedRole(principal);

        return incidentRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .filter(incident -> admin
                        || (warehouseScoped && scopedWarehouseIds.contains(incident.getReservation().getWarehouse().getId()))
                        || incident.getReservation().belongsTo(principal.getId())
                        || incident.getOpenedBy().getId().equals(principal.getId()))
                .filter(incident -> normalizedReservationId == null
                        || incident.getReservation().getId().equals(normalizedReservationId))
                .filter(incident -> status == null || incident.getStatus() == status)
                .filter(incident -> normalizedQuery.isEmpty() || matchesQuery(incident, normalizedQuery))
                .map(incident -> toSummary(incident, viewer, internalViewer))
                .toList();
    }

    private int clampSize(int requestedSize) {
        if (requestedSize <= 0) {
            return 20;
        }
        return Math.min(requestedSize, MAX_PAGE_SIZE);
    }

    @Transactional
    public IncidentResponse open(CreateIncidentRequest request, AuthUserPrincipal principal) {
        Reservation reservation = reservationService.requireReservation(request.reservationId());
        User opener = loadUser(principal.getId());
        boolean privileged = hasPrivilegedRole(principal);
        boolean warehouseScopedSupport = warehouseAccessService.isSupport(principal);

        if ((warehouseAccessService.isOperatorOrCitySupervisor(principal) || warehouseScopedSupport)
                && !warehouseAccessService.canAccessWarehouse(principal, reservation.getWarehouse().getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "INCIDENT_FORBIDDEN", "No tienes acceso a la sede de esta reserva.");
        }

        if (!privileged && !reservation.belongsTo(principal.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "INCIDENT_FORBIDDEN", "No tienes permiso sobre esta reserva.");
        }

        if (canOpenOperationalIncident(reservation.getStatus())) {
            reservation.transitionTo(ReservationStatus.INCIDENT);
        }

        Incident incident = incidentRepository.save(Incident.open(reservation, opener, request.description()));
        notifyCustomerIncidentEvent(
                incident,
                "INCIDENT_OPENED",
                "Incidencia registrada",
                "Se registro una incidencia para tu reserva " + reservation.getQrCode() + "."
        );
        notifyIncidentAudience(
                incident,
                "INCIDENT_OPENED_FOR_WAREHOUSE",
                "Nueva incidencia en sede",
                "Se registro una incidencia para la reserva " + reservation.getQrCode() + "."
        );
        return toResponse(incident, opener, hasPrivilegedRole(principal));
    }

    @Transactional
    public IncidentResponse resolve(Long incidentId, ResolveIncidentRequest request, AuthUserPrincipal principal) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "INCIDENT_NOT_FOUND", "Incidencia no encontrada."));
        if (incident.getStatus() == IncidentStatus.RESOLVED) {
            throw new ApiException(HttpStatus.CONFLICT, "INCIDENT_ALREADY_RESOLVED", "La incidencia ya fue resuelta.");
        }
        Reservation reservation = incident.getReservation();
        if (!warehouseAccessService.isAdmin(principal)
                && !warehouseAccessService.canAccessWarehouse(principal, reservation.getWarehouse().getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "INCIDENT_FORBIDDEN", "No tienes acceso a la sede de esta incidencia.");
        }
        User resolver = loadUser(principal.getId());
        incident.resolve(resolver, request.resolution());

        if (reservation.getStatus() == ReservationStatus.INCIDENT) {
            reservation.transitionTo(ReservationStatus.STORED);
        }
        notifyCustomerIncidentEvent(
                incident,
                "INCIDENT_RESOLVED",
                "Incidencia resuelta",
                "La incidencia de tu reserva " + reservation.getQrCode() + " fue resuelta."
        );
        notifyIncidentAudience(
                incident,
                "INCIDENT_RESOLVED_FOR_WAREHOUSE",
                "Incidencia resuelta",
                "La incidencia de la reserva " + reservation.getQrCode() + " fue resuelta."
        );
        return toResponse(incident, resolver, hasPrivilegedRole(principal));
    }

    private User loadUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_INVALID", "Usuario inválido."));
    }

    private IncidentResponse toResponse(Incident incident, User viewer, boolean internalViewer) {
        Reservation reservation = incident.getReservation();
        User customer = reservation.getUser();
        return new IncidentResponse(
                incident.getId(),
                reservation.getId(),
                incident.getStatus(),
                translateIncidentTextForViewer(
                        incident.getDescription(),
                        incident.getOpenedBy(),
                        customer,
                        viewer,
                        internalViewer
                ),
                translateIncidentTextForViewer(
                        incident.getResolution(),
                        incident.getResolvedBy(),
                        customer,
                        viewer,
                        internalViewer
                ),
                incident.getOpenedBy().getId(),
                incident.getResolvedBy() != null ? incident.getResolvedBy().getId() : null,
                incident.getResolvedAt(),
                incident.getCreatedAt()
        );
    }

    private IncidentSummaryResponse toSummary(Incident incident, User viewer, boolean internalViewer) {
        Reservation reservation = incident.getReservation();
        User openedBy = incident.getOpenedBy();
        User customer = reservation.getUser();
        String customerPhone = customer.getPhone();
        return new IncidentSummaryResponse(
                incident.getId(),
                reservation.getId(),
                reservation.getQrCode(),
                reservation.getStatus(),
                reservation.getWarehouse().getName(),
                reservation.getWarehouse().getAddress(),
                openedBy.getId(),
                openedBy.getFullName(),
                openedBy.getEmail(),
                customer.getId(),
                customer.getFullName(),
                customer.getEmail(),
                customerPhone,
                buildWhatsappUrl(customerPhone, reservation.getId()),
                buildCallUrl(customerPhone),
                incident.getStatus(),
                translateIncidentTextForViewer(
                        incident.getDescription(),
                        openedBy,
                        customer,
                        viewer,
                        internalViewer
                ),
                translateIncidentTextForViewer(
                        incident.getResolution(),
                        incident.getResolvedBy(),
                        customer,
                        viewer,
                        internalViewer
                ),
                incident.getCreatedAt(),
                incident.getResolvedAt()
        );
    }

    private boolean canOpenOperationalIncident(ReservationStatus status) {
        return status == ReservationStatus.CHECKIN_PENDING
                || status == ReservationStatus.STORED
                || status == ReservationStatus.READY_FOR_PICKUP
                || status == ReservationStatus.OUT_FOR_DELIVERY;
    }

    private boolean hasPrivilegedRole(AuthUserPrincipal principal) {
        return warehouseAccessService.isAdmin(principal)
                || warehouseAccessService.isSupport(principal)
                || warehouseAccessService.isOperatorOrCitySupervisor(principal);
    }

    private boolean matchesQuery(Incident incident, String query) {
        return contains(incident.getDescription(), query)
                || contains(incident.getResolution(), query)
                || contains(incident.getReservation().getQrCode(), query)
                || contains(incident.getReservation().getWarehouse().getName(), query)
                || contains(incident.getReservation().getWarehouse().getAddress(), query)
                || contains(incident.getOpenedBy().getFullName(), query)
                || contains(incident.getOpenedBy().getEmail(), query);
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private String translateIncidentTextForViewer(
            String text,
            User author,
            User customer,
            User viewer,
            boolean internalViewer
    ) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String sourceLanguage = normalizeLanguage(author == null ? null : author.getPreferredLanguage());
        String targetLanguage = internalViewer
                ? "es"
                : preferredLanguageOrDefault(
                viewer == null ? null : viewer.getPreferredLanguage(),
                customer == null ? null : customer.getPreferredLanguage()
        );
        return opsMessageTranslationService.translate(text, sourceLanguage, targetLanguage);
    }

    private String preferredLanguageOrDefault(String preferredLanguage, String fallbackLanguage) {
        String normalized = normalizeLanguage(preferredLanguage);
        if (normalized != null) {
            return normalized;
        }
        String fallback = normalizeLanguage(fallbackLanguage);
        return fallback == null ? "es" : fallback;
    }

    private String normalizeLanguage(String rawLanguage) {
        if (rawLanguage == null || rawLanguage.isBlank()) {
            return null;
        }
        String normalized = rawLanguage.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() <= 2) {
            return normalized;
        }
        return normalized.substring(0, 2);
    }

    private String buildWhatsappUrl(String phone, Long reservationId) {
        String normalized = normalizePhoneForLink(phone);
        if (normalized == null) {
            return null;
        }
        return "https://wa.me/" + normalized + "?text=Hola%20TravelBox%2C%20necesito%20soporte%20de%20la%20reserva%20" + reservationId;
    }

    private String buildCallUrl(String phone) {
        String normalized = normalizePhoneForLink(phone);
        if (normalized == null) {
            return null;
        }
        return "tel:+" + normalized;
    }

    private String normalizePhoneForLink(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String digits = phone.replaceAll("[^0-9]", "");
        return digits.isBlank() ? null : digits;
    }

    private void notifyCustomerIncidentEvent(
            Incident incident,
            String type,
            String title,
            String message
    ) {
        User customer = incident.getReservation().getUser();
        String customerLanguage = preferredLanguageOrDefault(customer.getPreferredLanguage(), "es");
        String localizedTitle = opsMessageTranslationService.translateFromSpanish(title, customerLanguage);
        String localizedMessage = opsMessageTranslationService.translateFromSpanish(message, customerLanguage);
        notificationService.notifyUser(
                customer.getId(),
                type,
                localizedTitle,
                localizedMessage,
                java.util.Map.of(
                        "incidentId", incident.getId(),
                        "reservationId", incident.getReservation().getId(),
                        "warehouseId", incident.getReservation().getWarehouse().getId(),
                        "route", "/incidents?reservationId=" + incident.getReservation().getId()
                )
        );
    }

    private void notifyIncidentAudience(
            Incident incident,
            String type,
            String title,
            String message
    ) {
        Reservation reservation = incident.getReservation();
        Long warehouseId = reservation.getWarehouse().getId();
        LinkedHashMap<Long, User> audience = new LinkedHashMap<>();
        userRepository.findActiveByAnyRoleAndWarehouseId(Set.of(Role.SUPPORT), warehouseId)
                .forEach(user -> audience.put(user.getId(), user));
        userRepository.findActiveByAnyRoleAndWarehouseId(Set.of(Role.OPERATOR, Role.CITY_SUPERVISOR), warehouseId)
                .forEach(user -> audience.put(user.getId(), user));
        userRepository.findActiveByAnyRole(Set.of(Role.ADMIN))
                .forEach(user -> audience.put(user.getId(), user));

        for (User user : audience.values()) {
            String route = user.getRoles().contains(Role.ADMIN)
                    ? "/admin/incidents"
                    : user.getRoles().contains(Role.SUPPORT)
                    ? "/support/incidents"
                    : "/operator/incidents";
            notificationService.notifyUser(
                    user.getId(),
                    type,
                    title,
                    message,
                    java.util.Map.of(
                            "incidentId", incident.getId(),
                            "reservationId", reservation.getId(),
                            "warehouseId", warehouseId,
                            "warehouseName", reservation.getWarehouse().getName(),
                            "route", route
                    )
            );
        }
    }
}
