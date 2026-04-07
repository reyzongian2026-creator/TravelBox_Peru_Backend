package com.tuempresa.storage.incidents.infrastructure.out.persistence;

import com.tuempresa.storage.incidents.domain.Incident;
import com.tuempresa.storage.incidents.domain.IncidentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

    long countByStatus(IncidentStatus status);

    List<Incident> findByReservationIdIn(Collection<Long> reservationIds);

    List<Incident> findByReservationIdInAndStatus(Collection<Long> reservationIds, IncidentStatus status);

    @EntityGraph(attributePaths = {"reservation", "reservation.warehouse", "reservation.user", "openedBy", "resolvedBy"})
    @Query("""
            select i from Incident i
            where (:status is null or i.status = :status)
              and (:reservationId is null or i.reservation.id = :reservationId)
              and (
                :isAdmin = true
                or i.reservation.warehouse.id in :scopedWarehouseIds
                or i.reservation.user.id = :userId
                or i.openedBy.id = :userId
              )
            order by i.createdAt desc
            """)
    Page<Incident> findFiltered(
            @Param("status") IncidentStatus status,
            @Param("reservationId") Long reservationId,
            @Param("isAdmin") boolean isAdmin,
            @Param("scopedWarehouseIds") Set<Long> scopedWarehouseIds,
            @Param("userId") Long userId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"reservation", "reservation.warehouse", "reservation.user", "openedBy", "resolvedBy"})
    @Query("""
            select i from Incident i
            where (:status is null or i.status = :status)
              and (:reservationId is null or i.reservation.id = :reservationId)
              and (
                :isAdmin = true
                or i.reservation.warehouse.id in :scopedWarehouseIds
                or i.reservation.user.id = :userId
                or i.openedBy.id = :userId
              )
            order by i.createdAt desc
            """)
    List<Incident> findFilteredAll(
            @Param("status") IncidentStatus status,
            @Param("reservationId") Long reservationId,
            @Param("isAdmin") boolean isAdmin,
            @Param("scopedWarehouseIds") Set<Long> scopedWarehouseIds,
            @Param("userId") Long userId
    );

    @EntityGraph(attributePaths = {"reservation", "reservation.warehouse", "reservation.user", "openedBy", "resolvedBy"})
    @Query("""
            select i from Incident i
            where (:status is null or i.status = :status)
              and (:reservationId is null or i.reservation.id = :reservationId)
              and (
                :isAdmin = true
                or i.reservation.warehouse.id in :scopedWarehouseIds
                or i.reservation.user.id = :userId
                or i.openedBy.id = :userId
              )
              and (
                lower(i.description) like concat('%', :query, '%')
                or lower(i.resolution) like concat('%', :query, '%')
                or lower(i.reservation.qrCode) like concat('%', :query, '%')
                or lower(i.reservation.warehouse.name) like concat('%', :query, '%')
                or lower(i.reservation.warehouse.address) like concat('%', :query, '%')
                or lower(i.openedBy.fullName) like concat('%', :query, '%')
                or lower(i.openedBy.email) like concat('%', :query, '%')
              )
            """)
    Page<Incident> findFilteredWithSearch(
            @Param("status") IncidentStatus status,
            @Param("reservationId") Long reservationId,
            @Param("isAdmin") boolean isAdmin,
            @Param("scopedWarehouseIds") Set<Long> scopedWarehouseIds,
            @Param("userId") Long userId,
            @Param("query") String query,
            Pageable pageable
    );
}
