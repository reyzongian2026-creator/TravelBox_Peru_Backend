package com.tuempresa.storage.shared.infrastructure.in.web;

import com.tuempresa.storage.shared.application.i18n.I18nReportService;
import com.tuempresa.storage.shared.application.i18n.I18nReportService.I18nErrorEntry;
import com.tuempresa.storage.shared.application.i18n.I18nReportService.I18nReportSummary;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping({"/api/v1/admin/i18n-report", "/api/v1/admin/reporte-i18n"})
@PreAuthorize("hasRole('ADMIN')")
public class I18nReportController {

    private final I18nReportService i18nReportService;

    public I18nReportController(I18nReportService i18nReportService) {
        this.i18nReportService = i18nReportService;
    }

    @PostMapping("/errors")
    public Mono<ResponseEntity<Void>> reportErrors(
            @RequestBody I18nErrorReportRequest request,
            @AuthenticationPrincipal(expression = "username") String username
    ) {
        return Mono.fromSupplier(() -> {
            if (request.keys() != null && !request.keys().isEmpty()) {
                String reportedBy = username != null ? username : (request.reportedBy() != null ? request.reportedBy() : "anonymous");
                i18nReportService.reportErrors(
                    request.locale() != null ? request.locale() : "unknown",
                    request.keys(),
                    request.context(),
                    reportedBy
                );
            }
            return ResponseEntity.accepted().build();
        });
    }

    @PostMapping("/error")
    public Mono<ResponseEntity<Void>> reportSingleError(
            @RequestBody I18nSingleErrorRequest request,
            @AuthenticationPrincipal(expression = "username") String username
    ) {
        return Mono.fromSupplier(() -> {
            String reportedBy = username != null ? username : (request.reportedBy() != null ? request.reportedBy() : "anonymous");
            i18nReportService.reportError(
                request.locale() != null ? request.locale() : "unknown",
                request.key(),
                request.context(),
                reportedBy
            );
            return ResponseEntity.accepted().build();
        });
    }

    @GetMapping("/errors")
    public Mono<ResponseEntity<List<I18nErrorEntry>>> getAllErrors() {
        return Mono.fromSupplier(() -> ResponseEntity.ok(i18nReportService.getAllErrors()));
    }

    @GetMapping("/errors/locale/{locale}")
    public Mono<ResponseEntity<List<I18nErrorEntry>>> getErrorsByLocale(@PathVariable String locale) {
        return Mono.fromSupplier(() -> ResponseEntity.ok(i18nReportService.getErrorsByLocale(locale)));
    }

    @GetMapping("/errors/type/{type}")
    public Mono<ResponseEntity<List<I18nErrorEntry>>> getErrorsByType(@PathVariable String type) {
        return Mono.fromSupplier(() -> ResponseEntity.ok(i18nReportService.getErrorsByType(type)));
    }

    @GetMapping("/errors/user/{reportedBy}")
    public Mono<ResponseEntity<List<I18nErrorEntry>>> getErrorsByUser(@PathVariable String reportedBy) {
        return Mono.fromSupplier(() -> ResponseEntity.ok(i18nReportService.getErrorsByUser(reportedBy)));
    }

    @GetMapping("/summary")
    public Mono<ResponseEntity<I18nReportSummary>> getSummary() {
        return Mono.fromSupplier(() -> ResponseEntity.ok(i18nReportService.getSummary()));
    }

    @GetMapping(value = "/export/json", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<String>> exportToJson() {
        return Mono.fromSupplier(() -> {
            String json = i18nReportService.exportToJson();
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=i18n_report_" + Instant.now().toEpochMilli() + ".json")
                .body(json);
        });
    }

    @GetMapping(value = "/export/csv", produces = "text/csv")
    public Mono<ResponseEntity<String>> exportToCsv() {
        return Mono.fromSupplier(() -> {
            List<I18nErrorEntry> errors = i18nReportService.getAllErrors();
            StringBuilder csv = new StringBuilder();
            csv.append("ID,Key,Locale,Timestamp,Context,ReportedBy,ErrorType,BackendErrorCode,BackendErrorMessage\n");
            
            for (I18nErrorEntry error : errors) {
                csv.append(escapeCsv(error.id())).append(",");
                csv.append(escapeCsv(error.key())).append(",");
                csv.append(escapeCsv(error.locale())).append(",");
                csv.append(error.timestamp().toString()).append(",");
                csv.append(escapeCsv(error.context())).append(",");
                csv.append(escapeCsv(error.reportedBy())).append(",");
                csv.append(escapeCsv(error.errorType())).append(",");
                csv.append(escapeCsv(error.backendErrorCode().orElse(""))).append(",");
                csv.append(escapeCsv(error.backendErrorMessage().orElse(""))).append("\n");
            }
            
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=i18n_report_" + Instant.now().toEpochMilli() + ".csv")
                .body(csv.toString());
        });
    }

    @GetMapping("/errors/json")
    public Mono<ResponseEntity<String>> getErrorsAsJson() {
        return Mono.fromSupplier(() -> ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .body(i18nReportService.getReportAsJson()));
    }

    @DeleteMapping("/errors")
    public Mono<ResponseEntity<Void>> clearAllErrors() {
        return Mono.fromSupplier(() -> {
            i18nReportService.clearAllErrors();
            return ResponseEntity.noContent().build();
        });
    }

    @DeleteMapping("/errors/locale/{locale}")
    public Mono<ResponseEntity<Void>> clearErrorsByLocale(@PathVariable String locale) {
        return Mono.fromSupplier(() -> {
            i18nReportService.clearErrorsByLocale(locale);
            return ResponseEntity.noContent().build();
        });
    }

    @DeleteMapping("/errors/user/{reportedBy}")
    public Mono<ResponseEntity<Void>> clearErrorsByUser(@PathVariable String reportedBy) {
        return Mono.fromSupplier(() -> {
            i18nReportService.clearErrorsByUser(reportedBy);
            return ResponseEntity.noContent().build();
        });
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    public record I18nErrorReportRequest(
        String locale,
        List<String> keys,
        String context,
        String reportedBy
    ) {}

    public record I18nSingleErrorRequest(
        String locale,
        String key,
        String context,
        String reportedBy
    ) {}
}
