package com.tuempresa.storage.shared.infrastructure.config;

import com.tuempresa.storage.delivery.domain.DeliveryOrder;
import com.tuempresa.storage.delivery.domain.DeliveryStatus;
import com.tuempresa.storage.delivery.domain.DeliveryTrackingEvent;
import com.tuempresa.storage.delivery.infrastructure.out.persistence.DeliveryOrderRepository;
import com.tuempresa.storage.delivery.infrastructure.out.persistence.DeliveryTrackingEventRepository;
import com.tuempresa.storage.geo.domain.City;
import com.tuempresa.storage.geo.domain.TouristZone;
import com.tuempresa.storage.geo.infrastructure.out.persistence.CityRepository;
import com.tuempresa.storage.geo.infrastructure.out.persistence.TouristZoneRepository;
import com.tuempresa.storage.incidents.domain.Incident;
import com.tuempresa.storage.incidents.infrastructure.out.persistence.IncidentRepository;
import com.tuempresa.storage.payments.domain.PaymentAttempt;
import com.tuempresa.storage.payments.domain.PaymentStatus;
import com.tuempresa.storage.payments.infrastructure.out.persistence.PaymentAttemptRepository;
import com.tuempresa.storage.reservations.domain.Reservation;
import com.tuempresa.storage.reservations.domain.ReservationBagSize;
import com.tuempresa.storage.reservations.domain.ReservationStatus;
import com.tuempresa.storage.reservations.infrastructure.out.persistence.ReservationRepository;
import com.tuempresa.storage.users.domain.Role;
import com.tuempresa.storage.users.domain.User;
import com.tuempresa.storage.users.infrastructure.out.persistence.UserRepository;
import com.tuempresa.storage.warehouses.domain.Warehouse;
import com.tuempresa.storage.warehouses.infrastructure.out.persistence.WarehouseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Configuration
public class BootstrapDataInitializer {

    private static final Logger log = LoggerFactory.getLogger(BootstrapDataInitializer.class);

    @Bean
    CommandLineRunner seedData(
            UserRepository userRepository,
            CityRepository cityRepository,
            TouristZoneRepository touristZoneRepository,
            WarehouseRepository warehouseRepository,
            ReservationRepository reservationRepository,
            PaymentAttemptRepository paymentAttemptRepository,
            IncidentRepository incidentRepository,
            DeliveryOrderRepository deliveryOrderRepository,
            DeliveryTrackingEventRepository deliveryTrackingEventRepository,
            PasswordEncoder passwordEncoder,
            TransactionTemplate transactionTemplate,
            @Value("${app.bootstrap.enabled:true}") boolean bootstrapEnabled,
            @Value("${app.bootstrap.seed-operational-demo:false}") boolean seedOperationalDemoDataEnabled,
            @Value("${app.bootstrap.minimal-seed-only:false}") boolean minimalSeedOnly
    ) {
        return args -> transactionTemplate.executeWithoutResult(status -> {
            if (!bootstrapEnabled) {
                log.info("Bootstrap seed skipped (app.bootstrap.enabled=false)");
                return;
            }
            log.info(
                    "Bootstrap seed started (seedOperationalDemoDataEnabled={}, minimalSeedOnly={})",
                    seedOperationalDemoDataEnabled,
                    minimalSeedOnly
            );
            seedUsers(userRepository, passwordEncoder);
            if (minimalSeedOnly) {
                log.info("Bootstrap minimal seed completed (users={})", userRepository.count());
                return;
            }
            seedGeoAndWarehouses(cityRepository, touristZoneRepository, warehouseRepository);
            seedOperationalWarehouseScopes(userRepository, warehouseRepository);
            seedPerWarehouseOperationalUsers(userRepository, passwordEncoder, warehouseRepository);
            if (seedOperationalDemoDataEnabled) {
                seedOperationalDemoData(
                        userRepository,
                        warehouseRepository,
                        reservationRepository,
                        paymentAttemptRepository,
                        incidentRepository,
                        deliveryOrderRepository,
                        deliveryTrackingEventRepository
                );
            }
            log.info(
                    "Bootstrap seed completed (users={}, warehouses={})",
                    userRepository.count(),
                    warehouseRepository.count()
            );
        });
    }

