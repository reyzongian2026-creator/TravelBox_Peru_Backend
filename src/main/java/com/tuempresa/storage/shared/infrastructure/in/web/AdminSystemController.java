package com.tuempresa.storage.shared.infrastructure.in.web;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuempresa.storage.shared.application.usecase.AuditLogService;
import com.tuempresa.storage.shared.domain.audit.AuditLogEntry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.io.InputStream;
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
    private final ObjectMapper objectMapper;
    
    @Value("${spring.application.name:storage}")
    private String applicationName;

    @Value("${server.port:8080}")
    private String serverPort;

    public AdminSystemController(AuditLogService auditLogService, ObjectMapper objectMapper) {
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
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
            AdminAzureCostSnapshot snapshot = loadCostSnapshot();

            return new AzureResourcesResponse(
                    snapshot != null ? snapshot.generatedAt() : LocalDate.now().toString(),
                    List.of(
                            new AzureResource(
                                    "App Service - Backend",
                                    "travelbox-backend-bs",
                                    "B2 Linux",
                                    "Running",
                                    Map.of(
                                            "cpuPercent", Math.min(loadAverage * 25, 100),
                                            "memoryMB", usedMemory / (1024 * 1024),
                                            "memoryTotalMB", runtime.totalMemory() / (1024 * 1024)
                                    ),
                                    null,
                                    "https://portal.azure.com/#resource/subscriptions/33815caa-4cfb-4a9e-b60a-8fee5caa2b08/resourceGroups/travelbox-peru-bs-rg/providers/Microsoft.Web/sites/travelbox-backend-bs"
                            ),
                            new AzureResource(
                                    "Static Web App - Frontend",
                                    "travelbox-frontend",
                                    "Free",
                                    "Active",
                                    Map.of(
                                            "region", "West US 2",
                                            "customDomain", "www.inkavoy.pe"
                                    ),
                                    null,
                                    "https://portal.azure.com/#resource/subscriptions/33815caa-4cfb-4a9e-b60a-8fee5caa2b08/resourceGroups/travelbox-peru-rg/providers/Microsoft.Web/staticSites/travelbox-frontend"
                            ),
                            new AzureResource(
                                    "PostgreSQL Flexible",
                                    "travelbox-peru-db-bs",
                                    "Standard_B1ms",
                                    "Running",
                                    Map.of(
                                            "storageGB", 32,
                                            "version", "16",
                                            "backupDays", 7
                                    ),
                                    null,
                                    "https://portal.azure.com/#resource/subscriptions/33815caa-4cfb-4a9e-b60a-8fee5caa2b08/resourceGroups/travelbox-peru-bs-rg/providers/Microsoft.DBforPostgreSQL/flexibleServers/travelbox-peru-db-bs"
                            ),
                            new AzureResource(
                                    "Azure AI - Translator",
                                    "travelbox-translator",
                                    "S1",
                                    "Running",
                                    Map.of(
                                            "scope", "Usage-based"
                                    ),
                                    null,
                                    "https://portal.azure.com/#resource/subscriptions/33815caa-4cfb-4a9e-b60a-8fee5caa2b08/resourceGroups/travelbox-rg/providers/Microsoft.CognitiveServices/accounts/travelbox-translator"
                            ),
                            new AzureResource(
                                    "Azure Maps",
                                    "travelbox-maps",
                                    "G2",
                                    "Running",
                                    Map.of(
                                            "scope", "Usage-based"
                                    ),
                                    null,
                                    "https://portal.azure.com/#resource/subscriptions/33815caa-4cfb-4a9e-b60a-8fee5caa2b08/resourceGroups/travelbox-rg/providers/Microsoft.Maps/accounts/travelbox-maps"
                            ),
                            new AzureResource(
                                    "Key Vault",
                                    "kvtravelboxpebs",
                                    "Standard",
                                    "Running",
                                    Map.of(
                                            "secretCount", "~22 app settings",
                                            "softDeleteDays", 90
                                    ),
                                    null,
                                    "https://portal.azure.com/#resource/subscriptions/33815caa-4cfb-4a9e-b60a-8fee5caa2b08/resourceGroups/travelbox_kv_bs/providers/Microsoft.KeyVault/vaults/kvtravelboxpebs"
                            )
                    ),
                    buildEstimatedCosts(snapshot)
            );
        });
    }

    private EstimatedCosts buildEstimatedCosts(AdminAzureCostSnapshot snapshot) {
        if (snapshot != null && snapshot.dashboard() != null && snapshot.dashboard().items() != null && !snapshot.dashboard().items().isEmpty()) {
            List<CostItem> items = snapshot.dashboard().items().stream()
                    .map(item -> new CostItem(item.service(), item.sku(), item.amount(), item.period()))
                    .toList();
            return new EstimatedCosts(snapshot.dashboard().currency(), items, snapshot.dashboard().totalMonthlyUsd());
        }

        return new EstimatedCosts(
                "USD",
                List.of(
                        new CostItem("App Service Backend", "B2 Linux", 133.59, "monthly"),
                        new CostItem("Static Web App Frontend", "Free", 0.00, "monthly"),
                        new CostItem("PostgreSQL Flexible", "Standard_B1ms + 32GB", 32.54, "monthly"),
                        new CostItem("Azure AI Translator", "S1", 0.00, "usage-based"),
                        new CostItem("Azure Maps", "G2", 0.00, "usage-based"),
                        new CostItem("Key Vault", "Standard", 0.00, "monthly"),
                        new CostItem("Blob Storage", "Current footprint", 0.00, "monthly")
                ),
                166.13
        );
    }

    private AdminAzureCostSnapshot loadCostSnapshot() {
        ClassPathResource resource = new ClassPathResource("admin/azure-cost-snapshot.json");
        if (!resource.exists()) {
            return null;
        }

        try (InputStream inputStream = resource.getInputStream()) {
            return objectMapper.readValue(inputStream, AdminAzureCostSnapshot.class);
        } catch (Exception ex) {
            return null;
        }
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AdminAzureCostSnapshot(
            String generatedAt,
            DashboardCosts dashboard
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DashboardCosts(
            String currency,
            double totalMonthlyUsd,
            List<DashboardCostItem> items
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DashboardCostItem(
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
