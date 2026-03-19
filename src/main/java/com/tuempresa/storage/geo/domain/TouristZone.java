package com.tuempresa.storage.geo.domain;

import com.tuempresa.storage.shared.infrastructure.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "tourist_zones")
public class TouristZone extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "city_id", nullable = false)
    private City city;

    @Column(nullable = false, length = 140)
    private String name;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(nullable = false)
    private double radiusKm;

    public static TouristZone of(City city, String name, double latitude, double longitude, double radiusKm) {
        TouristZone zone = new TouristZone();
        zone.city = city;
        zone.name = name;
        zone.latitude = latitude;
        zone.longitude = longitude;
        zone.radiusKm = radiusKm;
        return zone;
    }

    public City getCity() {
        return city;
    }

    public String getName() {
        return name;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public double getRadiusKm() {
        return radiusKm;
    }
}