    private void seedUsers(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        seedUser(
                userRepository,
                passwordEncoder,
                "admin@travelbox.pe",
                "Admin123!",
                "Administrador",
                "TravelBox",
                "+51900000001",
                Set.of(Role.ADMIN, Role.SUPPORT)
        );
        seedUser(
                userRepository,
                passwordEncoder,
                "operator@travelbox.pe",
                "Operator123!",
                "Operador",
                "Principal",
                "+51900000002",
                Set.of(Role.OPERATOR)
        );
        seedUser(
                userRepository,
                passwordEncoder,
                "operator.north@travelbox.pe",
                "Operator123!",
                "Operador",
                "Norte",
                "+51900000008",
                Set.of(Role.OPERATOR)
        );
        seedUser(
                userRepository,
                passwordEncoder,
                "operator.south@travelbox.pe",
                "Operator123!",
                "Operador",
                "Sur",
                "+51900000010",
                Set.of(Role.OPERATOR)
        );
        seedUser(
                userRepository,
                passwordEncoder,
                "operator.molina@travelbox.pe",
                "Operator123!",
                "Operador",
                "Molina",
                "+51900000011",
                Set.of(Role.OPERATOR)
        );
        seedUser(
                userRepository,
                passwordEncoder,
                "operator.demo.multisede@travelbox.pe",
                "Operator123!",
                "Operador",
                "Demo Multisede",
                "+51900000017",
                Set.of(Role.OPERATOR)
        );
        seedUser(
                userRepository,
                passwordEncoder,
                "courier@travelbox.pe",
                "Courier123!",
                "Courier",
                "TravelBox",
                "+51900000007",
                Set.of(Role.COURIER)
        );
        seedUser(
                userRepository,
                passwordEncoder,
                "courier.north@travelbox.pe",
                "Courier123!",
                "Courier",
                "Norte",
                "+51900000009",
                Set.of(Role.COURIER)
        );
        seedUser(
                userRepository,
                passwordEncoder,
                "courier.south@travelbox.pe",
                "Courier123!",
                "Courier",
                "Sur",
                "+51900000012",
                Set.of(Role.COURIER)
        );
        seedUser(
                userRepository,
                passwordEncoder,
                "courier.molina@travelbox.pe",
                "Courier123!",
                "Courier",
                "Molina",
                "+51900000013",
                Set.of(Role.COURIER)
        );
        seedUser(
                userRepository,
                passwordEncoder,
                "courier.demo.multisede@travelbox.pe",
                "Courier123!",
                "Courier",
                "Demo Multisede",
                "+51900000018",
                Set.of(Role.COURIER)
        );
        seedUser(
                userRepository,
                passwordEncoder,
                "support@travelbox.pe",
                "Support123!",
                "Soporte",
                "TravelBox",
                "+51900000006",
                Set.of(Role.SUPPORT)
        );
        seedUser(
                userRepository,
                passwordEncoder,
                "support.north@travelbox.pe",
                "Support123!",
                "Soporte",
                "Norte",
                "+51900000014",
                Set.of(Role.SUPPORT)
        );
        seedUser(
                userRepository,
                passwordEncoder,
                "support.south@travelbox.pe",
                "Support123!",
                "Soporte",
                "Sur",
                "+51900000015",
                Set.of(Role.SUPPORT)
        );
        seedUser(
                userRepository,
                passwordEncoder,
                "support.molina@travelbox.pe",
                "Support123!",
                "Soporte",
                "Molina",
                "+51900000016",
                Set.of(Role.SUPPORT)
        );
        seedUser(
                userRepository,
                passwordEncoder,
                "support.demo.multisede@travelbox.pe",
                "Support123!",
                "Soporte",
                "Demo Multisede",
                "+51900000019",
                Set.of(Role.SUPPORT)
        );
        seedUser(
                userRepository,
                passwordEncoder,
                "supervisor.demo@travelbox.pe",
                "Supervisor123!",
                "Supervisor",
                "Demo",
                "+51900000020",
                Set.of(Role.CITY_SUPERVISOR)
        );
        seedUser(
                userRepository,
                passwordEncoder,
                "client@travelbox.pe",
                "Client123!",
                "Cliente",
                "Demo",
                "+51900000003",
                Set.of(Role.CLIENT)
        );
        seedUser(
                userRepository,
                passwordEncoder,
                "client.cusco@travelbox.pe",
                "Client123!",
                "Valeria",
                "Cusco",
                "+51900000004",
                Set.of(Role.CLIENT)
        );
        seedUser(
                userRepository,
                passwordEncoder,
                "client.north@travelbox.pe",
                "Client123!",
                "Mateo",
                "Norte",
                "+51900000005",
                Set.of(Role.CLIENT)
        );
    }

    private void seedUser(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            String email,
            String rawPassword,
            String firstName,
            String lastName,
            String phone,
            Set<Role> roles
    ) {
        if (userRepository.existsByEmailIgnoreCase(email)) {
            return;
        }
        User user = User.of(
                firstName + " " + lastName,
                email,
                passwordEncoder.encode(rawPassword),
                phone,
                roles
        );
        user.applyRegistrationDetails(firstName, lastName, "Peru", "es", phone, true, null);
        user.markEmailVerified();
        user.markManagedByAdmin(isInternalUser(roles));
        if (roles.contains(Role.COURIER)) {
            user.updateVehiclePlate(seedVehiclePlate(email));
        }
        userRepository.save(user);
    }

    private boolean isInternalUser(Set<Role> roles) {
        return roles.stream().anyMatch(role -> role != Role.CLIENT);
    }

    private String seedVehiclePlate(String email) {
        int suffix = Math.abs(email.hashCode() % 9000) + 1000;
        return "TBX-" + suffix;
    }

