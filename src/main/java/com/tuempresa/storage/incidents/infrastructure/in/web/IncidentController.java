package com.tuempresa.storage.incidents.infrastructure.in.web;

import com.tuempresa.storage.incidents.application.dto.CreateIncidentRequest;
import com.tuempresa.storage.incidents.application.dto.AddIncidentMessageRequest;
import com.tuempresa.storage.incidents.application.dto.IncidentMessageResponse;
import com.tuempresa.storage.incidents.application.dto.IncidentResponse;
import com.tuempresa.storage.incidents.application.dto.IncidentSummaryResponse;
import com.tuempresa.storage.incidents.application.dto.ResolveIncidentRequest;
import com.tuempresa.storage.incidents.application.usecase.IncidentService;
import com.tuempresa.storage.incidents.domain.IncidentStatus;
import com.tuempresa.storage.shared.domain.exception.ApiException;
import com.tuempresa.storage.shared.infrastructure.reactive.ReactiveBlockingExecutor;
import com.tuempresa.storage.shared.infrastructure.security.SecurityUtils;
import com.tuempresa.storage.shared.infrastructure.storage.StorageService;
import com.tuempresa.storage.shared.infrastructure.web.PagedResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentController {

        private final IncidentService incidentService;
        private final SecurityUtils securityUtils;
        private final ReactiveBlockingExecutor reactiveBlockingExecutor;
        private final StorageService storageService;

        public IncidentController(
                        IncidentService incidentService,
                        SecurityUtils securityUtils,
                        ReactiveBlockingExecutor reactiveBlockingExecutor,
                        StorageService storageService) {
                this.incidentService = incidentService;
                this.securityUtils = securityUtils;
                this.reactiveBlockingExecutor = reactiveBlockingExecutor;
                this.storageService = storageService;
        }

        @GetMapping
        @PreAuthorize("isAuthenticated()")
        public Mono<ResponseEntity<List<IncidentSummaryResponse>>> list(
                        @RequestParam(required = false) IncidentStatus status,
                        @RequestParam(required = false) String query,
                        @RequestParam(required = false) Long reservationId) {
                return securityUtils.currentUserOrThrowReactive()
                                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                                                () -> incidentService.list(currentUser, status, query, reservationId)))
                                .map(ResponseEntity::ok);
        }

        @GetMapping("/page")
        @PreAuthorize("isAuthenticated()")
        public Mono<ResponseEntity<PagedResponse<IncidentSummaryResponse>>> page(
                        @RequestParam(defaultValue = "0") @Min(0) int page,
                        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
                        @RequestParam(required = false) IncidentStatus status,
                        @RequestParam(required = false) String query,
                        @RequestParam(required = false) Long reservationId) {
                return securityUtils.currentUserOrThrowReactive()
                                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                                                () -> incidentService.listPage(currentUser, page, size, status, query,
                                                                reservationId)))
                                .map(ResponseEntity::ok);
        }

        @PostMapping
        @PreAuthorize("isAuthenticated()")
        public Mono<ResponseEntity<IncidentResponse>> open(@Valid @RequestBody CreateIncidentRequest request) {
                return securityUtils.currentUserOrThrowReactive()
                                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                                                () -> incidentService.open(request, currentUser)))
                                .map(ResponseEntity::ok);
        }

        @GetMapping("/{id}/messages")
        @PreAuthorize("isAuthenticated()")
        public Mono<ResponseEntity<List<IncidentMessageResponse>>> messages(@PathVariable Long id) {
                return securityUtils.currentUserOrThrowReactive()
                                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                                                () -> incidentService.listMessages(id, currentUser)))
                                .map(ResponseEntity::ok);
        }

        @PostMapping(value = "/{id}/messages", consumes = MediaType.APPLICATION_JSON_VALUE)
        @PreAuthorize("isAuthenticated()")
        public Mono<ResponseEntity<IncidentMessageResponse>> addMessageJson(
                        @PathVariable Long id,
                        @Valid @RequestBody AddIncidentMessageRequest request) {
                return securityUtils.currentUserOrThrowReactive()
                                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                                                () -> incidentService.addMessage(id, request, null, currentUser)))
                                .map(ResponseEntity::ok);
        }

        @PostMapping(value = "/{id}/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @PreAuthorize("isAuthenticated()")
        public Mono<ResponseEntity<IncidentMessageResponse>> addMessageMultipart(
                        @PathVariable Long id,
                        @RequestParam String message,
                        @RequestParam(required = false) String originalLanguage,
                        @RequestPart(required = false) MultipartFile image) {
                return securityUtils.currentUserOrThrowReactive()
                                .flatMap(currentUser -> reactiveBlockingExecutor.call(() -> {
                                        String imageUrl = null;
                                        if (image != null && !image.isEmpty()) {
                                                var result = storageService.upload(image, StorageService.FileCategory.EVIDENCES);
                                                imageUrl = result.url();
                                        }
                                        var request = buildMultipartMessageRequest(message, originalLanguage, image);
                                        return incidentService.addMessage(id, request, imageUrl, currentUser);
                                }))
                                .map(ResponseEntity::ok);
        }

        @PatchMapping("/{id}/resolve")
        @PreAuthorize("hasAnyRole('SUPPORT','ADMIN')")
        public Mono<ResponseEntity<IncidentResponse>> resolve(
                        @PathVariable Long id,
                        @Valid @RequestBody ResolveIncidentRequest request) {
                return securityUtils.currentUserOrThrowReactive()
                                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                                                () -> incidentService.resolve(id, request, currentUser)))
                                .map(ResponseEntity::ok);
        }

        private AddIncidentMessageRequest buildMultipartMessageRequest(
                        String message,
                        String originalLanguage,
                        MultipartFile image) {
                String normalizedMessage = message == null ? "" : message.trim();
                boolean hasImage = image != null && !image.isEmpty();
                if (normalizedMessage.isBlank() && !hasImage) {
                        throw new ApiException(
                                        HttpStatus.BAD_REQUEST,
                                        "INCIDENT_MESSAGE_REQUIRED",
                                        "Debes escribir un mensaje o adjuntar una imagen.");
                }
                if (normalizedMessage.length() > 500) {
                        throw new ApiException(
                                        HttpStatus.BAD_REQUEST,
                                        "INCIDENT_MESSAGE_TOO_LONG",
                                        "El mensaje no puede exceder 500 caracteres.");
                }
                String normalizedLanguage = originalLanguage == null ? null : originalLanguage.trim();
                if (normalizedLanguage != null && normalizedLanguage.isBlank()) {
                        normalizedLanguage = null;
                }
                if (normalizedLanguage != null && normalizedLanguage.length() > 5) {
                        throw new ApiException(
                                        HttpStatus.BAD_REQUEST,
                                        "INCIDENT_LANGUAGE_INVALID",
                                        "El idioma original no puede exceder 5 caracteres.");
                }
                return new AddIncidentMessageRequest(normalizedMessage, normalizedLanguage);
        }
}
