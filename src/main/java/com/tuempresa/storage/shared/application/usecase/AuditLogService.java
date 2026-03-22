package com.tuempresa.storage.shared.application.usecase;

import com.tuempresa.storage.shared.domain.audit.AuditLogEntry;
import com.tuempresa.storage.shared.infrastructure.persistence.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
public class AuditLogService {

    private static final Logger LOG = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository repository;
    private final int retentionDays;

    public AuditLogService(
            AuditLogRepository repository,
            @Value("${app.audit.retention-days:90}") int retentionDays
    ) {
        this.repository = repository;
        this.retentionDays = retentionDays;
    }

    @Async
    public void logAsync(String action, AuditLogEntry.EntityType entityType, String entityId,
                         String details, String performedBy) {
        try {
            log(action, entityType, entityId, details, performedBy);
        } catch (Exception e) {
            LOG.error("Failed to save audit log asynchronously: {}", e.getMessage());
        }
    }

    public void log(String action, AuditLogEntry.EntityType entityType, String entityId,
                    String details, String performedBy) {
        String ipAddress = extractIpAddress();
        String userAgent = extractUserAgent();

        AuditLogEntry entry = AuditLogEntry.create(
                action,
                entityType,
                entityId,
                details,
                performedBy,
                ipAddress,
                userAgent
        );

        repository.save(entry);
        LOG.debug("Audit log saved: {} - {} - {}", action, entityType, entityId);
    }

    public void logFileUpload(String filename, String category, String performedBy) {
        log("FILE_UPLOAD", AuditLogEntry.EntityType.FILE_UPLOAD,
                filename, "Category: " + category, performedBy);
    }

    public void logFileDelete(String filename, String category, String performedBy) {
        log("FILE_DELETE", AuditLogEntry.EntityType.FILE_DELETE,
                filename, "Category: " + category, performedBy);
    }

    public void logFileDownload(String filename, String category, String performedBy) {
        log("FILE_DOWNLOAD", AuditLogEntry.EntityType.FILE_UPLOAD,
                filename, "Category: " + category, performedBy);
    }

    public Page<AuditLogEntry> getEntries(Pageable pageable) {
        return repository.findAll(pageable);
    }

    public Page<AuditLogEntry> getEntriesByEntity(AuditLogEntry.EntityType entityType,
                                                   String entityId, Pageable pageable) {
        return repository.findByEntityTypeAndEntityId(entityType, entityId, pageable);
    }

    public Page<AuditLogEntry> getEntriesByAction(String action, Pageable pageable) {
        return repository.findByAction(action, pageable);
    }

    public Page<AuditLogEntry> getEntriesByUser(String performedBy, Pageable pageable) {
        return repository.findByPerformedBy(performedBy, pageable);
    }

    public Page<AuditLogEntry> getEntriesByDateRange(Instant startDate, Instant endDate,
                                                      Pageable pageable) {
        return repository.findByDateRange(startDate, endDate, pageable);
    }

    public Page<AuditLogEntry> search(AuditLogEntry.EntityType entityType, String entityId,
                                       String action, String performedBy,
                                       Instant startDate, Instant endDate,
                                       Pageable pageable) {
        return repository.search(entityType, entityId, action, performedBy,
                startDate, endDate, pageable);
    }

    public List<AuditLogEntry> getRecentEntries(int limit) {
        return repository.findTop100ByOrderByPerformedAtDesc();
    }

    public Optional<AuditLogEntry> getById(Long id) {
        return repository.findById(id);
    }

    @Async
    public void cleanupOldEntriesAsync() {
        cleanupOldEntries();
    }

    public void cleanupOldEntries() {
        Instant cutoffDate = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        repository.deleteOlderThan(cutoffDate);
        LOG.info("Cleaned up audit logs older than {}", cutoffDate);
    }

    private String extractIpAddress() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return null;
            }
            HttpServletRequest request = attrs.getRequest();
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                return xForwardedFor.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        } catch (Exception e) {
            return null;
        }
    }

    private String extractUserAgent() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return null;
            }
            HttpServletRequest request = attrs.getRequest();
            return request.getHeader("User-Agent");
        } catch (Exception e) {
            return null;
        }
    }
}