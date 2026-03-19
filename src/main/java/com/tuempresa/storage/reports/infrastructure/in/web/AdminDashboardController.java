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
    public Mono<AdminDashboardResponse> dashboard(@RequestParam(defaultValue = "month") String period) {
        return reactiveBlockingExecutor.call(() -> adminDashboardService.dashboard(period));
    }
}
