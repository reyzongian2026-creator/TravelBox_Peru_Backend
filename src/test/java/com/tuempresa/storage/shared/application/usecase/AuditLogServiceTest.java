package com.tuempresa.storage.shared.application.usecase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AuditLogServiceTest {

    private AuditLogService auditLogService;

    @BeforeEach
    void setUp() {
        auditLogService = new AuditLogService();
    }

    @Test
    void shouldLogEntry() {
        auditLogService.log("CREATE", "User", 1L, "User created", "admin@test.com");
        
        List<AuditLogService.AuditEntry> entries = auditLogService.getEntries(10);
        
        assertEquals(1, entries.size());
        assertEquals("CREATE", entries.get(0).action());
        assertEquals("User", entries.get(0).entityType());
        assertEquals(1L, entries.get(0).entityId());
        assertEquals("admin@test.com", entries.get(0).performedBy());
    }

    @Test
    void shouldFilterByEntityTypeAndId() {
        auditLogService.log("CREATE", "User", 1L, "User 1", "admin@test.com");
        auditLogService.log("UPDATE", "User", 1L, "User 1 updated", "admin@test.com");
        auditLogService.log("CREATE", "Warehouse", 1L, "Warehouse 1", "admin@test.com");
        
        List<AuditLogService.AuditEntry> userEntries = auditLogService.getEntriesByEntity("User", 1L, 10);
        
        assertEquals(2, userEntries.size());
        assertTrue(userEntries.stream().allMatch(e -> e.entityType().equals("User")));
        assertTrue(userEntries.stream().allMatch(e -> e.entityId().equals(1L)));
    }

    @Test
    void shouldFilterByAction() {
        auditLogService.log("CREATE", "User", 1L, "User created", "admin@test.com");
        auditLogService.log("DELETE", "User", 2L, "User deleted", "admin@test.com");
        auditLogService.log("UPDATE", "User", 3L, "User updated", "admin@test.com");
        
        List<AuditLogService.AuditEntry> createEntries = auditLogService.getEntriesByAction("CREATE", 10);
        
        assertEquals(1, createEntries.size());
        assertEquals("CREATE", createEntries.get(0).action());
    }

    @Test
    void shouldFilterByUser() {
        auditLogService.log("CREATE", "User", 1L, "User 1", "admin@test.com");
        auditLogService.log("CREATE", "Warehouse", 1L, "Warehouse 1", "admin@test.com");
        auditLogService.log("CREATE", "Reservation", 1L, "Reservation 1", "other@test.com");
        
        List<AuditLogService.AuditEntry> adminEntries = auditLogService.getEntriesByUser("admin@test.com", 10);
        
        assertEquals(2, adminEntries.size());
        assertTrue(adminEntries.stream().allMatch(e -> "admin@test.com".equals(e.performedBy())));
    }

    @Test
    void shouldLimitResults() {
        for (int i = 0; i < 20; i++) {
            auditLogService.log("CREATE", "User", (long) i, "User " + i, "admin@test.com");
        }
        
        List<AuditLogService.AuditEntry> entries = auditLogService.getEntries(5);
        
        assertEquals(5, entries.size());
    }

    @Test
    void shouldReturnNewestFirst() throws InterruptedException {
        auditLogService.log("FIRST", "User", 1L, "First", "admin@test.com");
        Thread.sleep(10);
        auditLogService.log("SECOND", "User", 2L, "Second", "admin@test.com");
        
        List<AuditLogService.AuditEntry> entries = auditLogService.getEntries(10);
        
        assertEquals("SECOND", entries.get(0).action());
        assertEquals("FIRST", entries.get(1).action());
    }

    @Test
    void shouldCleanupOldEntriesWhenExceedingLimit() {
        for (int i = 0; i < 10; i++) {
            auditLogService.log("CREATE", "User", (long) i, "User " + i, "admin@test.com");
        }
        auditLogService.log("NEWEST", "User", 999L, "Newest", "admin@test.com");
        
        List<AuditLogService.AuditEntry> entries = auditLogService.getEntries(10);
        
        assertEquals(10, entries.size());
        assertTrue(entries.size() < 11, "Should return limited entries");
    }

    @Test
    void shouldHandleEmptyResult() {
        List<AuditLogService.AuditEntry> entries = auditLogService.getEntries(10);
        assertTrue(entries.isEmpty());
    }

    @Test
    void shouldReturnEmptyForNonMatchingFilter() {
        auditLogService.log("CREATE", "User", 1L, "User 1", "admin@test.com");
        
        List<AuditLogService.AuditEntry> entries = auditLogService.getEntriesByEntity("Warehouse", 1L, 10);
        
        assertTrue(entries.isEmpty());
    }
}
