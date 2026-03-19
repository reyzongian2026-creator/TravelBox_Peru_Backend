package com.tuempresa.storage.incidents.application.usecase;

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

    private final IncidentRepository incidentRepository;
    private final ReservationService reservationService;
    private final UserRepository userRepository;
    private final WarehouseAccessService warehouseAccessService;
    private final NotificationService notificationService;

    public IncidentService(
            IncidentRepository incidentRepository,
            ReservationService reservationService,
            UserRepository userRepository,
            WarehouseAccessService warehouseAccessService,
            NotificationService notificationService
    ) {
        this.incidentRepository = incidentRepository;
        this.reservationService = reservationService;
        this.userRepository = userRepository;
        this.warehouseAccessService = warehouseAccessService;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public List<IncidentSummaryResponse> list(AuthUserPrincipal principal, IncidentStatus status, String query) {
        boolean admin = warehouseAccessService.isAdmin(principal);
        boolean scopedSupport = warehouseAccessService.isSupport(principal);
        boolean scopedOps = warehouseAccessService.isOperatorOrCitySupervisor(principal);
        boolean warehouseScoped = scopedSupport || scopedOps;
        Set<Long> scopedWarehouseIds = warehouseScoped ? warehouseAccessService.assignedWarehouseIds(principal) : Set.of();
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);

        return incidentRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .filter(incident -> admin
                        || (warehouseScoped && scopedWarehouseIds.contains(incident.getReservation().getWarehouse().getId()))
                        || incident.getReservation().belongsTo(principal.getId())
                        || incident.getOpenedBy().getId().equals(principal.getId()))
                .filter(incident -> status == null || incident.getStatus() == status)
                .filter(incident -> normalizedQuery.isEmpty() || matchesQuery(incident, normalizedQuery))
                .map(this::toSummary)
                .toList();
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
        return toResponse(incident);
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
        return toResponse(incident);
    }

    private User loadUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_INVALID", "Usuario inválido."));
    }

    private IncidentResponse toResponse(Incident incident) {
        return new IncidentResponse(
                incident.getId(),
                incident.getReservation().getId(),
                incident.getStatus(),
                incident.getDescription(),
                incident.getResolution(),
                incident.getOpenedBy().getId(),
                incident.getResolvedBy() != null ? incident.getResolvedBy().getId() : null,
                incident.getResolvedAt(),
                incident.getCreatedAt()
        );
    }

    private IncidentSummaryResponse toSummary(Incident incident) {
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
                incident.getDescription(),
                incident.getResolution(),
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
        notificationService.notifyUser(
                incident.getReservation().getUser().getId(),
                type,
                title,
                message,
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