    private void seedGeoAndWarehouses(
            CityRepository cityRepository,
            TouristZoneRepository touristZoneRepository,
            WarehouseRepository warehouseRepository
    ) {
        Map<String, Warehouse> existingByName = warehouseRepository.findAll().stream()
                .collect(Collectors.toMap(Warehouse::getName, Function.identity(), (left, right) -> left, LinkedHashMap::new));

        for (WarehouseSeed seed : warehouseSeeds()) {
            if (existingByName.containsKey(seed.warehouseName())) {
                continue;
            }
            City city = cityRepository.findByNameIgnoreCase(seed.cityName())
                    .orElseGet(() -> cityRepository.save(City.of(seed.cityName(), "Peru")));
            TouristZone zone = touristZoneRepository.findByCityIdOrderByNameAsc(city.getId())
                    .stream()
                    .filter(candidate -> candidate.getName().equalsIgnoreCase(seed.zoneName()))
                    .findFirst()
                    .orElseGet(() -> touristZoneRepository.save(TouristZone.of(
                            city,
                            seed.zoneName(),
                            seed.zoneLatitude(),
                            seed.zoneLongitude(),
                            seed.zoneRadiusKm()
                    )));

            warehouseRepository.save(Warehouse.of(
                    city,
                    zone,
                    seed.warehouseName(),
                    seed.address(),
                    seed.latitude(),
                    seed.longitude(),
                    seed.capacity(),
                    seed.openHour(),
                    seed.closeHour(),
                    seed.rules(),
                    Warehouse.DEFAULT_PRICE_SMALL_PER_HOUR,
                    Warehouse.DEFAULT_PRICE_MEDIUM_PER_HOUR,
                    Warehouse.DEFAULT_PRICE_LARGE_PER_HOUR,
                    Warehouse.DEFAULT_PRICE_EXTRA_LARGE_PER_HOUR,
                    Warehouse.DEFAULT_PICKUP_FEE,
                    Warehouse.DEFAULT_DROPOFF_FEE,
                    Warehouse.DEFAULT_INSURANCE_FEE
            ));
        }
    }

    private void seedOperationalWarehouseScopes(
            UserRepository userRepository,
            WarehouseRepository warehouseRepository
    ) {
        Map<String, Warehouse> warehousesByName = warehouseRepository.findByActiveTrueOrderByNameAsc()
                .stream()
                .collect(Collectors.toMap(Warehouse::getName, Function.identity(), (left, right) -> left, LinkedHashMap::new));

        assignWarehouses(
                userRepository,
                "operator@travelbox.pe",
                warehousesByName,
                limaCoreWarehouses()
        );
        assignWarehouses(
                userRepository,
                "operator.north@travelbox.pe",
                warehousesByName,
                northWarehouses()
        );
        assignWarehouses(
                userRepository,
                "operator.south@travelbox.pe",
                warehousesByName,
                southWarehouses()
        );
        assignWarehouses(
                userRepository,
                "operator.molina@travelbox.pe",
                warehousesByName,
                molinaWarehouse()
        );
        assignWarehouses(
                userRepository,
                "operator.demo.multisede@travelbox.pe",
                warehousesByName,
                demoMultiScopeWarehouses()
        );
        assignWarehouses(
                userRepository,
                "courier@travelbox.pe",
                warehousesByName,
                limaCoreWarehouses()
        );
        assignWarehouses(
                userRepository,
                "courier.north@travelbox.pe",
                warehousesByName,
                northWarehouses()
        );
        assignWarehouses(
                userRepository,
                "courier.south@travelbox.pe",
                warehousesByName,
                southWarehouses()
        );
        assignWarehouses(
                userRepository,
                "courier.molina@travelbox.pe",
                warehousesByName,
                molinaWarehouse()
        );
        assignWarehouses(
                userRepository,
                "courier.demo.multisede@travelbox.pe",
                warehousesByName,
                demoMultiScopeWarehouses()
        );
        assignWarehouses(
                userRepository,
                "support@travelbox.pe",
                warehousesByName,
                limaSupportWarehouses()
        );
        assignWarehouses(
                userRepository,
                "support.north@travelbox.pe",
                warehousesByName,
                northWarehouses()
        );
        assignWarehouses(
                userRepository,
                "support.south@travelbox.pe",
                warehousesByName,
                southWarehouses()
        );
        assignWarehouses(
                userRepository,
                "support.molina@travelbox.pe",
                warehousesByName,
                molinaWarehouse()
        );
        assignWarehouses(
                userRepository,
                "support.demo.multisede@travelbox.pe",
                warehousesByName,
                demoMultiScopeWarehouses()
        );
        assignWarehouses(
                userRepository,
                "supervisor.demo@travelbox.pe",
                warehousesByName,
                demoSupervisorWarehouses()
        );
    }

