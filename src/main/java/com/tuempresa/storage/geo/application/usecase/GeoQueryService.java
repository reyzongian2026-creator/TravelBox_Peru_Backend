package com.tuempresa.storage.geo.application.usecase;

import com.tuempresa.storage.geo.application.dto.CityResponse;
import com.tuempresa.storage.geo.application.dto.GeoSearchSuggestionResponse;
import com.tuempresa.storage.geo.application.dto.TouristZoneResponse;
import com.tuempresa.storage.geo.infrastructure.out.persistence.CityRepository;
import com.tuempresa.storage.geo.infrastructure.out.persistence.TouristZoneRepository;
import com.tuempresa.storage.warehouses.infrastructure.out.persistence.WarehouseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GeoQueryService {

    private final CityRepository cityRepository;
    private final TouristZoneRepository touristZoneRepository;
    private final WarehouseRepository warehouseRepository;

    public GeoQueryService(
            CityRepository cityRepository,
            TouristZoneRepository touristZoneRepository,
            WarehouseRepository warehouseRepository
    ) {
        this.cityRepository = cityRepository;
        this.touristZoneRepository = touristZoneRepository;
        this.warehouseRepository = warehouseRepository;
    }

    @Transactional(readOnly = true)
    public List<CityResponse> listCities() {
        return cityRepository.findByActiveTrueOrderByNameAsc()
                .stream()
                .map(city -> new CityResponse(city.getId(), city.getName(), city.getCountry()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TouristZoneResponse> listZones(Long cityId) {
        return touristZoneRepository.findByCityIdOrderByNameAsc(cityId)
                .stream()
                .map(zone -> new TouristZoneResponse(
                        zone.getId(),
                        zone.getCity().getId(),
                        zone.getName(),
                        zone.getLatitude(),
                        zone.getLongitude(),
                        zone.getRadiusKm()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GeoSearchSuggestionResponse> search(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String normalized = query.trim().toLowerCase();

        List<GeoSearchSuggestionResponse> cityMatches = cityRepository.findByActiveTrueOrderByNameAsc()
                .stream()
                .filter(city -> city.getName().toLowerCase().contains(normalized))
                .map(city -> new GeoSearchSuggestionResponse(
                        "CITY",
                        city.getId(),
                        city.getName(),
                        city.getId(),
                        city.getName(),
                        null,
                        null
                ))
                .toList();

        List<GeoSearchSuggestionResponse> zoneMatches = touristZoneRepository.findAll()
                .stream()
                .filter(zone -> zone.getName().toLowerCase().contains(normalized))
                .map(zone -> new GeoSearchSuggestionResponse(
                        "ZONE",
                        zone.getId(),
                        zone.getName(),
                        zone.getCity().getId(),
                        zone.getCity().getName(),
                        zone.getLatitude(),
                        zone.getLongitude()
                ))
                .toList();

        List<GeoSearchSuggestionResponse> warehouseMatches = warehouseRepository.findByActiveTrueOrderByNameAsc()
                .stream()
                .filter(warehouse ->
                        warehouse.getName().toLowerCase().contains(normalized)
                        || warehouse.getAddress().toLowerCase().contains(normalized)
                )
                .map(warehouse -> new GeoSearchSuggestionResponse(
                        "WAREHOUSE",
                        warehouse.getId(),
                        warehouse.getName() + " - " + warehouse.getAddress(),
                        warehouse.getCity().getId(),
                        warehouse.getCity().getName(),
                        warehouse.getLatitude(),
                        warehouse.getLongitude()
                ))
                .toList();

        return java.util.stream.Stream.of(cityMatches, zoneMatches, warehouseMatches)
                .flatMap(List::stream)
                .limit(30)
                .toList();
    }
}
