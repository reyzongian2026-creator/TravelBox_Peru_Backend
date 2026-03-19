package com.tuempresa.storage.reports.infrastructure.in.web;

import com.tuempresa.storage.reports.application.dto.AdminDashboardResponse;
import com.tuempresa.storage.reports.application.usecase.AdminDashboardService;
import com.tuempresa.storage.shared.infrastructure.reactive.ReactiveBlockingExecutor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('ADMIN')")
public class DashboardCompatibilityController {

    private final AdminDashboardService adminDashboardService;
    private final ReactiveBlockingExecutor reactiveBlockingExecutor;

    public DashboardCompatibilityController(
            AdminDashboardService adminDashboardService,
            ReactiveBlockingExecutor reactiveBlockingExecutor
    ) {
        this.adminDashboardService = adminDashboardService;
        this.reactiveBlockingExecutor = reactiveBlockingExecutor;
    }

    @GetMapping({"/dashboard", "/admin-dashboard"})
    public Mono<AdminDashboardResponse> dashboardCompatibility(@RequestParam(defaultValue = "month") String period) {
        return reactiveBlockingExecutor.call(() -> adminDashboardService.dashboard(period));
    }
}
