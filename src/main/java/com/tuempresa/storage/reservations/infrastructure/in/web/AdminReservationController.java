package com.tuempresa.storage.reservations.infrastructure.in.web;

import com.tuempresa.storage.reservations.application.dto.BulkOperationResponse;
import com.tuempresa.storage.reservations.application.dto.BulkReservationStatusRequest;
import com.tuempresa.storage.reservations.application.dto.ReservationExportRow;
import com.tuempresa.storage.reservations.application.dto.ReservationResponse;
import com.tuempresa.storage.reservations.application.usecase.ReservationService;
import com.tuempresa.storage.shared.application.usecase.ExcelExportService;
import com.tuempresa.storage.shared.domain.exception.ApiException;
import com.tuempresa.storage.shared.infrastructure.reactive.ReactiveBlockingExecutor;
import com.tuempresa.storage.shared.infrastructure.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

@RestController
@RequestMapping({"/api/v1/admin/reservations", "/api/v1/admin/reservas"})
@PreAuthorize("hasRole('ADMIN')")
public class AdminReservationController {

    private final ReservationService reservationService;
    private final SecurityUtils securityUtils;
    private final ReactiveBlockingExecutor reactiveBlockingExecutor;
    private final ExcelExportService excelExportService;

    public AdminReservationController(
            ReservationService reservationService,
            SecurityUtils securityUtils,
            ReactiveBlockingExecutor reactiveBlockingExecutor,
            ExcelExportService excelExportService
    ) {
        this.reservationService = reservationService;
        this.securityUtils = securityUtils;
        this.reactiveBlockingExecutor = reactiveBlockingExecutor;
        this.excelExportService = excelExportService;
    }

    @GetMapping({"", "/", "/list"})
    public Mono<List<ReservationResponse>> list() {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                        () -> reservationService.list(currentUser)
                ));
    }

    @PatchMapping("/bulk/status")
    public Mono<BulkOperationResponse> bulkUpdateStatus(@Valid @RequestBody BulkReservationStatusRequest request) {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(() -> {
                    return bulkUpdateStatusInternal(request.ids(), request.status());
                }));
    }

    @GetMapping(value = "/export", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public Mono<ResponseEntity<byte[]>> exportReservations() {
        return reactiveBlockingExecutor.call(() -> {
            List<ReservationExportRow> rows = reservationService.exportReservations();
            List<String> headers = ReservationExportRow.headers();
            List<Function<ReservationExportRow, String>> mappers = ReservationExportRow.dtoColumnMappers();
            byte[] excel;
            try {
                excel = excelExportService.exportToExcel(
                        "Reservas",
                        "Exportacion de Reservas",
                        headers,
                        rows,
                        mappers
                );
            } catch (IOException e) {
                throw new ApiException(
                        org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                        "RESERVATION_EXPORT_FAILED",
                        "No se pudo generar el reporte Excel de reservas."
                );
            }
            String filename = "reservations_export_" + Instant.now().toEpochMilli() + ".xlsx";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(excel);
        });
    }

    private BulkOperationResponse bulkUpdateStatusInternal(Set<Long> ids, com.tuempresa.storage.reservations.domain.ReservationStatus targetStatus) {
        int processed = ids.size();
        int succeeded = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();
        for (Long id : ids) {
            try {
                reservationService.moveStatus(id, targetStatus);
                succeeded++;
            } catch (ApiException e) {
                failed++;
                errors.add("ID " + id + ": " + e.getMessage());
            } catch (DataIntegrityViolationException e) {
                failed++;
                errors.add("ID " + id + ": Violacion de integridad de datos");
            } catch (Exception e) {
                failed++;
                errors.add("ID " + id + ": " + e.getMessage());
            }
        }
        if (failed > 0) {
            return BulkOperationResponse.partial(processed, succeeded, failed, "Actualizacion bulk de estado");
        }
        return BulkOperationResponse.success(processed, "Actualizacion bulk de estado");
    }
}
