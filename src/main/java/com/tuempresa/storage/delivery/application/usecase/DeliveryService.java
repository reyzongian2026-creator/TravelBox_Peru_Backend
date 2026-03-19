package com.tuempresa.storage.delivery.application.usecase;

import com.tuempresa.storage.delivery.application.dto.CreateDeliveryOrderRequest;
import com.tuempresa.storage.delivery.application.dto.CourierClaimDeliveryRequest;
import com.tuempresa.storage.delivery.application.dto.CourierTrackingUpdateRequest;
import com.tuempresa.storage.delivery.application.dto.DeliveryMonitorItemResponse;
import com.tuempresa.storage.delivery.application.dto.DeliveryOrderResponse;
import com.tuempresa.storage.delivery.application.dto.DeliveryTrackingEventResponse;
import com.tuempresa.storage.delivery.application.dto.DeliveryTrackingResponse;
import com.tuempresa.storage.delivery.domain.DeliveryOrder;
import com.tuempresa.storage.delivery.domain.DeliveryStatus;
import com.tuempresa.storage.delivery.domain.DeliveryTrackingEvent;
import com.tuempresa.storage.notifications.application.usecase.NotificationService;
import com.tuempresa.storage.geo.application.dto.RoutePointResponse;
import com.tuempresa.storage.geo.application.dto.RouteResponse;
import com.tuempresa.storage.geo.application.usecase.GeoRoutingService;
import com.tuempresa.storage.delivery.infrastructure.out.persistence.DeliveryOrderRepository;
import com.tuempresa.storage.delivery.infrastructure.out.persistence.DeliveryTrackingEventRepository;
import com.tuempresa.storage.reservations.application.usecase.ReservationService;
import com.tuempresa.storage.reservations.domain.Reservation;
import com.tuempresa.storage.reservations.domain.ReservationStatus;
import com.tuempresa.storage.shared.domain.exception.ApiException;
import com.tuempresa.storage.shared.infrastructure.security.AuthUserPrincipal;
import com.tuempresa.storage.shared.infrastructure.security.WarehouseAccessService;
import com.tuempresa.storage.users.domain.Role;
import com.tuempresa.storage.users.domain.User;
import com.tuempresa.storage.users.infrastructure.out.persistence.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class DeliveryService {

    private static final Collection<DeliveryStatus> ACTIVE_ORDER_STATUSES = List.of(
            DeliveryStatus.REQUESTED,
            DeliveryStatus.ASSIGNED,
            DeliveryStatus.IN_TRANSIT
    );

    private final DeliveryOrderRepository deliveryOrderRepository;
    private final DeliveryTrackingEventRepository deliveryTrackingEventRepository;
    private final GeoRoutingService geoRoutingService;
    private final ReservationService reservationService;
    private final UserRepository userRepository;
    private final WarehouseAccessService warehouseAccessService;
    private final NotificationService notificationService;

    public DeliveryService(
            DeliveryOrderRepository deliveryOrderRepository,
            DeliveryTrackingEventRepository deliveryTrackingEventRepository,
            GeoRoutingService geoRoutingService,
            ReservationService reservationService,
            UserRepository userRepository,
            WarehouseAccessService warehouseAccessService,
            NotificationService notificationService
    ) {
        this.deliveryOrderRepository = deliveryOrderRepository;
        this.deliveryTrackingEventRepository = deliveryTrackingEventRepository;
        this.geoRoutingService = geoRoutingService;
        this.reservationService = reservationService;
        this.userRepository = userRepository;
        this.warehouseAccessService = warehouseAccessService;
        this.notificationService = notificationService;
    }

    @Transactional
    public DeliveryOrderResponse create(CreateDeliveryOrderRequest request, AuthUserPrincipal principal) {
        Reservation reservation = reservationService.requireReservation(request.reservationId());
        assertReservationAccess(reservation, principal);
        if ((request.latitude() == null) != (request.longitude() == null)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "DELIVERY_COORDS_INCOMPLETE",
                    "Debes enviar latitud y longitud juntas para fijar punto de servicio."
            );
        }
        String normalizedType = request.type() == null ? "" : request.type().trim().toUpperCase(Locale.ROOT);
        if (!normalizedType.equals("DELIVERY") && !normalizedType.equals("PICKUP")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DELIVERY_TYPE_INVALID", "Solo se admite DELIVERY o PICKUP.");
        }
        if (deliveryOrderRepository.existsByReservationIdAndTypeIgnoreCaseAndStatusIn(
                reservation.getId(),
                normalizedType,
                ACTIVE_ORDER_STATUSES
        )) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "DELIVERY_ALREADY_ACTIVE",
                    normalizedType.equals("PICKUP")
                            ? "Ya existe un recojo activo para esta reserva."
                            : "Ya existe un delivery activo para esta reserva."
            );
        }
        if (normalizedType.equals("DELIVERY")) {
            if (!reservation.isDropoffRequested()) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "DELIVERY_NOT_REQUESTED_BY_CLIENT",
                        "Esta reserva no tiene delivery solicitado por el cliente."
                );
            }
            if (reservation.getStatus() != ReservationStatus.STORED
                    && reservation.getStatus() != ReservationStatus.READY_FOR_PICKUP) {
                throw new ApiException(HttpStatus.CONFLICT, "DELIVERY_NOT_ALLOWED", "La entrega no esta permitida para el estado actual.");
            }
            if (reservation.getStatus() == ReservationStatus.STORED
                    || reservation.getStatus() == ReservationStatus.READY_FOR_PICKUP) {
                reservation.transitionTo(ReservationStatus.OUT_FOR_DELIVERY);
            }
        } else {
            if (!reservation.isPickupRequested()) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "PICKUP_NOT_REQUESTED_BY_CLIENT",
                        "Esta reserva no tiene recojo solicitado por el cliente."
                );
            }
            if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
                throw new ApiException(HttpStatus.CONFLICT, "PICKUP_NOT_ALLOWED", "El recojo no esta permitido para el estado actual.");
            }
            if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
                reservation.transitionTo(ReservationStatus.CHECKIN_PENDING);
            }
        }
        BigDecimal cost = BigDecimal.valueOf(15.00);
        DeliveryOrder order = DeliveryOrder.create(
                reservation,
                normalizedType,
                request.address(),
                request.zone(),
                cost
        );
        initializeMockTracking(order, request.latitude(), request.longitude());
        DeliveryOrder saved = deliveryOrderRepository.save(order);
        deliveryTrackingEventRepository.save(DeliveryTrackingEvent.of(
                saved,
                0,
                DeliveryStatus.REQUESTED,
                saved.getCurrentLatitude(),
                saved.getCurrentLongitude(),
                saved.getEtaMinutes(),
                "Solicitud registrada. Esperando asignacion."
        ));
        notifyScopedCouriers(saved);
        notifyCustomerDeliveryEvent(
                saved,
                "DELIVERY_ORDER_CREATED",
                saved.getType().equalsIgnoreCase("PICKUP") ? "Recojo solicitado" : "Delivery solicitado",
                saved.getType().equalsIgnoreCase("PICKUP")
                        ? "Tu solicitud de recojo para la reserva " + saved.getReservation().getQrCode() + " fue registrada."
                        : "Tu solicitud de entrega para la reserva " + saved.getReservation().getQrCode() + " fue registrada."
        );
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public DeliveryOrderResponse findById(Long id, AuthUserPrincipal principal) {
        DeliveryOrder order = requireOrder(id);
        assertOrderAccess(order, principal);
        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public List<DeliveryMonitorItemResponse> list(
            AuthUserPrincipal principal,
            boolean activeOnly,
            String query,
            String scope
    ) {
        Collection<DeliveryStatus> activeStatuses = ACTIVE_ORDER_STATUSES;
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<DeliveryOrder> orders;
        if (warehouseAccessService.isAdmin(principal)) {
            orders = activeOnly
                    ? deliveryOrderRepository.findByStatusInOrderByUpdatedAtDesc(activeStatuses)
                    : deliveryOrderRepository.findAllByOrderByUpdatedAtDesc();
        } else if (warehouseAccessService.isOperatorOrCitySupervisor(principal)) {
            java.util.Set<Long> warehouseIds = warehouseAccessService.assignedWarehouseIds(principal);
            if (warehouseIds.isEmpty()) {
                return List.of();
            }
            orders = activeOnly
                    ? deliveryOrderRepository.findByReservationWarehouseIdInAndStatusInOrderByUpdatedAtDesc(warehouseIds, activeStatuses)
                    : deliveryOrderRepository.findByReservationWarehouseIdInOrderByUpdatedAtDesc(warehouseIds);
        } else if (isCourier(principal)) {
            boolean availableScope = "available".equalsIgnoreCase(scope);
            java.util.Set<Long> warehouseIds = warehouseAccessService.assignedWarehouseIds(principal);
            if (warehouseIds.isEmpty() && availableScope) {
                return List.of();
            }
            orders = availableScope
                    ? (activeOnly
                        ? deliveryOrderRepository.findByAssignedCourierIsNullAndReservationWarehouseIdInAndStatusInOrderByUpdatedAtDesc(warehouseIds, activeStatuses)
                        : deliveryOrderRepository.findByAssignedCourierIsNullAndReservationWarehouseIdInOrderByUpdatedAtDesc(warehouseIds))
                    : (activeOnly
                        ? deliveryOrderRepository.findByAssignedCourierIdAndStatusInOrderByUpdatedAtDesc(principal.getId(), activeStatuses)
                        : deliveryOrderRepository.findByAssignedCourierIdOrderByUpdatedAtDesc(principal.getId()));
        } else {
            throw new ApiException(HttpStatus.FORBIDDEN, "DELIVERY_MONITOR_FORBIDDEN", "No puedes acceder al monitoreo logistico.");
        }
        return orders.stream()
                .filter(order -> normalizedQuery.isEmpty() || matchesQuery(order, normalizedQuery))
                .map(this::toMonitorItem)
                .toList();
    }

    @Transactional
    public DeliveryTrackingResponse tracking(Long deliveryOrderId, AuthUserPrincipal principal) {
        DeliveryOrder order = requireOrder(deliveryOrderId);
        assertOrderAccess(order, principal);
        advanceIfNeeded(order);
        return toTrackingResponse(order);
    }

    @Transactional
    public DeliveryTrackingResponse trackingByReservation(Long reservationId, AuthUserPrincipal principal) {
        DeliveryOrder order = deliveryOrderRepository.findFirstByReservationIdOrderByCreatedAtDesc(reservationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DELIVERY_NOT_FOUND", "Orden de delivery no encontrada."));
        assertOrderAccess(order, principal);
        advanceIfNeeded(order);
        return toTrackingResponse(order);
    }

    @Transactional
    public DeliveryOrderResponse claim(
            Long deliveryOrderId,
            CourierClaimDeliveryRequest request,
            AuthUserPrincipal principal
    ) {
        if (!isCourier(principal) && !warehouseAccessService.isAdmin(principal) && !warehouseAccessService.isOperatorOrCitySupervisor(principal)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "DELIVERY_CLAIM_FORBIDDEN", "No puedes tomar este servicio.");
        }
        DeliveryOrder order = requireOrder(deliveryOrderId);
        assertWarehouseScope(order, principal);
        User courier = loadUser(principal.getId());
        if (order.getAssignedCourier() != null
                && !order.getAssignedCourier().getId().equals(courier.getId())
                && !warehouseAccessService.isAdmin(principal)
                && !warehouseAccessService.isOperatorOrCitySupervisor(principal)) {
            throw new ApiException(HttpStatus.CONFLICT, "DELIVERY_ALREADY_ASSIGNED", "Este servicio ya fue asignado a otro courier.");
        }

        order.assignCourier(courier);
        order.updateVehicle(request.vehicleType(), request.vehiclePlate());
        order.updateDriverPhone(courier.getPhone());

        if (order.getStatus() == DeliveryStatus.REQUESTED) {
            double latitude = safeCoordinate(order.getCurrentLatitude());
            double longitude = safeCoordinate(order.getCurrentLongitude());
            int etaMinutes = order.getEtaMinutes() == null ? 15 : order.getEtaMinutes();
            order.advanceTracking(
                    latitude,
                    longitude,
                    DeliveryStatus.ASSIGNED,
                    etaMinutes,
                    Instant.now().plusSeconds(20)
            );
            deliveryTrackingEventRepository.save(DeliveryTrackingEvent.of(
                    order,
                    nextSequence(order.getId()),
                    DeliveryStatus.ASSIGNED,
                    latitude,
                    longitude,
                    etaMinutes,
                    "Courier asignado y servicio aceptado."
            ));
        }
        notifyCustomerDeliveryEvent(
                order,
                "DELIVERY_COURIER_ASSIGNED",
                "Courier asignado",
                "El servicio de la reserva " + order.getReservation().getQrCode() + " ya fue tomado por courier."
        );
        notifyOperationalDeliveryEvent(
                order,
                "DELIVERY_COURIER_ASSIGNED_FOR_WAREHOUSE",
                "Courier asignado al servicio",
                "El servicio de la reserva " + order.getReservation().getQrCode() + " ya fue asignado."
        );
        return toResponse(order);
    }

    @Transactional
    public DeliveryTrackingResponse updateProgress(
            Long deliveryOrderId,
            CourierTrackingUpdateRequest request,
            AuthUserPrincipal principal
    ) {
        DeliveryOrder order = requireOrder(deliveryOrderId);
        assertCourierAccess(order, principal);
        DeliveryStatus targetStatus = parseStatus(request.status());
        if (!isValidCourierTransition(order.getStatus(), targetStatus)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "DELIVERY_STATUS_INVALID",
                    "No se puede pasar de " + order.getStatus() + " a " + targetStatus + "."
            );
        }

        if (order.getAssignedCourier() == null) {
            order.assignCourier(loadUser(principal.getId()));
        }
        order.updateVehicle(request.vehicleType(), request.vehiclePlate());

        double latitude = request.latitude() != null ? request.latitude() : safeCoordinate(order.getCurrentLatitude());
        double longitude = request.longitude() != null ? request.longitude() : safeCoordinate(order.getCurrentLongitude());
        int etaMinutes = request.etaMinutes() != null
                ? Math.max(request.etaMinutes(), 0)
                : order.getEtaMinutes() == null ? 0 : order.getEtaMinutes();

        order.advanceTracking(
                latitude,
                longitude,
                targetStatus,
                etaMinutes,
                targetStatus == DeliveryStatus.DELIVERED || targetStatus == DeliveryStatus.CANCELLED
                        ? Instant.now()
                        : Instant.now().plusSeconds(20)
        );

        if (targetStatus == DeliveryStatus.DELIVERED) {
            applyReservationCompletion(order);
        }

        deliveryTrackingEventRepository.save(DeliveryTrackingEvent.of(
                order,
                nextSequence(order.getId()),
                targetStatus,
                latitude,
                longitude,
                etaMinutes,
                resolveCourierMessage(order, targetStatus, request.message())
        ));
        notifyCustomerDeliveryEvent(
                order,
                "DELIVERY_TRACKING_UPDATED",
                deliveryTitle(order, targetStatus),
                deliveryCustomerMessage(order, targetStatus, request.message())
        );
        notifyOperationalDeliveryEvent(
                order,
                "DELIVERY_TRACKING_UPDATED_FOR_WAREHOUSE",
                deliveryTitle(order, targetStatus),
                deliveryOperationalMessage(order, targetStatus)
        );
        return toTrackingResponse(order);
    }

    private DeliveryOrder requireOrder(Long id) {
        return deliveryOrderRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DELIVERY_NOT_FOUND", "Orden de delivery no encontrada."));
    }

    private void assertReservationAccess(Reservation reservation, AuthUserPrincipal principal) {
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
        throw new ApiException(HttpStatus.FORBIDDEN, "DELIVERY_FORBIDDEN", "No puedes acceder a este delivery.");
    }

    private void assertOrderAccess(DeliveryOrder order, AuthUserPrincipal principal) {
        Reservation reservation = order.getReservation();
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
        if (isCourier(principal)
                && order.getAssignedCourier() != null
                && order.getAssignedCourier().getId().equals(principal.getId())
                && warehouseAccessService.canAccessWarehouse(principal, reservation.getWarehouse().getId())) {
            return;
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "DELIVERY_FORBIDDEN", "No puedes acceder a este delivery.");
    }

    private boolean isCourier(AuthUserPrincipal principal) {
        return principal.roleNames().contains(Role.COURIER.name());
    }

    private void initializeMockTracking(DeliveryOrder order, Double requestedLatitude, Double requestedLongitude) {
        Reservation reservation = order.getReservation();
        double originLatitude = reservation.getWarehouse().getLatitude();
        double originLongitude = reservation.getWarehouse().getLongitude();
        double[] destination = buildMockDestination(originLatitude, originLongitude, order.getAddress());
        boolean hasRequestedDestination = requestedLatitude != null && requestedLongitude != null;
        double targetLatitude = hasRequestedDestination ? requestedLatitude : destination[0];
        double targetLongitude = hasRequestedDestination ? requestedLongitude : destination[1];
        int initialEtaMinutes = estimateEtaMinutes(
                originLatitude,
                originLongitude,
                targetLatitude,
                targetLongitude
        );
        order.configureMockTracking(
                "Operador " + reservation.getWarehouse().getCity().getName(),
                "+51991" + String.format("%05d", Math.abs(order.getAddress().hashCode()) % 100000),
                vehicleType(order.getType()),
                "TBX-" + String.format("%04d", Math.abs(order.getAddress().hashCode()) % 10000),
                originLatitude,
                originLongitude,
                targetLatitude,
                targetLongitude,
                initialEtaMinutes
        );
    }

    private void advanceIfNeeded(DeliveryOrder order) {
        if (order.getAssignedCourier() != null) {
            return;
        }
        if (order.getStatus() == DeliveryStatus.DELIVERED || order.getStatus() == DeliveryStatus.CANCELLED) {
            return;
        }
        int nextStage = Math.min(order.getTrackingStage() + 1, 3);
        double progress = switch (nextStage) {
            case 1 -> 0.25;
            case 2 -> 0.7;
            default -> 1.0;
        };
        DeliveryStatus nextStatus = switch (nextStage) {
            case 1 -> DeliveryStatus.ASSIGNED;
            case 2 -> DeliveryStatus.IN_TRANSIT;
            default -> DeliveryStatus.DELIVERED;
        };
        double[] nextCoordinates = resolveMockProgressCoordinates(order, progress);
        double latitude = nextCoordinates[0];
        double longitude = nextCoordinates[1];
        int etaMinutes = nextStatus == DeliveryStatus.DELIVERED
                ? 0
                : estimateEtaMinutes(
                        latitude,
                        longitude,
                        safeCoordinate(order.getDestinationLatitude()),
                        safeCoordinate(order.getDestinationLongitude())
                );
        order.advanceTracking(latitude, longitude, nextStatus, etaMinutes, Instant.now().plusSeconds(10));
        if (nextStatus == DeliveryStatus.DELIVERED) {
            applyReservationCompletion(order);
        }
        deliveryTrackingEventRepository.save(DeliveryTrackingEvent.of(
                order,
                nextStage,
                nextStatus,
                latitude,
                longitude,
                etaMinutes,
                trackingMessage(order, nextStatus)
        ));
    }

    private DeliveryTrackingResponse toTrackingResponse(DeliveryOrder order) {
        List<DeliveryTrackingEventResponse> events = deliveryTrackingEventRepository
                .findByDeliveryOrderIdOrderBySequenceNumberAsc(order.getId())
                .stream()
                .map(event -> new DeliveryTrackingEventResponse(
                        event.getSequenceNumber(),
                        event.getStatus(),
                        event.getLatitude(),
                        event.getLongitude(),
                        event.getEtaMinutes(),
                        event.getMessage(),
                        event.getCreatedAt()
                ))
                .toList();

        return new DeliveryTrackingResponse(
                order.getId(),
                order.getReservation().getId(),
                order.getStatus(),
                order.getDriverName(),
                order.getDriverPhone(),
                order.getVehicleType(),
                order.getVehiclePlate(),
                safeCoordinate(order.getCurrentLatitude()),
                safeCoordinate(order.getCurrentLongitude()),
                safeCoordinate(order.getDestinationLatitude()),
                safeCoordinate(order.getDestinationLongitude()),
                order.getEtaMinutes(),
                order.getAssignedCourier() == null ? "route-estimated" : "courier-manual",
                order.getStatus() != DeliveryStatus.DELIVERED,
                order.getUpdatedAt(),
                events
        );
    }

    private DeliveryMonitorItemResponse toMonitorItem(DeliveryOrder order) {
        Reservation reservation = order.getReservation();
        return new DeliveryMonitorItemResponse(
                order.getId(),
                reservation.getId(),
                reservation.getQrCode(),
                order.getType(),
                reservation.getStatus(),
                order.getStatus(),
                reservation.getWarehouse().getName(),
                reservation.getWarehouse().getCity().getName(),
                reservation.getUser().getFullName(),
                reservation.getUser().getEmail(),
                order.getAddress(),
                order.getZone(),
                order.getAssignedCourier() != null ? order.getAssignedCourier().getId() : null,
                order.getDriverName(),
                order.getDriverPhone(),
                order.getVehicleType(),
                order.getVehiclePlate(),
                safeCoordinate(order.getCurrentLatitude()),
                safeCoordinate(order.getCurrentLongitude()),
                safeCoordinate(order.getDestinationLatitude()),
                safeCoordinate(order.getDestinationLongitude()),
                order.getEtaMinutes(),
                order.getUpdatedAt()
        );
    }

    private DeliveryOrderResponse toResponse(DeliveryOrder order) {
        return new DeliveryOrderResponse(
                order.getId(),
                order.getReservation().getId(),
                order.getType(),
                order.getAddress(),
                order.getZone(),
                order.getStatus(),
                order.getCost(),
                order.getAssignedCourier() != null ? order.getAssignedCourier().getId() : null,
                order.getDriverName(),
                order.getDriverPhone(),
                order.getVehicleType(),
                order.getVehiclePlate(),
                order.getEtaMinutes(),
                order.getCreatedAt()
        );
    }

    private double[] buildMockDestination(double originLatitude, double originLongitude, String address) {
        int hash = Math.abs(address == null ? 0 : address.hashCode());
        double latOffset = 0.008 + (hash % 18) / 1000.0;
        double lngOffset = 0.006 + ((hash / 17) % 16) / 1000.0;
        double targetLatitude = originLatitude + ((hash % 2 == 0) ? latOffset : -latOffset);
        double targetLongitude = originLongitude + ((hash % 3 == 0) ? lngOffset : -lngOffset);
        return new double[]{targetLatitude, targetLongitude};
    }

    private String vehicleType(String requestType) {
        String normalized = requestType == null ? "" : requestType.trim().toUpperCase(Locale.ROOT);
        if (normalized.contains("BUS")) {
            return "BUS";
        }
        if (normalized.contains("AUTO")) {
            return "AUTO";
        }
        return "MOTO";
    }

    private String trackingMessage(DeliveryOrder order, DeliveryStatus status) {
        return switch (status) {
            case REQUESTED -> "Solicitud recibida.";
            case ASSIGNED -> "Unidad asignada y en camino.";
            case IN_TRANSIT -> order.getType().equalsIgnoreCase("PICKUP")
                    ? "Courier en ruta para recoger el equipaje."
                    : "Unidad en ruta hacia el destino.";
            case DELIVERED -> order.getType().equalsIgnoreCase("PICKUP")
                    ? "Equipaje recogido y entregado al almacen."
                    : "Equipaje entregado.";
            case CANCELLED -> "Servicio cancelado.";
        };
    }

    private double interpolate(Double origin, Double destination, double progress) {
        double start = safeCoordinate(origin);
        double end = safeCoordinate(destination);
        return start + ((end - start) * progress);
    }

    private double[] resolveMockProgressCoordinates(DeliveryOrder order, double progress) {
        double originLatitude = order.getReservation().getWarehouse().getLatitude();
        double originLongitude = order.getReservation().getWarehouse().getLongitude();
        double destinationLatitude = safeCoordinate(order.getDestinationLatitude());
        double destinationLongitude = safeCoordinate(order.getDestinationLongitude());

        try {
            RouteResponse route = geoRoutingService.route(
                    originLatitude,
                    originLongitude,
                    destinationLatitude,
                    destinationLongitude,
                    "driving"
            );
            List<RoutePointResponse> points = route.points();
            if (points != null && !points.isEmpty()) {
                int targetIndex = Math.max(
                        0,
                        Math.min((int) Math.round((points.size() - 1) * progress), points.size() - 1)
                );
                RoutePointResponse point = points.get(targetIndex);
                return new double[]{point.latitude(), point.longitude()};
            }
        } catch (Exception ignored) {
        }

        return new double[]{
                interpolate(originLatitude, destinationLatitude, progress),
                interpolate(originLongitude, destinationLongitude, progress)
        };
    }

    private int estimateEtaMinutes(
            double originLatitude,
            double originLongitude,
            double destinationLatitude,
            double destinationLongitude
    ) {
        try {
            RouteResponse route = geoRoutingService.route(
                    originLatitude,
                    originLongitude,
                    destinationLatitude,
                    destinationLongitude,
                    "driving"
            );
            int etaFromRoute = (int) Math.ceil(Math.max(route.durationSeconds(), 0) / 60.0);
            if (etaFromRoute > 0) {
                return etaFromRoute;
            }
        } catch (Exception ignored) {
        }

        double distanceMeters = Math.sqrt(
                Math.pow(destinationLatitude - originLatitude, 2)
                        + Math.pow(destinationLongitude - originLongitude, 2)
        ) * 111_000;
        int fallbackEta = (int) Math.ceil(Math.max(distanceMeters, 0) / 450.0);
        return Math.max(fallbackEta, 3);
    }

    private double safeCoordinate(Double value) {
        return value == null ? 0.0 : value;
    }

    private boolean matchesQuery(DeliveryOrder order, String query) {
        Reservation reservation = order.getReservation();
        return contains(reservation.getQrCode(), query)
                || contains(String.valueOf(order.getId()), query)
                || contains(String.valueOf(reservation.getId()), query)
                || contains(order.getType(), query)
                || contains(reservation.getWarehouse().getName(), query)
                || contains(reservation.getWarehouse().getCity().getName(), query)
                || contains(reservation.getUser().getFullName(), query)
                || contains(reservation.getUser().getEmail(), query)
                || contains(order.getDriverName(), query)
                || contains(order.getVehiclePlate(), query)
                || contains(order.getAddress(), query)
                || contains(order.getZone(), query);
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private void assertCourierAccess(DeliveryOrder order, AuthUserPrincipal principal) {
        if (warehouseAccessService.isAdmin(principal) || warehouseAccessService.isOperatorOrCitySupervisor(principal)) {
            return;
        }
        if (!isCourier(principal)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "DELIVERY_COURIER_FORBIDDEN", "No puedes operar este servicio.");
        }
        assertWarehouseScope(order, principal);
        if (order.getAssignedCourier() == null || !order.getAssignedCourier().getId().equals(principal.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "DELIVERY_NOT_ASSIGNED", "Este servicio no esta asignado a tu usuario.");
        }
    }

    private void assertWarehouseScope(DeliveryOrder order, AuthUserPrincipal principal) {
        if (warehouseAccessService.isAdmin(principal)) {
            return;
        }
        if (!warehouseAccessService.canAccessWarehouse(principal, order.getReservation().getWarehouse().getId())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "WAREHOUSE_SCOPE_FORBIDDEN",
                    "No tienes acceso a la sede asociada a este servicio."
            );
        }
    }

    private void notifyScopedCouriers(DeliveryOrder order) {
        Long warehouseId = order.getReservation().getWarehouse().getId();
        List<User> couriers = userRepository.findActiveByAnyRoleAndWarehouseId(
                Set.of(Role.COURIER),
                warehouseId
        );
        List<User> operators = userRepository.findActiveByAnyRoleAndWarehouseId(
                Set.of(Role.OPERATOR, Role.CITY_SUPERVISOR),
                warehouseId
        );
        List<User> admins = userRepository.findActiveByAnyRole(Set.of(Role.ADMIN));
        LinkedHashMap<Long, User> audience = new LinkedHashMap<>();
        couriers.forEach(user -> audience.put(user.getId(), user));
        operators.forEach(user -> audience.put(user.getId(), user));
        admins.forEach(user -> audience.put(user.getId(), user));

        for (User user : audience.values()) {
            String route = routeForDeliveryAudience(user);
            notificationService.notifyUser(
                    user.getId(),
                    "DELIVERY_ORDER_AVAILABLE",
                    "Nuevo servicio disponible",
                    "Hay un nuevo servicio disponible en tu sede.",
                    deliveryPayload(order, route)
            );
        }
    }

    private DeliveryStatus parseStatus(String rawValue) {
        try {
            return DeliveryStatus.valueOf(rawValue.trim().toUpperCase(Locale.ROOT));
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DELIVERY_STATUS_INVALID", "Estado de delivery no soportado.");
        }
    }

    private boolean isValidCourierTransition(DeliveryStatus currentStatus, DeliveryStatus targetStatus) {
        if (currentStatus == targetStatus) {
            return true;
        }
        return switch (currentStatus) {
            case REQUESTED -> targetStatus == DeliveryStatus.ASSIGNED || targetStatus == DeliveryStatus.CANCELLED;
            case ASSIGNED -> targetStatus == DeliveryStatus.IN_TRANSIT || targetStatus == DeliveryStatus.CANCELLED;
            case IN_TRANSIT -> targetStatus == DeliveryStatus.DELIVERED || targetStatus == DeliveryStatus.CANCELLED;
            case DELIVERED, CANCELLED -> false;
        };
    }

    private int nextSequence(Long deliveryOrderId) {
        return deliveryTrackingEventRepository.findByDeliveryOrderIdOrderBySequenceNumberAsc(deliveryOrderId).size();
    }

    private String resolveCourierMessage(DeliveryOrder order, DeliveryStatus status, String message) {
        if (message != null && !message.isBlank()) {
            return message.trim();
        }
        return switch (status) {
            case REQUESTED -> "Solicitud registrada.";
            case ASSIGNED -> "Courier confirmado para el servicio.";
            case IN_TRANSIT -> order.getType().equalsIgnoreCase("PICKUP")
                    ? "Courier en ruta para recoger el equipaje."
                    : "Courier en ruta hacia el destino.";
            case DELIVERED -> order.getType().equalsIgnoreCase("PICKUP")
                    ? "Equipaje recogido y entregado al almacen."
                    : "Equipaje entregado al cliente.";
            case CANCELLED -> "Servicio cancelado por courier u operacion.";
        };
    }

    private void applyReservationCompletion(DeliveryOrder order) {
        Reservation reservation = order.getReservation();
        if (order.getType().equalsIgnoreCase("PICKUP")) {
            if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
                reservation.transitionTo(ReservationStatus.CHECKIN_PENDING);
            }
            if (reservation.getStatus() == ReservationStatus.CHECKIN_PENDING) {
                reservation.transitionTo(ReservationStatus.STORED);
            }
            return;
        }
        if (reservation.getStatus() == ReservationStatus.OUT_FOR_DELIVERY) {
            reservation.transitionTo(ReservationStatus.COMPLETED);
        }
    }

    private User loadUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_INVALID", "Usuario invalido."));
    }

    private void notifyCustomerDeliveryEvent(
            DeliveryOrder order,
            String type,
            String title,
            String message
    ) {
        notificationService.notifyUser(
                order.getReservation().getUser().getId(),
                type,
                title,
                message,
                deliveryPayload(order, "/tracking/" + order.getReservation().getId())
        );
    }

    private void notifyOperationalDeliveryEvent(
            DeliveryOrder order,
            String type,
            String title,
            String message
    ) {
        Long warehouseId = order.getReservation().getWarehouse().getId();
        LinkedHashMap<Long, User> audience = new LinkedHashMap<>();
        userRepository.findActiveByAnyRoleAndWarehouseId(Set.of(Role.COURIER), warehouseId)
                .forEach(user -> audience.put(user.getId(), user));
        userRepository.findActiveByAnyRoleAndWarehouseId(Set.of(Role.OPERATOR, Role.CITY_SUPERVISOR), warehouseId)
                .forEach(user -> audience.put(user.getId(), user));
        userRepository.findActiveByAnyRole(Set.of(Role.ADMIN))
                .forEach(user -> audience.put(user.getId(), user));

        for (User user : audience.values()) {
            notificationService.notifyUser(
                    user.getId(),
                    type,
                    title,
                    message,
                    deliveryPayload(order, routeForDeliveryAudience(user))
            );
        }
    }

    private Map<String, Object> deliveryPayload(DeliveryOrder order, String route) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("deliveryOrderId", order.getId());
        payload.put("reservationId", order.getReservation().getId());
        payload.put("warehouseId", order.getReservation().getWarehouse().getId());
        payload.put("warehouseName", order.getReservation().getWarehouse().getName());
        payload.put("deliveryStatus", order.getStatus().name());
        payload.put("deliveryType", order.getType());
        payload.put("route", route);
        if (order.getAssignedCourier() != null) {
            payload.put("assignedCourierId", order.getAssignedCourier().getId());
        }
        return payload;
    }

    private String routeForDeliveryAudience(User user) {
        if (user.getRoles().contains(Role.ADMIN)) {
            return "/admin/tracking";
        }
        if (user.getRoles().contains(Role.COURIER)) {
            return "/courier/services";
        }
        return "/operator/tracking";
    }

    private String deliveryTitle(DeliveryOrder order, DeliveryStatus status) {
        String subject = order.getType().equalsIgnoreCase("PICKUP") ? "recojo" : "delivery";
        return switch (status) {
            case REQUESTED -> "Solicitud de " + subject + " registrada";
            case ASSIGNED -> "Courier asignado";
            case IN_TRANSIT -> subject.equals("recojo") ? "Courier en camino al recojo" : "Courier en camino a la entrega";
            case DELIVERED -> subject.equals("recojo") ? "Recojo completado" : "Entrega completada";
            case CANCELLED -> "Servicio cancelado";
        };
    }

    private String deliveryCustomerMessage(DeliveryOrder order, DeliveryStatus status, String rawMessage) {
        if (rawMessage != null && !rawMessage.isBlank()) {
            return rawMessage.trim();
        }
        return switch (status) {
            case REQUESTED -> "Tu servicio fue registrado correctamente.";
            case ASSIGNED -> "Tu servicio ya cuenta con courier asignado.";
            case IN_TRANSIT -> order.getType().equalsIgnoreCase("PICKUP")
                    ? "El courier va en camino para recoger tu equipaje."
                    : "El courier va en camino para entregar tu equipaje.";
            case DELIVERED -> order.getType().equalsIgnoreCase("PICKUP")
                    ? "Tu equipaje fue recogido y registrado hacia almacen."
                    : "Tu equipaje fue entregado correctamente.";
            case CANCELLED -> "Tu servicio fue cancelado.";
        };
    }

    private String deliveryOperationalMessage(DeliveryOrder order, DeliveryStatus status) {
        return switch (status) {
            case REQUESTED -> "Se registro un nuevo servicio para la reserva " + order.getReservation().getQrCode() + ".";
            case ASSIGNED -> "El servicio de la reserva " + order.getReservation().getQrCode() + " ya tiene courier asignado.";
            case IN_TRANSIT -> "El servicio de la reserva " + order.getReservation().getQrCode() + " ya esta en ruta.";
            case DELIVERED -> "El servicio de la reserva " + order.getReservation().getQrCode() + " fue completado.";
            case CANCELLED -> "El servicio de la reserva " + order.getReservation().getQrCode() + " fue cancelado.";
        };
    }
}
