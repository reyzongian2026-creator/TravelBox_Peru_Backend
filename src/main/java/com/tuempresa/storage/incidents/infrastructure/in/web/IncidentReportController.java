package com.tuempresa.storage.incidents.infrastructure.in.web;

import com.tuempresa.storage.incidents.application.dto.IncidentSummaryResponse;
import com.tuempresa.storage.incidents.application.usecase.IncidentReportService;
import com.tuempresa.storage.incidents.application.usecase.IncidentReportService.ReportResult;
import com.tuempresa.storage.incidents.application.usecase.IncidentService;
import com.tuempresa.storage.incidents.domain.IncidentStatus;
import com.tuempresa.storage.reservations.domain.ReservationStatus;
import com.tuempresa.storage.shared.infrastructure.reactive.ReactiveBlockingExecutor;
import com.tuempresa.storage.shared.infrastructure.security.AuthUserPrincipal;
import com.tuempresa.storage.shared.infrastructure.security.SecurityUtils;
import com.tuempresa.storage.users.infrastructure.out.persistence.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/v1/incidents/reports")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPPORT')")
public class IncidentReportController {

    private final IncidentService incidentService;
    private final IncidentReportService reportService;
    private final SecurityUtils securityUtils;
    private final ReactiveBlockingExecutor reactiveBlockingExecutor;
    private final UserRepository userRepository;

    public IncidentReportController(
            IncidentService incidentService,
            IncidentReportService reportService,
            SecurityUtils securityUtils,
            ReactiveBlockingExecutor reactiveBlockingExecutor,
            UserRepository userRepository
    ) {
        this.incidentService = incidentService;
        this.reportService = reportService;
        this.securityUtils = securityUtils;
        this.reactiveBlockingExecutor = reactiveBlockingExecutor;
        this.userRepository = userRepository;
    }

    public record IncidentExportRequest(
            String status,
            String query,
            Long reservationId
    ) {}

    public record ReportResponse(
            String fileName,
            String downloadUrl,
            String format,
            int recordCount
    ) {}

    @PostMapping("/export/pdf")
    public Mono<ReportResponse> exportPdf(@RequestBody(required = false) IncidentExportRequest request) {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(user -> reactiveBlockingExecutor.call(() -> {
                    List<IncidentSummaryResponse> incidents = getIncidentsForExport(user, request);
                    String generatedBy = userRepository.findById(user.getId())
                            .map(u -> u.getFullName())
                            .orElse("Unknown");
                    ReportResult result = reportService.generatePdfReport(incidents, generatedBy);
                    return new ReportResponse(result.fileName(), result.downloadUrl(), result.format(), incidents.size());
                }));
    }

    @PostMapping("/export/excel")
    public Mono<ReportResponse> exportExcel(@RequestBody(required = false) IncidentExportRequest request) {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(user -> reactiveBlockingExecutor.call(() -> {
                    List<IncidentSummaryResponse> incidents = getIncidentsForExport(user, request);
                    String generatedBy = userRepository.findById(user.getId())
                            .map(u -> u.getFullName())
                            .orElse("Unknown");
                    ReportResult result = reportService.generateExcelReport(incidents, generatedBy);
                    return new ReportResponse(result.fileName(), result.downloadUrl(), result.format(), incidents.size());
                }));
    }

    private List<IncidentSummaryResponse> getIncidentsForExport(AuthUserPrincipal principal, IncidentExportRequest request) {
        IncidentStatus status = null;
        String query = null;
        Long reservationId = null;

        if (request != null) {
            if (request.status() != null && !request.status().isEmpty() && !"ALL".equalsIgnoreCase(request.status())) {
                try {
                    status = IncidentStatus.valueOf(request.status().toUpperCase());
                } catch (IllegalArgumentException ignored) {}
            }
            query = request.query();
            reservationId = request.reservationId();
        }

        return incidentService.list(principal, status, query, reservationId);
    }
}
