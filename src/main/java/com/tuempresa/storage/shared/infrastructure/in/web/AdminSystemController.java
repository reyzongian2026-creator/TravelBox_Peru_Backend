package com.tuempresa.storage.shared.infrastructure.in.web;

import com.tuempresa.storage.shared.application.usecase.AuditLogService;
import com.tuempresa.storage.shared.application.usecase.AuditLogService.AuditEntry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/v1/admin/system", "/api/v1/admin/sistema"})
@PreAuthorize("hasRole('ADMIN')")
public class AdminSystemController {

    private final AuditLogService auditLogService;
    
    @Value("${spring.application.name:storage}")
    private String applicationName;

    @Value("${server.port:8080}")
    private String serverPort;

    public AdminSystemController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping("/health")
    public SystemHealthResponse getHealth() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory();
        
        int availableProcessors = runtime.availableProcessors();
        double loadAverage = ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
        
        return new SystemHealthResponse(
                applicationName,
                "UP",
                serverPort,
                Instant.now(),
                new MemoryInfo(
                        usedMemory / (1024 * 1024),
                        maxMemory / (1024 * 1024),
                        freeMemory / (1024 * 1024),
                        ((double) usedMemory / maxMemory) * 100
                ),
                new CpuInfo(
                        availableProcessors,
                        loadAverage >= 0 ? loadAverage : -1
                )
        );
    }

    @GetMapping("/audit-log")
    public List<AuditEntry> getAuditLog(
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long entityId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String performedBy
    ) {
        int safeLimit = Math.min(Math.max(limit, 1), 1000);
        
        if (entityType != null && entityId != null) {
            return auditLogService.getEntriesByEntity(entityType, entityId, safeLimit);
        }
        if (action != null) {
            return auditLogService.getEntriesByAction(action, safeLimit);
        }
        if (performedBy != null) {
            return auditLogService.getEntriesByUser(performedBy, safeLimit);
        }
        return auditLogService.getEntries(safeLimit);
    }

    public record SystemHealthResponse(
            String application,
            String status,
            String port,
            Instant timestamp,
            MemoryInfo memory,
            CpuInfo cpu
    ) {}

    public record MemoryInfo(
            long usedMB,
            long maxMB,
            long freeMB,
            double usagePercent
    ) {}

    public record CpuInfo(
            int availableProcessors,
            double loadAverage
    ) {}
}
