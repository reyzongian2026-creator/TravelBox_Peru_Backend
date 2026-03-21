package com.tuempresa.storage.shared.application.usecase;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class AuditLogService {

    private final ConcurrentMap<Long, AuditEntry> entries = new ConcurrentHashMap<>();
    private final AtomicLong counter = new AtomicLong(0L);

    public void log(String action, String entityType, Long entityId, String details, String performedBy) {
        Long id = counter.incrementAndGet();
        AuditEntry entry = new AuditEntry(
                id,
                Instant.now(),
                action,
                entityType,
                entityId,
                details,
                performedBy
        );
        entries.put(id, entry);
        if (entries.size() > 10000) {
            cleanupOldEntries();
        }
    }

    public List<AuditEntry> getEntries(int limit) {
        return entries.values().stream()
                .sorted((a, b) -> b.timestamp().compareTo(a.timestamp()))
                .limit(limit)
                .toList();
    }

    public List<AuditEntry> getEntriesByEntity(String entityType, Long entityId, int limit) {
        return entries.values().stream()
                .filter(e -> e.entityType().equals(entityType) && e.entityId().equals(entityId))
                .sorted((a, b) -> b.timestamp().compareTo(a.timestamp()))
                .limit(limit)
                .toList();
    }

    public List<AuditEntry> getEntriesByAction(String action, int limit) {
        return entries.values().stream()
                .filter(e -> e.action().equals(action))
                .sorted((a, b) -> b.timestamp().compareTo(a.timestamp()))
                .limit(limit)
                .toList();
    }

    public List<AuditEntry> getEntriesByUser(String performedBy, int limit) {
        return entries.values().stream()
                .filter(e -> e.performedBy() != null && e.performedBy().equals(performedBy))
                .sorted((a, b) -> b.timestamp().compareTo(a.timestamp()))
                .limit(limit)
                .toList();
    }

    private void cleanupOldEntries() {
        List<Long> idsToRemove = entries.values().stream()
                .sorted((a, b) -> a.timestamp().compareTo(b.timestamp()))
                .limit(5000)
                .map(AuditEntry::id)
                .toList();
        idsToRemove.forEach(entries::remove);
    }

    public record AuditEntry(
            Long id,
            Instant timestamp,
            String action,
            String entityType,
            Long entityId,
            String details,
            String performedBy
    ) {}
}
