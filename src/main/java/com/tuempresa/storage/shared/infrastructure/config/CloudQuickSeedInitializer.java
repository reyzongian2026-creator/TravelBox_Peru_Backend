package com.tuempresa.storage.shared.infrastructure.config;

import com.tuempresa.storage.geo.domain.City;
import com.tuempresa.storage.geo.domain.TouristZone;
import com.tuempresa.storage.geo.infrastructure.out.persistence.CityRepository;
import com.tuempresa.storage.geo.infrastructure.out.persistence.TouristZoneRepository;
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

import java.util.Set;

@Configuration
public class CloudQuickSeedInitializer {

    private static final Logger log = LoggerFactory.getLogger(CloudQuickSeedInitializer.class);

    @Bean
    CommandLineRunner quickSeedForCloud(
            UserRepository userRepository,
            CityRepository cityRepository,
            TouristZoneRepository touristZoneRepository,
            WarehouseRepository warehouseRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.bootstrap.quick-seed:false}") boolean quickSeedEnabled
    ) {
        return args -> {
            if (!quickSeedEnabled) {
                return;
            }
            log.info("Cloud quick seed started");
            Warehouse warehouse = ensureWarehouse(cityRepository, touristZoneRepository, warehouseRepository);

            ensureUser(
                    userRepository,
                    passwordEncoder,
                    "admin@travelbox.pe",
                    "Admin123!",
                    "Admin",
                    "TravelBox",
                    "+51900010001",
                    Set.of(Role.ADMIN, Role.SUPPORT),
                    null
            );
            ensureUser(
                    userRepository,
                    passwordEncoder,
                    "operator@travelbox.pe",
                    "Operator123!",
                    "Operador",
                    "Principal",
                    "+51900010002",
                    Set.of(Role.OPERATOR),
                    null
            );
            ensureUser(
                    userRepository,
                    passwordEncoder,
                    "courier@travelbox.pe",
                    "Courier123!",
                    "Courier",
                    "TravelBox",
                    "+51900010003",
                    Set.of(Role.COURIER),
                    "TBX-1001"
            );
            ensureUser(
                    userRepository,
                    passwordEncoder,
                    "support@travelbox.pe",
                    "Support123!",
                    "Soporte",
                    "TravelBox",
                    "+51900010004",
                    Set.of(Role.SUPPORT),
                    null
            );
            ensureUser(
                    userRepository,
                    passwordEncoder,
                    "client@travelbox.pe",
                    "Client123!",
                    "Cliente",
                    "Demo",
                    "+51900010005",
                    Set.of(Role.CLIENT),
                    null
            );

            assignWarehouse(userRepository, "operator@travelbox.pe", warehouse);
            assignWarehouse(userRepository, "courier@travelbox.pe", warehouse);
            assignWarehouse(userRepository, "support@travelbox.pe", warehouse);

            log.info(
                    "Cloud quick seed completed (users={}, warehouses={})",
                    userRepository.count(),
                    warehouseRepository.count()
            );
        };
    }

    private Warehouse ensureWarehouse(
            CityRepository cityRepository,
            TouristZoneRepository touristZoneRepository,
            WarehouseRepository warehouseRepository
    ) {
        Warehouse existing = warehouseRepository.findByActiveTrueOrderByNameAsc()
                .stream()
                .filter(warehouse -> "TravelBox Miraflores".equalsIgnoreCase(warehouse.getName()))
                .findFirst()
                .orElse(null);
        if (existing != null) {
            return existing;
        }

        City city = cityRepository.findByNameIgnoreCase("Lima")
                .orElseGet(() -> cityRepository.save(City.of("Lima", "Peru")));

        TouristZone zone = touristZoneRepository.findByCityIdOrderByNameAsc(city.getId())
                .stream()
                .filter(candidate -> "Miraflores".equalsIgnoreCase(candidate.getName()))
                .findFirst()
                .orElseGet(() -> touristZoneRepository.save(
                        TouristZone.of(city, "Miraflores", -12.1211, -77.0297, 2.5)
                ));

        return warehouseRepository.save(Warehouse.of(
                city,
                zone,
                "TravelBox Miraflores",
                "Av. Larco 812, Miraflores",
                -12.1220,
                -77.0309,
                120,
                "08:00",
                "22:00",
                "No se permiten objetos peligrosos ni perecibles.",
                Warehouse.DEFAULT_PRICE_SMALL_PER_HOUR,
                Warehouse.DEFAULT_PRICE_MEDIUM_PER_HOUR,
                Warehouse.DEFAULT_PRICE_LARGE_PER_HOUR,
                Warehouse.DEFAULT_PRICE_EXTRA_LARGE_PER_HOUR,
                Warehouse.DEFAULT_PICKUP_FEE,
                Warehouse.DEFAULT_DROPOFF_FEE,
                Warehouse.DEFAULT_INSURANCE_FEE
        ));
    }

    private void ensureUser(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            String email,
            String rawPassword,
            String firstName,
            String lastName,
            String phone,
            Set<Role> roles,
            String vehiclePlate
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
        user.markManagedByAdmin(roles.stream().anyMatch(role -> role != Role.CLIENT));
        if (vehiclePlate != null && !vehiclePlate.isBlank()) {
            user.updateVehiclePlate(vehiclePlate);
        }
        userRepository.save(user);
    }

    private void assignWarehouse(UserRepository userRepository, String email, Warehouse warehouse) {
        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null) {
            return;
        }
        user.updateWarehouseAssignments(Set.of(warehouse));
        userRepository.save(user);
    }
}
