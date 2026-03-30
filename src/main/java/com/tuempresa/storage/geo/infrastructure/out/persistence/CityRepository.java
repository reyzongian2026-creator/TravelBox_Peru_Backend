package com.tuempresa.storage.geo.infrastructure.out.persistence;

import com.tuempresa.storage.geo.domain.City;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CityRepository extends JpaRepository<City, Long> {

    List<City> findByActiveTrueOrderByNameAsc();

    @Query("""
            select c from City c
            where c.active = true
              and lower(c.name) like concat('%', lower(:query), '%')
            order by c.name asc
            """)
    List<City> searchActiveByName(@Param("query") String query, Pageable pageable);

    Optional<City> findByNameIgnoreCase(String name);
}
