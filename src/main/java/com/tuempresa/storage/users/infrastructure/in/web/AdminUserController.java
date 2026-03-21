package com.tuempresa.storage.users.infrastructure.in.web;

import com.tuempresa.storage.shared.application.usecase.CsvExportService;
import com.tuempresa.storage.shared.infrastructure.reactive.ReactiveBlockingExecutor;
import com.tuempresa.storage.shared.infrastructure.reactive.ReactiveMultipartAdapter;
import com.tuempresa.storage.shared.infrastructure.security.SecurityUtils;
import com.tuempresa.storage.shared.infrastructure.web.PagedResponse;
import com.tuempresa.storage.users.application.dto.AdminUserPagedResponse;
import com.tuempresa.storage.users.application.dto.AdminUserResponse;
import com.tuempresa.storage.users.application.dto.AdminUserSummaryResponse;
import com.tuempresa.storage.users.application.dto.BulkActiveRequest;
import com.tuempresa.storage.users.application.dto.BulkIdsRequest;
import com.tuempresa.storage.users.application.dto.BulkOperationResponse;
import com.tuempresa.storage.users.application.dto.BulkRolesRequest;
import com.tuempresa.storage.users.application.dto.CreateAdminUserRequest;
import com.tuempresa.storage.users.application.dto.UpdateAdminUserRequest;
import com.tuempresa.storage.users.application.dto.UpdateUserActiveRequest;
import com.tuempresa.storage.users.application.dto.UpdateUserPasswordRequest;
import com.tuempresa.storage.users.application.dto.UpdateUserRolesRequest;
import com.tuempresa.storage.users.application.usecase.AdminUserService;
import com.tuempresa.storage.users.application.usecase.AdminUserService.UserExportRow;
import com.tuempresa.storage.users.domain.Role;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@RestController
@RequestMapping({"/api/v1/admin/users", "/api/v1/admin/usuarios"})
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final SecurityUtils securityUtils;
    private final ReactiveBlockingExecutor reactiveBlockingExecutor;
    private final ReactiveMultipartAdapter reactiveMultipartAdapter;
    private final CsvExportService csvExportService;

    public AdminUserController(
            AdminUserService adminUserService,
            SecurityUtils securityUtils,
            ReactiveBlockingExecutor reactiveBlockingExecutor,
            ReactiveMultipartAdapter reactiveMultipartAdapter,
            CsvExportService csvExportService
    ) {
        this.adminUserService = adminUserService;
        this.securityUtils = securityUtils;
        this.reactiveBlockingExecutor = reactiveBlockingExecutor;
        this.reactiveMultipartAdapter = reactiveMultipartAdapter;
        this.csvExportService = csvExportService;
    }

    @GetMapping
    public Mono<List<AdminUserResponse>> list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false, defaultValue = "false") boolean latestOnly,
            @RequestParam(required = false) Integer limit
    ) {
        return reactiveBlockingExecutor.call(() -> adminUserService.list(query, role, latestOnly, limit));
    }

    @GetMapping("/page")
    public Mono<PagedResponse<AdminUserPagedResponse>> listPage(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Role role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return reactiveBlockingExecutor.call(() -> adminUserService.listPage(query, role, page, size));
    }

    @GetMapping("/summary")
    public Mono<AdminUserSummaryResponse> summary(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Role role
    ) {
        return reactiveBlockingExecutor.call(() -> adminUserService.summary(query, role));
    }

    @PostMapping
    public Mono<AdminUserResponse> create(@Valid @RequestBody CreateAdminUserRequest request) {
        return reactiveBlockingExecutor.call(() -> adminUserService.create(request));
    }

    @PutMapping("/{id}")
    public Mono<AdminUserResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAdminUserRequest request
    ) {
        return reactiveBlockingExecutor.call(() -> adminUserService.update(id, request));
    }

    @PatchMapping("/{id}/roles")
    public Mono<AdminUserResponse> updateRoles(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRolesRequest request
    ) {
        return reactiveBlockingExecutor.call(() -> adminUserService.updateRoles(id, request));
    }

    @PatchMapping("/{id}/active")
    public Mono<AdminUserResponse> updateActive(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserActiveRequest request
    ) {
        return reactiveBlockingExecutor.call(() -> adminUserService.updateActive(id, request));
    }

    @PatchMapping("/{id}/password")
    public Mono<AdminUserResponse> updatePassword(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserPasswordRequest request
    ) {
        return reactiveBlockingExecutor.call(() -> adminUserService.updatePassword(id, request));
    }

    @PostMapping(value = "/document-photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<Map<String, String>> uploadDocumentPhoto(@RequestPart("file") FilePart file) {
        return reactiveMultipartAdapter.toMultipartFile(file)
                .flatMap(multipartFile -> reactiveBlockingExecutor.call(() -> {
                    String url = adminUserService.uploadDocumentPhoto(multipartFile);
                    return Map.of("url", url);
                }));
    }

    @DeleteMapping("/{id}")
    public Mono<Void> delete(@PathVariable Long id) {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(() -> {
                    adminUserService.delete(id, currentUser);
                    return true;
                }))
                .then();
    }

    @PatchMapping("/bulk/delete")
    public Mono<BulkOperationResponse> bulkDelete(@Valid @RequestBody BulkIdsRequest request) {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(() -> 
                        adminUserService.bulkDelete(request.ids(), currentUser)));
    }

    @PatchMapping("/bulk/active")
    public Mono<BulkOperationResponse> bulkUpdateActive(@Valid @RequestBody BulkActiveRequest request) {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(() -> 
                        adminUserService.bulkUpdateActive(request.ids(), request.active(), currentUser)));
    }

    @PatchMapping("/bulk/roles")
    public Mono<BulkOperationResponse> bulkUpdateRoles(@Valid @RequestBody BulkRolesRequest request) {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(() -> 
                        adminUserService.bulkUpdateRoles(request.ids(), request.roles(), request.warehouseIds(), currentUser)));
    }

    @GetMapping(value = "/export", produces = "text/csv")
    public Mono<ResponseEntity<byte[]>> exportUsers(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Role role
    ) {
        return reactiveBlockingExecutor.call(() -> {
            List<UserExportRow> users = adminUserService.exportUsers(query, role);
            List<String> headers = List.of(
                    "ID", "Nombre", "Email", "Telefono", "Nacionalidad",
                    "Roles", "Warehouses", "Activo", "Email Verificado",
                    "Perfil Completo", "Entregas Asignadas", "Entregas Completadas", "Fecha Creacion"
            );
            List<Function<UserExportRow, String>> mappers = List.of(
                    row -> String.valueOf(row.id()),
                    row -> row.fullName(),
                    row -> row.email(),
                    row -> row.phone() != null ? row.phone() : "",
                    row -> row.nationality() != null ? row.nationality() : "",
                    row -> row.roles(),
                    row -> row.warehouses(),
                    row -> row.active() ? "Si" : "No",
                    row -> row.emailVerified() ? "Si" : "No",
                    row -> row.profileCompleted() ? "Si" : "No",
                    row -> String.valueOf(row.assignedDeliveries()),
                    row -> String.valueOf(row.completedDeliveries()),
                    row -> row.createdAt() != null ? csvExportService.formatInstant(row.createdAt()) : ""
            );
            byte[] csv;
            try {
                csv = exportToCsv(headers, users, mappers);
            } catch (IOException e) {
                csv = ("Error generating CSV: " + e.getMessage()).getBytes();
            }
            String filename = "users_export_" + Instant.now().toEpochMilli() + ".csv";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                    .body(csv);
        });
    }

    private <T> byte[] exportToCsv(List<String> headers, List<T> data, List<Function<T, String>> columnMappers) throws IOException {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        csvExportService.exportToCsvWithHeader(baos, "Exportacion de Usuarios", headers, data, columnMappers);
        return baos.toByteArray();
    }

    @DeleteMapping("/firebase/account")
    public Mono<Void> deleteFirebaseByEmail(@RequestParam(required = false) String email) {
        return deleteFirebaseByEmailInternal(resolveEmail(email, null));
    }

    @PostMapping("/firebase/account")
    public Mono<Void> deleteFirebaseByEmailPost(
            @RequestParam(required = false) String email,
            @RequestBody(required = false) DeleteFirebaseAccountRequest request
    ) {
        return deleteFirebaseByEmailInternal(resolveEmail(email, request));
    }

    private Mono<Void> deleteFirebaseByEmailInternal(String email) {
        return reactiveBlockingExecutor.call(() -> {
                    adminUserService.deleteFirebaseAccountByEmail(email);
                    return true;
                })
                .then();
    }

    private String resolveEmail(String queryEmail, DeleteFirebaseAccountRequest request) {
        if (queryEmail != null && !queryEmail.isBlank()) {
            return queryEmail;
        }
        if (request != null && request.email() != null && !request.email().isBlank()) {
            return request.email();
        }
        return null;
    }

    private record DeleteFirebaseAccountRequest(String email) {
    }
}
