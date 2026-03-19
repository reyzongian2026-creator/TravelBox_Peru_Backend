package com.tuempresa.storage.geo.domain;

import com.tuempresa.storage.shared.infrastructure.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "cities")
public class City extends AuditableEntity {

    @Column(nullable = false, unique = true, length = 120)
    private String name;

    @Column(nullable = false, length = 80)
    private String country;

    @Column(nullable = false)
    private boolean active = true;

    public static City of(String name, String country) {
        City city = new City();
        city.name = name;
        city.country = country;
        city.active = true;
        return city;
    }

    public String getName() {
        return name;
    }

    public String getCountry() {
        return country;
    }

    public boolean isActive() {
        return active;
    }
}
