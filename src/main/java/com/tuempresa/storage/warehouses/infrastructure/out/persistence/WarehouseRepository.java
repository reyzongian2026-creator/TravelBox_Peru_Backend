package com.tuempresa.storage.warehouses.infrastructure.out.persistence;

import com.tuempresa.storage.warehouses.domain.Warehouse;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {

    @Lock(LockModeType.OPTIMISTIC)
    @Query("select w from Warehouse w where w.id = :id")
    Optional<Warehouse> findByIdForUpdate(Long id);

    @EntityGraph(attributePaths = {"city", "zone"})
    @Query("select w from Warehouse w where w.id = :id")
    Optional<Warehouse> findByIdWithLocation(@Param("id") Long id);

    List<Warehouse> findByActiveTrueOrderByNameAsc();

    @Query("""
            select w from Warehouse w
            where w.active = true
              and (:cityId is null or w.city.id = :cityId)
              and (:query = '' or lower(w.name) like concat('%', lower(:query), '%')
                    or lower(w.address) like concat('%', lower(:query), '%'))
            order by w.name asc
            """)
    List<Warehouse> search(Long cityId, String query);

    @Query("""
            select w from Warehouse w
            where (:cityId is null or w.city.id = :cityId)
              and (:active is null or w.active = :active)
              and (:query = '' or lower(w.name) like concat('%', lower(:query), '%')
                    or lower(w.address) like concat('%', lower(:query), '%'))
            order by w.createdAt desc
            """)
    List<Warehouse> searchAdmin(Long cityId, String query, Boolean active);

    @Query("""
            select w from Warehouse w
            where (:cityId is null or w.city.id = :cityId)
              and (:active is null or w.active = :active)
              and (:query = '' or lower(w.name) like concat('%', lower(:query), '%')
                    or lower(w.address) like concat('%', lower(:query), '%'))
            """)
    Page<Warehouse> searchAdminPage(Long cityId, String query, Boolean active, Pageable pageable);
}