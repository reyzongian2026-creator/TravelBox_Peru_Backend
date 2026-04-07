package com.tuempresa.storage.reservations.infrastructure.out.persistence;

import com.tuempresa.storage.reservations.domain.Reservation;
import com.tuempresa.storage.reservations.domain.ReservationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    @Query("""
            select count(r) from Reservation r
            where r.warehouse.id = :warehouseId
              and r.status in :activeStatuses
              and r.startAt < :endAt
              and r.endAt > :startAt
            """)
    long countOverlapping(
            Long warehouseId,
            Instant startAt,
            Instant endAt,
            Collection<ReservationStatus> activeStatuses
    );

    // 👇 MÉTODO AÑADIDO PARA IDEMPOTENCIA / ANTI-SPAM 👇
    boolean existsByUserIdAndWarehouseIdAndCreatedAtAfter(Long userId, Long warehouseId, Instant time);

    List<Reservation> findByUserIdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = {"warehouse", "warehouse.city", "user"})
    List<Reservation> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"warehouse", "warehouse.city", "user"})
    List<Reservation> findByWarehouseIdInOrderByCreatedAtDesc(Collection<Long> warehouseIds);

    Page<Reservation> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<Reservation> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Reservation> findByStatusOrderByCreatedAtDesc(ReservationStatus status, Pageable pageable);

    Page<Reservation> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, ReservationStatus status, Pageable pageable);

    List<Reservation> findByStatusAndExpiresAtBefore(ReservationStatus status, Instant threshold);

    List<Reservation> findByStartAtBetweenOrderByStartAtAsc(Instant startAt, Instant endAt);

    @Query("""
            select r from Reservation r
            where r.createdAt between :startAt and :endAt
            order by r.createdAt desc
            """)
    List<Reservation> findByCreatedAtBetweenOrderByCreatedAtDesc(
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt
    );

    long countByStatusNotIn(Collection<ReservationStatus> statuses);

    Optional<Reservation> findByQrCodeIgnoreCase(String qrCode);

    @Query("""
            select r from Reservation r
            join r.warehouse w
            join w.city c
            left join w.zone z
            join r.user u
            where (:status is null or r.status = :status)
              and (
                    :query is null
                    or lower(r.qrCode) like lower(concat('%', :query, '%'))
                    or str(r.id) = :query
                    or lower(w.name) like lower(concat('%', :query, '%'))
                    or lower(c.name) like lower(concat('%', :query, '%'))
                    or lower(coalesce(z.name, '')) like lower(concat('%', :query, '%'))
                    or lower(concat(coalesce(u.firstName, ''), ' ', coalesce(u.lastName, ''))) like lower(concat('%', :query, '%'))
                 )
            order by r.createdAt desc
            """)
    Page<Reservation> searchPrivileged(
            @Param("status") ReservationStatus status,
            @Param("query") String query,
            Pageable pageable
    );

    @Query("""
            select r from Reservation r
            join r.warehouse w
            join w.city c
            left join w.zone z
            join r.user u
            where r.user.id = :userId
              and (:status is null or r.status = :status)
              and (
                    :query is null
                    or lower(r.qrCode) like lower(concat('%', :query, '%'))
                    or str(r.id) = :query
                    or lower(w.name) like lower(concat('%', :query, '%'))
                    or lower(c.name) like lower(concat('%', :query, '%'))
                    or lower(coalesce(z.name, '')) like lower(concat('%', :query, '%'))
                    or lower(concat(coalesce(u.firstName, ''), ' ', coalesce(u.lastName, ''))) like lower(concat('%', :query, '%'))
                 )
            order by r.createdAt desc
            """)
    Page<Reservation> searchByUser(
            @Param("userId") Long userId,
            @Param("status") ReservationStatus status,
            @Param("query") String query,
            Pageable pageable
    );

    @Query("""
            select r from Reservation r
            join r.warehouse w
            join w.city c
            left join w.zone z
            join r.user u
            where w.id in :warehouseIds
              and (:status is null or r.status = :status)
              and (
                    :query is null
                    or lower(r.qrCode) like lower(concat('%', :query, '%'))
                    or str(r.id) = :query
                    or lower(w.name) like lower(concat('%', :query, '%'))
                    or lower(c.name) like lower(concat('%', :query, '%'))
                    or lower(coalesce(z.name, '')) like lower(concat('%', :query, '%'))
                    or lower(concat(coalesce(u.firstName, ''), ' ', coalesce(u.lastName, ''))) like lower(concat('%', :query, '%'))
                 )
            order by r.createdAt desc
            """)
    Page<Reservation> searchByWarehouses(
            @Param("warehouseIds") Collection<Long> warehouseIds,
            @Param("status") ReservationStatus status,
            @Param("query") String query,
            Pageable pageable
    );

    @Query("""
            select r from Reservation r
            where r.status in :activeStatuses
            and r.endAt < :now
            order by r.endAt asc
            """)
    List<Reservation> findActiveReservationsForSurchargeProcessing(
            @Param("activeStatuses") Collection<ReservationStatus> activeStatuses,
            @Param("now") Instant now
    );

    @Query(nativeQuery = true, value = """
            SELECT COUNT(*) as count,
                   SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) as completed,
                   SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END) as cancelled,
                   COUNT(DISTINCT user_id) as unique_clients
            FROM reservations
            WHERE created_at BETWEEN :startAt AND :endAt
            """)
    Map<String, Object> getReservationSummary(
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt
    );

    @Query(nativeQuery = true, value = """
            SELECT w.id, w.name as warehouse_name, COUNT(*) as interaction_count,
                   SUM(CASE WHEN r.status = 'COMPLETED' THEN 1 ELSE 0 END) as completed_count,
                   SUM(CASE WHEN r.status = 'CANCELLED' THEN 1 ELSE 0 END) as cancelled_count
            FROM reservations r
            JOIN warehouses w ON r.warehouse_id = w.id
            WHERE r.created_at BETWEEN :startAt AND :endAt
            GROUP BY w.id, w.name
            ORDER BY interaction_count DESC
            LIMIT :limit
            """)
    List<Map<String, Object>> getWarehouseRanking(
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt,
            @Param("limit") int limit
    );
}
