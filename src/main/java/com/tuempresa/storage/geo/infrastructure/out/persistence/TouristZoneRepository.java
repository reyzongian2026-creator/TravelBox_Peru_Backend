package com.tuempresa.storage.geo.infrastructure.out.persistence;

import com.tuempresa.storage.geo.domain.TouristZone;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TouristZoneRepository extends JpaRepository<TouristZone, Long> {

    @EntityGraph(attributePaths = {"city"})
    List<TouristZone> findByCityIdOrderByNameAsc(Long cityId);

    @EntityGraph(attributePaths = {"city"})
    @Query("""
            select z from TouristZone z
            where lower(z.name) like concat('%', lower(:query), '%')
            order by z.name asc
            """)
    List<TouristZone> searchByName(@Param("query") String query, Pageable pageable);
}
