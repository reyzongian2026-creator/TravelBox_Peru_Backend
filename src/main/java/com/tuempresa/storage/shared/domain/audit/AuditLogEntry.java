package com.tuempresa.storage.shared.domain.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "audit_log", indexes = {
        @Index(name = "idx_audit_entity", columnList = "entity_type, entity_id"),
        @Index(name = "idx_audit_action", columnList = "action"),
        @Index(name = "idx_audit_performed_by", columnList = "performed_by"),
        @Index(name = "idx_audit_performed_at", columnList = "performed_at")
})
public class AuditLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String action;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 50)
    private EntityType entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(columnDefinition = "TEXT")
    private String details;

    @Column(name = "performed_by", length = 100)
    private String performedBy;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "performed_at", nullable = false)
    private Instant performedAt;

    public enum EntityType {
        USER, RESERVATION, INVENTORY, WAREHOUSE, PAYMENT, REPORT,
        FILE_UPLOAD, FILE_DELETE, PROFILE, DOCUMENT, EVIDENCE,
        AUTH, SYSTEM
    }

    public static AuditLogEntry create(
            String action,
            EntityType entityType,
            String entityId,
            String details,
            String performedBy,
            String ipAddress,
            String userAgent
    ) {
        AuditLogEntry entry = new AuditLogEntry();
        entry.action = action;
        entry.entityType = entityType;
        entry.entityId = parseEntityId(entityId);
        entry.details = details;
        entry.performedBy = performedBy;
        entry.ipAddress = ipAddress;
        entry.userAgent = userAgent;
        entry.performedAt = Instant.now();
        return entry;
    }

    private static Long parseEntityId(String entityId) {
        if (entityId == null || entityId.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(entityId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public Long getId() {
        return id;
    }

    public String getAction() {
        return action;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public String getDetails() {
        return details;
    }

    public String getPerformedBy() {
        return performedBy;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public Instant getPerformedAt() {
        return performedAt;
    }
}