package com.tuempresa.storage.users.infrastructure.in.web;

import com.tuempresa.storage.shared.infrastructure.reactive.ReactiveBlockingExecutor;
import com.tuempresa.storage.shared.infrastructure.reactive.ReactiveMultipartAdapter;
import com.tuempresa.storage.shared.infrastructure.security.SecurityUtils;
import com.tuempresa.storage.users.application.dto.AdminUserResponse;
import com.tuempresa.storage.users.application.dto.AdminUserSummaryResponse;
import com.tuempresa.storage.users.application.dto.CreateAdminUserRequest;
import com.tuempresa.storage.users.application.dto.UpdateAdminUserRequest;
import com.tuempresa.storage.users.application.dto.UpdateUserActiveRequest;
import com.tuempresa.storage.users.application.dto.UpdateUserPasswordRequest;
import com.tuempresa.storage.users.application.dto.UpdateUserRolesRequest;
import com.tuempresa.storage.users.application.usecase.AdminUserService;
import com.tuempresa.storage.users.domain.Role;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.MediaType;
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

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/v1/admin/users", "/api/v1/admin/usuarios"})
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final SecurityUtils securityUtils;
    private final ReactiveBlockingExecutor reactiveBlockingExecutor;
    private final ReactiveMultipartAdapter reactiveMultipartAdapter;

    public AdminUserController(
            AdminUserService adminUserService,
            SecurityUtils securityUtils,
            ReactiveBlockingExecutor reactiveBlockingExecutor,
            ReactiveMultipartAdapter reactiveMultipartAdapter
    ) {
        this.adminUserService = adminUserService;
        this.securityUtils = securityUtils;
        this.reactiveBlockingExecutor = reactiveBlockingExecutor;
        this.reactiveMultipartAdapter = reactiveMultipartAdapter;
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
