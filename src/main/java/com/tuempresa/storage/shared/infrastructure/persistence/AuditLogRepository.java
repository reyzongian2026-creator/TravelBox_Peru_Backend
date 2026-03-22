package com.tuempresa.storage.shared.infrastructure.persistence;

import com.tuempresa.storage.shared.domain.audit.AuditLogEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLogEntry, Long> {

    Page<AuditLogEntry> findByEntityTypeAndEntityId(
            AuditLogEntry.EntityType entityType,
            Long entityId,
            Pageable pageable
    );

    Page<AuditLogEntry> findByAction(String action, Pageable pageable);

    Page<AuditLogEntry> findByPerformedBy(String performedBy, Pageable pageable);

    @Query("""
            SELECT a FROM AuditLogEntry a
            WHERE a.performedAt BETWEEN :startDate AND :endDate
            ORDER BY a.performedAt DESC
            """)
    Page<AuditLogEntry> findByDateRange(
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            Pageable pageable
    );

    @Query("""
            SELECT a FROM AuditLogEntry a
            WHERE (:entityType IS NULL OR a.entityType = :entityType)
              AND (:entityId IS NULL OR a.entityId = :entityId)
              AND (:action IS NULL OR a.action = :action)
              AND (:performedBy IS NULL OR a.performedBy = :performedBy)
              AND (:startDate IS NULL OR a.performedAt >= :startDate)
              AND (:endDate IS NULL OR a.performedAt <= :endDate)
            ORDER BY a.performedAt DESC
            """)
    Page<AuditLogEntry> search(
            @Param("entityType") AuditLogEntry.EntityType entityType,
            @Param("entityId") Long entityId,
            @Param("action") String action,
            @Param("performedBy") String performedBy,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            Pageable pageable
    );

    List<AuditLogEntry> findTop100ByOrderByPerformedAtDesc();

    @Query("DELETE FROM AuditLogEntry a WHERE a.performedAt < :cutoffDate")
    void deleteOlderThan(@Param("cutoffDate") Instant cutoffDate);
}