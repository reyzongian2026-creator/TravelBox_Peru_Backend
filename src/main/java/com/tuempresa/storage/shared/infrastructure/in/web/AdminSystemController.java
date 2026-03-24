package com.tuempresa.storage.shared.infrastructure.in.web;

import com.tuempresa.storage.shared.application.usecase.AuditLogService;
import com.tuempresa.storage.shared.domain.audit.AuditLogEntry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.time.LocalDate;
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
    public Mono<SystemHealthResponse> getHealth() {
        return Mono.fromSupplier(() -> {
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
        });
    }

    @GetMapping("/audit-log")
    public reactor.core.publisher.Flux<AuditLogEntry> getAuditLog(
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String performedBy
    ) {
        int safeLimit = Math.min(Math.max(limit, 1), 1000);
        PageRequest pageable = PageRequest.of(0, safeLimit);
        
        if (entityType != null && entityId != null) {
            AuditLogEntry.EntityType type = AuditLogEntry.EntityType.valueOf(entityType.toUpperCase());
            Long parsedEntityId = parseEntityId(entityId);
            return reactor.core.publisher.Flux.fromIterable(auditLogService.getEntriesByEntity(type, parsedEntityId, pageable).getContent());
        }
        if (action != null) {
            return reactor.core.publisher.Flux.fromIterable(auditLogService.getEntriesByAction(action, pageable).getContent());
        }
        if (performedBy != null) {
            return reactor.core.publisher.Flux.fromIterable(auditLogService.getEntriesByUser(performedBy, pageable).getContent());
        }
        return reactor.core.publisher.Flux.fromIterable(auditLogService.getEntries(pageable).getContent());
    }

    @GetMapping("/azure-resources")
    public Mono<AzureResourcesResponse> getAzureResources() {
        return Mono.fromSupplier(() -> {
            Runtime runtime = Runtime.getRuntime();
            long totalMemory = runtime.totalMemory();
            long usedMemory = totalMemory - runtime.freeMemory();
            double loadAverage = ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();

            return new AzureResourcesResponse(
                    LocalDate.now().toString(),
                    List.of(
                            new AzureResource(
                                    "App Service - Backend",
                                    "travelbox-backend-prod",
                                    "P1V2",
                                    "Running",
                                    Map.of(
                                            "cpuPercent", Math.min(loadAverage * 25, 100),
                                            "memoryMB", usedMemory / (1024 * 1024),
                                            "memoryTotalMB", runtime.totalMemory() / (1024 * 1024)
                                    ),
                                    null,
                                    "https://portal.azure.com/#resource/subscriptions/33815caa-4cfb-4a9e-b60a-8fee5caa2b08/resourceGroups/travelbox-peru-rg/providers/Microsoft.Web/sites/travelbox-backend-prod"
                            ),
                            new AzureResource(
                                    "App Service - Frontend",
                                    "travelbox-frontend-prod",
                                    "P1V2",
                                    "Running",
                                    Map.of(
                                            "cpuPercent", Math.min(loadAverage * 20, 100),
                                            "memoryMB", usedMemory / (1024 * 1024) / 2,
                                            "memoryTotalMB", runtime.totalMemory() / (1024 * 1024) / 2
                                    ),
                                    null,
                                    "https://portal.azure.com/#resource/subscriptions/33815caa-4cfb-4a9e-b60a-8fee5caa2b08/resourceGroups/travelbox-peru-rg/providers/Microsoft.Web/sites/travelbox-frontend-prod"
                            ),
                            new AzureResource(
                                    "PostgreSQL Flexible",
                                    "travelbox-peru-db",
                                    "Standard_D2s_v3",
                                    "Running",
                                    Map.of(
                                            "storageGB", 32,
                                            "version", "16",
                                            "connections", 50
                                    ),
                                    null,
                                    "https://portal.azure.com/#resource/subscriptions/33815caa-4cfb-4a9e-b60a-8fee5caa2b08/resourceGroups/travelbox-peru-rg/providers/Microsoft.DBforPostgreSQL/flexibleServers/travelbox-peru-db"
                            ),
                            new AzureResource(
                                    "Azure AI - Translator",
                                    "travelbox-ai",
                                    "S0",
                                    "Running",
                                    Map.of(
                                            "endpoint", "https://travelbox-ai.cognitiveservices.azure.com/"
                                    ),
                                    null,
                                    "https://portal.azure.com/#resource/subscriptions/33815caa-4cfb-4a9e-b60a-8fee5caa2b08/resourceGroups/travelbox-peru-rg/providers/Microsoft.CognitiveServices/accounts/travelbox-ai"
                            ),
                            new AzureResource(
                                    "Azure Maps",
                                    "travelbox-maps",
                                    "Gen2",
                                    "Running",
                                    Map.of(),
                                    null,
                                    "https://portal.azure.com/#resource/subscriptions/33815caa-4cfb-4a9e-b60a-8fee5caa2b08/resourceGroups/travelbox-peru-rg/providers/Microsoft.Maps/accounts/travelbox-maps"
                            ),
                            new AzureResource(
                                    "Key Vault",
                                    "kvtravelboxpe",
                                    "Standard",
                                    "Running",
                                    Map.of(
                                            "secretCount", "~25 secrets",
                                            "softDeleteDays", 90
                                    ),
                                    null,
                                    "https://portal.azure.com/#resource/subscriptions/33815caa-4cfb-4a9e-b60a-8fee5caa2b08/resourceGroups/travelbox-peru-rg/providers/Microsoft.KeyVault/vaults/kvtravelboxpe"
                            )
                    ),
                    new EstimatedCosts(
                            "USD",
                            List.of(
                                    new CostItem("App Service Backend", "P1V2", 65.00, "monthly"),
                                    new CostItem("App Service Frontend", "P1V2", 65.00, "monthly"),
                                    new CostItem("PostgreSQL Flexible", "Standard_D2s_v3", 130.00, "monthly"),
                                    new CostItem("Azure AI Translator", "S0", 25.00, "monthly (estimated)"),
                                    new CostItem("Azure Maps", "Gen2", 10.00, "monthly (estimated)"),
                                    new CostItem("Key Vault", "Standard", 5.00, "monthly"),
                                    new CostItem("Blob Storage", "~50GB", 15.00, "monthly (estimated)")
                            ),
                            315.00
                    )
            );
        });
    }

    public record AzureResourcesResponse(
            String generatedAt,
            List<AzureResource> resources,
            EstimatedCosts estimatedCosts
    ) {}

    public record AzureResource(
            String name,
            String resourceName,
            String sku,
            String status,
            Map<String, Object> metrics,
            String expiresAt,
            String portalUrl
    ) {}

    public record EstimatedCosts(
            String currency,
            List<CostItem> items,
            double totalMonthly
    ) {}

    public record CostItem(
            String service,
            String sku,
            double amount,
            String period
    ) {}

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

    private Long parseEntityId(String entityId) {
        if (entityId == null || entityId.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(entityId);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
