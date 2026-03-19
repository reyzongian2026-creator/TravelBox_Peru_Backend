package com.tuempresa.storage.geo.infrastructure.out.persistence;

import com.tuempresa.storage.geo.domain.TouristZone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TouristZoneRepository extends JpaRepository<TouristZone, Long> {

    List<TouristZone> findByCityIdOrderByNameAsc(Long cityId);
}
