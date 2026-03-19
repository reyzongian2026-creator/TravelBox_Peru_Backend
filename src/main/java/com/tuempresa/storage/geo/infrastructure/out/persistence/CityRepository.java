package com.tuempresa.storage.geo.infrastructure.out.persistence;

import com.tuempresa.storage.geo.domain.City;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CityRepository extends JpaRepository<City, Long> {

    List<City> findByActiveTrueOrderByNameAsc();

    Optional<City> findByNameIgnoreCase(String name);
}
