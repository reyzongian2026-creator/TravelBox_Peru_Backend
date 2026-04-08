package com.tuempresa.storage.ops.infrastructure.in.web;

import com.tuempresa.storage.ops.application.dto.OpsApprovalDecisionRequest;
import com.tuempresa.storage.ops.application.dto.OpsApprovalItemResponse;
import com.tuempresa.storage.ops.application.dto.OpsApprovalRejectionRequest;
import com.tuempresa.storage.ops.application.dto.OpsApprovalRequest;
import com.tuempresa.storage.ops.application.dto.OpsBagTagRequest;
import com.tuempresa.storage.ops.application.dto.OpsFlagRequest;
import com.tuempresa.storage.ops.application.dto.OpsNotesRequest;
import com.tuempresa.storage.ops.application.dto.OpsPinRequest;
import com.tuempresa.storage.ops.application.dto.OpsQrCaseResponse;
import com.tuempresa.storage.ops.application.dto.OpsQrScanRequest;
import com.tuempresa.storage.ops.application.usecase.OpsQrHandoffService;
import com.tuempresa.storage.ops.domain.QrHandoffApprovalStatus;
import com.tuempresa.storage.shared.infrastructure.reactive.ReactiveBlockingExecutor;
import com.tuempresa.storage.shared.infrastructure.reactive.ReactiveMultipartAdapter;
import com.tuempresa.storage.shared.infrastructure.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
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
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ops/qr-handoff")
public class OpsQrHandoffController {

    private final OpsQrHandoffService opsQrHandoffService;
    private final SecurityUtils securityUtils;
    private final ReactiveBlockingExecutor reactiveBlockingExecutor;
    private final ReactiveMultipartAdapter reactiveMultipartAdapter;

    public OpsQrHandoffController(
            OpsQrHandoffService opsQrHandoffService,
            SecurityUtils securityUtils,
            ReactiveBlockingExecutor reactiveBlockingExecutor,
            ReactiveMultipartAdapter reactiveMultipartAdapter
    ) {
        this.opsQrHandoffService = opsQrHandoffService;
        this.securityUtils = securityUtils;
        this.reactiveBlockingExecutor = reactiveBlockingExecutor;
        this.reactiveMultipartAdapter = reactiveMultipartAdapter;
    }

