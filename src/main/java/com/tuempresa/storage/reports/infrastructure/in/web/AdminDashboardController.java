package com.tuempresa.storage.reports.infrastructure.in.web;

import com.tuempresa.storage.reports.application.dto.AdminDashboardResponse;
import com.tuempresa.storage.reports.application.usecase.AdminDashboardService;
import com.tuempresa.storage.shared.infrastructure.reactive.ReactiveBlockingExecutor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;
    private final ReactiveBlockingExecutor reactiveBlockingExecutor;

    public AdminDashboardController(
            AdminDashboardService adminDashboardService,
            ReactiveBlockingExecutor reactiveBlockingExecutor
    ) {
        this.adminDashboardService = adminDashboardService;
        this.reactiveBlockingExecutor = reactiveBlockingExecutor;
    }

    @GetMapping({"/dashboard", "/dashboard/summary", "/stats", "/overview"})
    public Mono<ResponseEntity<AdminDashboardResponse>> dashboard(
            @RequestParam(defaultValue = "month") String period
    ) {
        return reactiveBlockingExecutor.call(() -> adminDashboardService.dashboard(period))
                .map(response -> ResponseEntity.ok()
                        .cacheControl(CacheControl.maxAge(Duration.ofMinutes(1)))
                        .body(response));
    }

    @PostMapping("/dashboard/invalidate-cache")
    public Mono<ResponseEntity<Map<String, String>>> invalidateCache(
            @RequestParam(required = false) String period
    ) {
        if (period != null && !period.isBlank()) {
            adminDashboardService.invalidateCache(period);
        } else {
            adminDashboardService.invalidateCache();
        }
        return Mono.just(ResponseEntity.ok(Map.of("status", "cache_invalidated")));
    }
}