    private void assignWarehouses(
            UserRepository userRepository,
            String email,
            Map<String, Warehouse> warehousesByName,
            Set<String> warehouseNames
    ) {
        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null) {
            return;
        }
        Set<Warehouse> assignments = warehouseNames.stream()
                .map(warehousesByName::get)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        user.updateWarehouseAssignments(assignments);
        userRepository.save(user);
    }

    private void seedPerWarehouseOperationalUsers(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            WarehouseRepository warehouseRepository
    ) {
        List<Warehouse> warehouses = warehouseRepository.findByActiveTrueOrderByNameAsc();
        int index = 1;
        for (Warehouse warehouse : warehouses) {
            String suffix = warehouseEmailSuffix(warehouse.getName());
            String warehouseLabel = shortWarehouseLabel(warehouse.getName());

            String operatorEmail = "operator." + suffix + "@travelbox.pe";
            String courierEmail = "courier." + suffix + "@travelbox.pe";
            String supportEmail = "support." + suffix + "@travelbox.pe";

            seedUser(
                    userRepository,
                    passwordEncoder,
                    operatorEmail,
                    "Operator123!",
                    "Operador",
                    warehouseLabel,
                    buildSeedPhone(971, index),
                    Set.of(Role.OPERATOR)
            );
            seedUser(
                    userRepository,
                    passwordEncoder,
                    courierEmail,
                    "Courier123!",
                    "Courier",
                    warehouseLabel,
                    buildSeedPhone(972, index),
                    Set.of(Role.COURIER)
            );
            seedUser(
                    userRepository,
                    passwordEncoder,
                    supportEmail,
                    "Support123!",
                    "Soporte",
                    warehouseLabel,
                    buildSeedPhone(973, index),
                    Set.of(Role.SUPPORT)
            );

            assignWarehouses(
                    userRepository,
                    operatorEmail,
                    Map.of(warehouse.getName(), warehouse),
                    Set.of(warehouse.getName())
            );
            assignWarehouses(
                    userRepository,
                    courierEmail,
                    Map.of(warehouse.getName(), warehouse),
                    Set.of(warehouse.getName())
            );
            assignWarehouses(
                    userRepository,
                    supportEmail,
                    Map.of(warehouse.getName(), warehouse),
                    Set.of(warehouse.getName())
            );
            index++;
        }
    }

    private String warehouseEmailSuffix(String warehouseName) {
        String base = warehouseName == null ? "" : warehouseName.trim();
        base = base.replaceFirst("(?i)^travelbox\\s+", "");
        String normalized = base
                .toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", ".")
                .replaceAll("^\\.+", "")
                .replaceAll("\\.+$", "")
                .replaceAll("\\.{2,}", ".");
        return normalized.isBlank() ? "sede" : normalized;
    }

    private String shortWarehouseLabel(String warehouseName) {
        String base = warehouseName == null ? "Sede" : warehouseName.trim();
        return base.replaceFirst("(?i)^travelbox\\s+", "").trim();
    }

    private String buildSeedPhone(int prefix, int index) {
        return String.format(java.util.Locale.ROOT, "+51%d%06d", prefix, index);
    }

    private Set<String> limaCoreWarehouses() {
        return Set.of(
                "TravelBox Lima Centro",
                "TravelBox Miraflores",
                "TravelBox Barranco"
        );
    }

    private Set<String> limaSupportWarehouses() {
        return Set.of(
                "TravelBox Lima Centro",
                "TravelBox Miraflores",
                "TravelBox Barranco",
                "TravelBox La Molina"
        );
    }

    private Set<String> northWarehouses() {
        return Set.of(
                "TravelBox Trujillo Centro",
                "TravelBox Piura Plaza",
                "TravelBox Mancora Beach"
        );
    }

    private Set<String> southWarehouses() {
        return Set.of(
                "TravelBox Cusco Plaza",
                "TravelBox Arequipa Yanahuara",
                "TravelBox Huacachina",
                "TravelBox Puno Terminal",
                "TravelBox Paracas Muelle",
                "TravelBox Nazca Lines"
        );
    }

    private Set<String> molinaWarehouse() {
        return Set.of("TravelBox La Molina");
    }

    private Set<String> demoMultiScopeWarehouses() {
        return Set.of(
                "TravelBox Miraflores",
                "TravelBox La Molina"
        );
    }

    private Set<String> demoSupervisorWarehouses() {
        return Set.of(
                "TravelBox Lima Centro",
                "TravelBox Miraflores",
                "TravelBox Barranco"
        );
    }

    private void seedOperationalDemoData(
            UserRepository userRepository,
            WarehouseRepository warehouseRepository,
            ReservationRepository reservationRepository,
            PaymentAttemptRepository paymentAttemptRepository,
            IncidentRepository incidentRepository,
            DeliveryOrderRepository deliveryOrderRepository,
            DeliveryTrackingEventRepository deliveryTrackingEventRepository
    ) {
        if (reservationRepository.count() > 0) {
            return;
        }

        Map<String, User> usersByEmail = userRepository.findAll().stream()
                .collect(Collectors.toMap(User::getEmail, Function.identity(), (left, right) -> left));
        Map<String, Warehouse> warehousesByName = warehouseRepository.findByActiveTrueOrderByNameAsc().stream()
                .collect(Collectors.toMap(Warehouse::getName, Function.identity(), (left, right) -> left, LinkedHashMap::new));

        Instant now = Instant.now().truncatedTo(ChronoUnit.MINUTES);

        createReservationScenario(
                reservationRepository, paymentAttemptRepository, incidentRepository,
                usersByEmail.get("client@travelbox.pe"), warehousesByName.get("TravelBox Miraflores"),
                now.minus(2, ChronoUnit.DAYS), Duration.ofHours(8), new BigDecimal("36.00"), 2,
                ReservationStatus.COMPLETED, PaymentStatus.CONFIRMED, null, false
        );
        createReservationScenario(
                reservationRepository, paymentAttemptRepository, incidentRepository,
                usersByEmail.get("client.cusco@travelbox.pe"), warehousesByName.get("TravelBox Miraflores"),
                now.minus(6, ChronoUnit.HOURS), Duration.ofHours(18), new BigDecimal("28.00"), 1,
                ReservationStatus.STORED, PaymentStatus.CONFIRMED, null, false
        );
        createReservationScenario(
                reservationRepository, paymentAttemptRepository, incidentRepository,
                usersByEmail.get("client.north@travelbox.pe"), warehousesByName.get("TravelBox Barranco"),
                now.minus(5, ChronoUnit.DAYS), Duration.ofHours(6), new BigDecimal("18.00"), 1,
                ReservationStatus.CANCELLED, PaymentStatus.FAILED, "Cliente cancelo por cambio de ruta.", false
        );
        createReservationScenario(
                reservationRepository, paymentAttemptRepository, incidentRepository,
                usersByEmail.get("client.cusco@travelbox.pe"), warehousesByName.get("TravelBox Cusco Plaza"),
                now.minus(3, ChronoUnit.DAYS), Duration.ofHours(12), new BigDecimal("42.00"), 3,
                ReservationStatus.INCIDENT, PaymentStatus.CONFIRMED,
                "Maleta con etiqueta deteriorada. EVIDENCIA: /api/v1/files/evidences/demo-cusco-incident.jpg", true
        );
        createReservationScenario(
                reservationRepository, paymentAttemptRepository, incidentRepository,
                usersByEmail.get("client@travelbox.pe"), warehousesByName.get("TravelBox Arequipa Yanahuara"),
                now.minus(12, ChronoUnit.DAYS), Duration.ofHours(10), new BigDecimal("31.00"), 2,
                ReservationStatus.COMPLETED, PaymentStatus.CONFIRMED, null, false
        );
        createReservationScenario(
                reservationRepository, paymentAttemptRepository, incidentRepository,
                usersByEmail.get("client.cusco@travelbox.pe"), warehousesByName.get("TravelBox Huacachina"),
                now.minus(19, ChronoUnit.DAYS), Duration.ofHours(5), new BigDecimal("24.00"), 1,
                ReservationStatus.COMPLETED, PaymentStatus.CONFIRMED, null, false
        );
        createReservationScenario(
                reservationRepository, paymentAttemptRepository, incidentRepository,
                usersByEmail.get("client@travelbox.pe"), warehousesByName.get("TravelBox Puno Terminal"),
                now.minus(26, ChronoUnit.DAYS), Duration.ofHours(7), new BigDecimal("16.00"), 1,
                ReservationStatus.PENDING_PAYMENT, PaymentStatus.PENDING, null, false
        );
        createReservationScenario(
                reservationRepository, paymentAttemptRepository, incidentRepository,
                usersByEmail.get("client.north@travelbox.pe"), warehousesByName.get("TravelBox Paracas Muelle"),
                now.minus(28, ChronoUnit.DAYS), Duration.ofHours(8), new BigDecimal("29.00"), 2,
                ReservationStatus.OUT_FOR_DELIVERY, PaymentStatus.CONFIRMED, null, false
        );
        createReservationScenario(
                reservationRepository, paymentAttemptRepository, incidentRepository,
                usersByEmail.get("client.cusco@travelbox.pe"), warehousesByName.get("TravelBox Nazca Lines"),
                now.minus(41, ChronoUnit.DAYS), Duration.ofHours(9), new BigDecimal("27.00"), 2,
                ReservationStatus.COMPLETED, PaymentStatus.CONFIRMED, null, false
        );
        createReservationScenario(
                reservationRepository, paymentAttemptRepository, incidentRepository,
                usersByEmail.get("client@travelbox.pe"), warehousesByName.get("TravelBox Trujillo Centro"),
                now.minus(73, ChronoUnit.DAYS), Duration.ofHours(6), new BigDecimal("22.00"), 1,
                ReservationStatus.COMPLETED, PaymentStatus.CONFIRMED, null, false
        );
        createReservationScenario(
                reservationRepository, paymentAttemptRepository, incidentRepository,
                usersByEmail.get("client.north@travelbox.pe"), warehousesByName.get("TravelBox Piura Plaza"),
                now.minus(152, ChronoUnit.DAYS), Duration.ofHours(4), new BigDecimal("15.00"), 1,
                ReservationStatus.CANCELLED, PaymentStatus.FAILED, "Pago rechazado en intento inicial.", false
        );
        createReservationScenario(
                reservationRepository, paymentAttemptRepository, incidentRepository,
                usersByEmail.get("client.north@travelbox.pe"), warehousesByName.get("TravelBox Mancora Beach"),
                now.minus(280, ChronoUnit.DAYS), Duration.ofHours(12), new BigDecimal("48.00"), 2,
                ReservationStatus.COMPLETED, PaymentStatus.CONFIRMED, null, false
        );
        createReservationScenario(
                reservationRepository, paymentAttemptRepository, incidentRepository,
                usersByEmail.get("client@travelbox.pe"), warehousesByName.get("TravelBox Lima Centro"),
                now.minus(320, ChronoUnit.DAYS), Duration.ofHours(7), new BigDecimal("19.00"), 1,
                ReservationStatus.COMPLETED, PaymentStatus.CONFIRMED, null, false
        );
        createReservationScenario(
                reservationRepository, paymentAttemptRepository, incidentRepository,
                usersByEmail.get("client.cusco@travelbox.pe"), warehousesByName.get("TravelBox Miraflores"),
                now.minus(15, ChronoUnit.DAYS), Duration.ofHours(9), new BigDecimal("33.00"), 2,
                ReservationStatus.COMPLETED, PaymentStatus.CONFIRMED, null, false
        );

        seedDemoDeliveryOrders(
                userRepository,
                reservationRepository,
                deliveryOrderRepository,
                deliveryTrackingEventRepository
        );

        warehouseRepository.saveAll(warehousesByName.values());
    }

    private void createReservationScenario(
            ReservationRepository reservationRepository,
            PaymentAttemptRepository paymentAttemptRepository,
            IncidentRepository incidentRepository,
            User user,
            Warehouse warehouse,
            Instant startAt,
            Duration duration,
            BigDecimal totalPrice,
            int estimatedItems,
            ReservationStatus finalStatus,
            PaymentStatus paymentStatus,
            String details,
            boolean keepIncidentOpen
    ) {
        if (user == null || warehouse == null) {
            return;
        }

        Reservation reservation = Reservation.createPendingPayment(
                user,
                warehouse,
                startAt,
                startAt.plus(duration),
                totalPrice,
                estimatedItems,
                ReservationBagSize.MEDIUM,
                false,
                false,
                false,
                totalPrice,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                startAt.minus(12, ChronoUnit.HOURS)
        );
        reservation = reservationRepository.save(reservation);

        PaymentAttempt paymentAttempt = PaymentAttempt.pending(reservation, totalPrice);
        paymentAttempt.registerGatewayOutcome(paymentStatus.name(), paymentSeedMessage(paymentStatus));
        paymentAttempt.registerProviderReference("seed-" + reservation.getId() + "-" + paymentStatus.name().toLowerCase());
        if (paymentStatus == PaymentStatus.CONFIRMED) {
            paymentAttempt.confirm(paymentAttempt.getProviderReference());
            reservation.confirmPayment();
        } else if (paymentStatus == PaymentStatus.FAILED) {
            paymentAttempt.fail(paymentAttempt.getProviderReference());
        }
        paymentAttemptRepository.save(paymentAttempt);

        if (paymentStatus == PaymentStatus.CONFIRMED) {
            moveReservationToOperationalStatus(reservation, finalStatus);
        } else if (finalStatus == ReservationStatus.CANCELLED) {
            reservation.cancel(details != null ? details : "Cancelada en seed local");
        } else if (finalStatus == ReservationStatus.EXPIRED) {
            reservation.expire();
        }

        if (occupiesSpace(finalStatus)) {
            warehouse.occupyOneSlot();
        }

        reservation = reservationRepository.save(reservation);

        if (details != null && finalStatus == ReservationStatus.INCIDENT) {
            Incident incident = Incident.open(reservation, user, details);
            if (!keepIncidentOpen) {
                incident.resolve(user, "Incidencia cerrada en seed local.");
            }
            incidentRepository.save(incident);
        }
    }

    private void moveReservationToOperationalStatus(Reservation reservation, ReservationStatus targetStatus) {
        if (targetStatus == ReservationStatus.CONFIRMED) {
            return;
        }
        if (targetStatus == ReservationStatus.CANCELLED) {
            reservation.cancel("Cancelada despues de confirmar pago");
            return;
        }
        if (targetStatus == ReservationStatus.EXPIRED) {
            reservation.expire();
            return;
        }
        if (targetStatus == ReservationStatus.CHECKIN_PENDING) {
            reservation.transitionTo(ReservationStatus.CHECKIN_PENDING);
            return;
        }

        reservation.transitionTo(ReservationStatus.CHECKIN_PENDING);
        if (targetStatus == ReservationStatus.STORED) {
            reservation.transitionTo(ReservationStatus.STORED);
            return;
        }

        reservation.transitionTo(ReservationStatus.STORED);
        if (targetStatus == ReservationStatus.READY_FOR_PICKUP) {
            reservation.transitionTo(ReservationStatus.READY_FOR_PICKUP);
            return;
        }
        if (targetStatus == ReservationStatus.OUT_FOR_DELIVERY) {
            reservation.transitionTo(ReservationStatus.OUT_FOR_DELIVERY);
            return;
        }
        if (targetStatus == ReservationStatus.INCIDENT) {
            reservation.transitionTo(ReservationStatus.INCIDENT);
            return;
        }
        if (targetStatus == ReservationStatus.COMPLETED) {
            reservation.transitionTo(ReservationStatus.COMPLETED);
        }
    }

    private boolean occupiesSpace(ReservationStatus status) {
        return status == ReservationStatus.STORED
                || status == ReservationStatus.READY_FOR_PICKUP
                || status == ReservationStatus.OUT_FOR_DELIVERY
                || status == ReservationStatus.INCIDENT;
    }

    private void seedDemoDeliveryOrders(
            UserRepository userRepository,
            ReservationRepository reservationRepository,
            DeliveryOrderRepository deliveryOrderRepository,
            DeliveryTrackingEventRepository deliveryTrackingEventRepository
    ) {
        if (deliveryOrderRepository.count() > 0) {
            return;
        }

        List<Reservation> reservations = reservationRepository.findAll();
        Reservation deliveredReservation = reservations.stream()
                .filter(reservation -> reservation.getStatus() == ReservationStatus.COMPLETED)
                .filter(reservation -> reservation.getWarehouse().getName().equals("TravelBox Miraflores"))
                .findFirst()
                .orElse(null);
        Reservation inTransitReservation = reservations.stream()
                .filter(reservation -> reservation.getStatus() == ReservationStatus.OUT_FOR_DELIVERY)
                .findFirst()
                .orElse(null);
        User mainOperator = userRepository.findByEmailIgnoreCase("operator@travelbox.pe").orElse(null);
        User northOperator = userRepository.findByEmailIgnoreCase("operator.north@travelbox.pe").orElse(null);
        User mainCourier = userRepository.findByEmailIgnoreCase("courier@travelbox.pe").orElse(null);
        User northCourier = userRepository.findByEmailIgnoreCase("courier.north@travelbox.pe").orElse(null);

        seedDeliveryOrder(
                deliveryOrderRepository,
                deliveryTrackingEventRepository,
                deliveredReservation,
                mainOperator,
                mainCourier,
                DeliveryStatus.DELIVERED,
                "Hotel Miraflores Demo",
                "LIMA"
        );
        seedDeliveryOrder(
                deliveryOrderRepository,
                deliveryTrackingEventRepository,
                inTransitReservation,
                northOperator,
                northCourier,
                DeliveryStatus.IN_TRANSIT,
                "Muelle turistico de Paracas",
                "ICA"
        );
    }

    private void seedDeliveryOrder(
            DeliveryOrderRepository deliveryOrderRepository,
            DeliveryTrackingEventRepository deliveryTrackingEventRepository,
            Reservation reservation,
            User operator,
            User courier,
            DeliveryStatus targetStatus,
            String address,
            String zone
    ) {
        if (reservation == null) {
            return;
        }

        DeliveryOrder order = DeliveryOrder.create(
                reservation,
                "MOTO",
                address,
                zone,
                BigDecimal.valueOf(15.00)
        );
        if (operator != null) {
            order.setCreatedBy(operator.getEmail());
            order.setUpdatedBy(operator.getEmail());
        }
        if (courier != null) {
            order.assignCourier(courier);
        }
        double originLatitude = reservation.getWarehouse().getLatitude();
        double originLongitude = reservation.getWarehouse().getLongitude();
        double destinationLatitude = originLatitude + 0.011;
        double destinationLongitude = originLongitude + 0.009;

        order.configureMockTracking(
                "Operador " + reservation.getWarehouse().getCity().getName(),
                "+51991555000",
                "MOTO",
                "TBX-" + String.format("%04d", reservation.getId()),
                originLatitude,
                originLongitude,
                destinationLatitude,
                destinationLongitude,
                18
        );
        order = deliveryOrderRepository.save(order);

        deliveryTrackingEventRepository.save(DeliveryTrackingEvent.of(
                order,
                0,
                DeliveryStatus.REQUESTED,
                originLatitude,
                originLongitude,
                18,
                "Solicitud registrada. Esperando asignacion."
        ));

        if (targetStatus == DeliveryStatus.REQUESTED) {
            return;
        }

        double assignedLatitude = interpolate(originLatitude, destinationLatitude, 0.25);
        double assignedLongitude = interpolate(originLongitude, destinationLongitude, 0.25);
        order.advanceTracking(
                assignedLatitude,
                assignedLongitude,
                DeliveryStatus.ASSIGNED,
                12,
                Instant.now().plusSeconds(10)
        );
        order = deliveryOrderRepository.save(order);
        deliveryTrackingEventRepository.save(DeliveryTrackingEvent.of(
                order,
                1,
                DeliveryStatus.ASSIGNED,
                assignedLatitude,
                assignedLongitude,
                12,
                "Unidad asignada y en camino."
        ));

        if (targetStatus == DeliveryStatus.ASSIGNED) {
            return;
        }

        double transitLatitude = interpolate(originLatitude, destinationLatitude, 0.7);
        double transitLongitude = interpolate(originLongitude, destinationLongitude, 0.7);
        order.advanceTracking(
                transitLatitude,
                transitLongitude,
                DeliveryStatus.IN_TRANSIT,
                5,
                Instant.now().plusSeconds(10)
        );
        order = deliveryOrderRepository.save(order);
        deliveryTrackingEventRepository.save(DeliveryTrackingEvent.of(
                order,
                2,
                DeliveryStatus.IN_TRANSIT,
                transitLatitude,
                transitLongitude,
                5,
                "Unidad en ruta hacia el destino."
        ));

        if (targetStatus == DeliveryStatus.IN_TRANSIT) {
            return;
        }

        order.advanceTracking(
                destinationLatitude,
                destinationLongitude,
                DeliveryStatus.DELIVERED,
                0,
                Instant.now()
        );
        order = deliveryOrderRepository.save(order);
        deliveryTrackingEventRepository.save(DeliveryTrackingEvent.of(
                order,
                3,
                DeliveryStatus.DELIVERED,
                destinationLatitude,
                destinationLongitude,
                0,
                "Equipaje entregado."
        ));
    }

    private double interpolate(double origin, double destination, double progress) {
        return origin + ((destination - origin) * progress);
    }

    private String paymentSeedMessage(PaymentStatus paymentStatus) {
        return switch (paymentStatus) {
            case CONFIRMED -> "Pago confirmado en seed local";
            case FAILED -> "Pago rechazado en seed local";
            case PENDING -> "Pago aun pendiente en seed local";
            case REFUNDED -> "Pago reembolsado en seed local";
        };
    }

    private List<WarehouseSeed> warehouseSeeds() {
        return List.of(
                new WarehouseSeed("Lima", "Miraflores", -12.1211, -77.0297, 2.5, "TravelBox Miraflores", "Av. Larco 812, Miraflores", -12.1220, -77.0309, 120, "08:00", "22:00", "No se permiten objetos peligrosos ni perecibles."),
                new WarehouseSeed("Lima", "Barranco", -12.1465, -77.0209, 2.2, "TravelBox Barranco", "Jr. Perez Roca 450, Barranco", -12.1481, -77.0205, 90, "07:00", "22:00", "Ideal para equipaje de paso y pickups."),
                new WarehouseSeed("Lima", "Centro Historico", -12.0464, -77.0428, 3.1, "TravelBox Lima Centro", "Jr. De la Union 635, Cercado de Lima", -12.0474, -77.0420, 85, "08:00", "21:30", "Acceso con QR o documento principal."),
                new WarehouseSeed("Lima", "La Molina", -12.0856, -76.9530, 2.4, "TravelBox La Molina", "Av. La Molina 1650, La Molina", -12.0868, -76.9522, 72, "08:00", "22:00", "Cobertura para sedes universitarias y zonas residenciales."),
                new WarehouseSeed("Cusco", "Centro Historico", -13.5160, -71.9781, 3.0, "TravelBox Cusco Plaza", "Calle Plateros 145, Cusco", -13.5167, -71.9788, 80, "07:00", "21:00", "Ingreso con documento y QR vigente."),
                new WarehouseSeed("Arequipa", "Yanahuara", -16.3939, -71.5459, 2.8, "TravelBox Arequipa Yanahuara", "Calle Jerusalen 211, Arequipa", -16.3945, -71.5363, 70, "08:00", "21:00", "Se aceptan maletas, mochilas y cajas selladas."),
                new WarehouseSeed("Ica", "Huacachina", -14.0875, -75.7626, 2.4, "TravelBox Huacachina", "Av. Perotti s/n, Ica", -14.0878, -75.7621, 60, "08:00", "20:30", "Servicio rapido para tours de desierto."),
                new WarehouseSeed("Puno", "Centro Puno", -15.8402, -70.0219, 2.6, "TravelBox Puno Terminal", "Jr. Deustua 320, Puno", -15.8413, -70.0237, 55, "06:30", "21:00", "Equipaje etiquetado para salidas tempranas."),
                new WarehouseSeed("Paracas", "El Chaco", -13.8357, -76.2506, 2.0, "TravelBox Paracas Muelle", "Av. Paracas 145, Paracas", -13.8351, -76.2485, 50, "07:00", "20:00", "Soporte para recojo hotel y muelle."),
                new WarehouseSeed("Nazca", "Centro Nazca", -14.8280, -74.9388, 2.3, "TravelBox Nazca Lines", "Calle Lima 118, Nazca", -14.8292, -74.9385, 45, "07:30", "20:00", "Cobertura para tours a lineas y terminal."),
                new WarehouseSeed("Trujillo", "Centro Historico", -8.1116, -79.0287, 2.5, "TravelBox Trujillo Centro", "Jr. Pizarro 610, Trujillo", -8.1106, -79.0281, 65, "08:00", "21:30", "Capacidad mixta para equipaje y paquetes."),
                new WarehouseSeed("Piura", "Centro Piura", -5.1945, -80.6328, 2.8, "TravelBox Piura Plaza", "Av. Grau 201, Piura", -5.1941, -80.6322, 58, "08:00", "21:00", "Cobertura para aeropuerto y terminal."),
                new WarehouseSeed("Mancora", "Playa Mancora", -4.1075, -81.0470, 2.4, "TravelBox Mancora Beach", "Av. Piura 415, Mancora", -4.1069, -81.0464, 40, "08:30", "20:30", "Servicio de corta estancia para playa.")
        );
    }

    private record WarehouseSeed(
            String cityName,
            String zoneName,
            double zoneLatitude,
            double zoneLongitude,
            double zoneRadiusKm,
            String warehouseName,
            String address,
            double latitude,
            double longitude,
            int capacity,
            String openHour,
            String closeHour,
            String rules
    ) {
    }
}