    @PostMapping("/scan")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN','CITY_SUPERVISOR','COURIER')")
    public Mono<ResponseEntity<OpsQrCaseResponse>> scan(@Valid @RequestBody OpsQrScanRequest request) {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                        () -> opsQrHandoffService.scan(request.scannedValue(), request.customerLanguage(), currentUser)
                ))
                .map(ResponseEntity::ok);
    }

    @GetMapping("/reservations/batch")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN','CITY_SUPERVISOR','COURIER')")
    public Mono<ResponseEntity<List<OpsQrCaseResponse>>> batchDetail(@RequestParam List<Long> ids) {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                        () -> opsQrHandoffService.batchDetail(ids, currentUser)
                ))
                .map(ResponseEntity::ok);
    }

    @GetMapping("/reservations/{reservationId}")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN','CITY_SUPERVISOR','COURIER')")
    public Mono<ResponseEntity<OpsQrCaseResponse>> detail(@PathVariable Long reservationId) {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                        () -> opsQrHandoffService.detail(reservationId, currentUser)
                ))
                .map(ResponseEntity::ok);
    }

    @PostMapping("/reservations/{reservationId}/tag")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN','CITY_SUPERVISOR')")
    public Mono<ResponseEntity<OpsQrCaseResponse>> tag(
            @PathVariable Long reservationId,
            @Valid @RequestBody(required = false) OpsBagTagRequest request
    ) {
        Integer bagUnits = request == null ? null : request.bagUnits();
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                        () -> opsQrHandoffService.tagLuggage(reservationId, bagUnits, currentUser)
                ))
                .map(ResponseEntity::ok);
    }

    @PostMapping("/reservations/{reservationId}/store")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN','CITY_SUPERVISOR')")
    public Mono<ResponseEntity<OpsQrCaseResponse>> store(
            @PathVariable Long reservationId,
            @Valid @RequestBody(required = false) OpsNotesRequest request
    ) {
        String notes = request == null ? null : request.notes();
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                        () -> opsQrHandoffService.markStored(reservationId, notes, currentUser)
                ))
                .map(ResponseEntity::ok);
    }

    @PostMapping(path = "/reservations/{reservationId}/store-with-photos", consumes = {"multipart/form-data"})
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN','CITY_SUPERVISOR')")
    public Mono<ResponseEntity<OpsQrCaseResponse>> storeWithPhotos(
            @PathVariable Long reservationId,
            @RequestParam(required = false) String notes,
            @RequestPart("files") List<FilePart> files
    ) {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveMultipartAdapter.toMultipartFiles(files)
                        .flatMap(multipartFiles -> reactiveBlockingExecutor.call(
                                () -> opsQrHandoffService.markStoredWithPhotos(
                                        reservationId,
                                        notes,
                                        multipartFiles,
                                        currentUser
                                )
                        )))
                .map(ResponseEntity::ok);
    }

    @PostMapping("/reservations/{reservationId}/ready-for-pickup")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN','CITY_SUPERVISOR')")
    public Mono<ResponseEntity<OpsQrCaseResponse>> readyForPickup(
            @PathVariable Long reservationId,
            @Valid @RequestBody(required = false) OpsNotesRequest request
    ) {
        String notes = request == null ? null : request.notes();
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                        () -> opsQrHandoffService.markReadyForPickup(reservationId, notes, currentUser)
                ))
                .map(ResponseEntity::ok);
    }

    @PostMapping("/reservations/{reservationId}/pickup/confirm")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN','CITY_SUPERVISOR')")
    public Mono<ResponseEntity<OpsQrCaseResponse>> confirmPresentialPickup(
            @PathVariable Long reservationId,
            @Valid @RequestBody OpsPinRequest request
    ) {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                        () -> opsQrHandoffService.validatePickupPin(
                                reservationId,
                                request.pin(),
                                request.notes(),
                                currentUser
                        )
                ))
                .map(ResponseEntity::ok);
    }

    @PatchMapping("/reservations/{reservationId}/delivery/identity")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN','CITY_SUPERVISOR','COURIER')")
    public Mono<ResponseEntity<OpsQrCaseResponse>> deliveryIdentity(
            @PathVariable Long reservationId,
            @Valid @RequestBody OpsFlagRequest request
    ) {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                        () -> opsQrHandoffService.setDeliveryIdentity(
                                reservationId,
                                Boolean.TRUE.equals(request.value()),
                                currentUser
                        )
                ))
                .map(ResponseEntity::ok);
    }

    @PatchMapping("/reservations/{reservationId}/delivery/luggage")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN','CITY_SUPERVISOR','COURIER')")
    public Mono<ResponseEntity<OpsQrCaseResponse>> deliveryLuggage(
            @PathVariable Long reservationId,
            @Valid @RequestBody OpsFlagRequest request
    ) {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                        () -> opsQrHandoffService.setDeliveryLuggage(
                                reservationId,
                                Boolean.TRUE.equals(request.value()),
                                currentUser
                        )
                ))
                .map(ResponseEntity::ok);
    }

    @PostMapping("/reservations/{reservationId}/delivery/request-approval")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN','CITY_SUPERVISOR','COURIER')")
    public Mono<ResponseEntity<OpsQrCaseResponse>> requestApproval(
            @PathVariable Long reservationId,
            @Valid @RequestBody OpsApprovalRequest request
    ) {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                        () -> opsQrHandoffService.requestApproval(reservationId, request, currentUser)
                ))
                .map(ResponseEntity::ok);
    }

    @GetMapping("/approvals")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN','CITY_SUPERVISOR')")
    public Mono<ResponseEntity<List<OpsApprovalItemResponse>>> approvals(
            @RequestParam(required = false) QrHandoffApprovalStatus status,
            @RequestParam(required = false) Long reservationId
    ) {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                        () -> opsQrHandoffService.listApprovals(status, reservationId, currentUser)
                ))
                .map(ResponseEntity::ok);
    }

    @PostMapping("/approvals/{approvalId}/approve")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN','CITY_SUPERVISOR')")
    public Mono<ResponseEntity<OpsQrCaseResponse>> approve(
            @PathVariable Long approvalId,
            @Valid @RequestBody(required = false) OpsApprovalDecisionRequest request
    ) {
        String pin = request == null ? null : request.pin();
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                        () -> opsQrHandoffService.approve(approvalId, pin, currentUser)
                ))
                .map(ResponseEntity::ok);
    }

    @PostMapping("/approvals/{approvalId}/reject")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN','CITY_SUPERVISOR')")
    public Mono<ResponseEntity<OpsApprovalItemResponse>> reject(
            @PathVariable Long approvalId,
            @Valid @RequestBody(required = false) OpsApprovalRejectionRequest request
    ) {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                        () -> opsQrHandoffService.reject(approvalId, currentUser)
                ))
                .map(ResponseEntity::ok);
    }

    @PostMapping("/reservations/{reservationId}/delivery/complete")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN','CITY_SUPERVISOR','COURIER')")
    public Mono<ResponseEntity<OpsQrCaseResponse>> completeDelivery(
            @PathVariable Long reservationId,
            @Valid @RequestBody OpsPinRequest request
    ) {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                        () -> opsQrHandoffService.completeDelivery(
                                reservationId,
                                request.pin(),
                                request.notes(),
                                currentUser
                        )
                ))
                .map(ResponseEntity::ok);
    }
}
