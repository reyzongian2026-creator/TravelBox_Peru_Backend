package com.tuempresa.storage.inventory.infrastructure.in.web;

import com.tuempresa.storage.inventory.application.dto.CheckinRequest;
import com.tuempresa.storage.inventory.application.dto.CheckoutRequest;
import com.tuempresa.storage.inventory.application.dto.EvidenceRequest;
import com.tuempresa.storage.inventory.application.dto.EvidenceResponse;
import com.tuempresa.storage.inventory.application.dto.InventoryActionResponse;
import com.tuempresa.storage.inventory.application.usecase.InventoryService;
import com.tuempresa.storage.shared.infrastructure.reactive.ReactiveBlockingExecutor;
import com.tuempresa.storage.shared.infrastructure.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final InventoryService inventoryService;
    private final SecurityUtils securityUtils;
    private final ReactiveBlockingExecutor reactiveBlockingExecutor;

    public InventoryController(
            InventoryService inventoryService,
            SecurityUtils securityUtils,
            ReactiveBlockingExecutor reactiveBlockingExecutor
    ) {
        this.inventoryService = inventoryService;
        this.securityUtils = securityUtils;
        this.reactiveBlockingExecutor = reactiveBlockingExecutor;
    }

    @GetMapping("/evidences")
    @PreAuthorize("isAuthenticated()")
    public Mono<ResponseEntity<List<EvidenceResponse>>> getEvidences(@RequestParam Long reservationId) {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                        () -> inventoryService.getEvidencesByReservationId(reservationId)
                ))
                .map(ResponseEntity::ok);
    }

    @PostMapping("/checkin")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN','CITY_SUPERVISOR')")
    public Mono<ResponseEntity<InventoryActionResponse>> checkin(@Valid @RequestBody CheckinRequest request) {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                        () -> inventoryService.checkin(request, currentUser)
                ))
                .map(ResponseEntity::ok);
    }

    @PostMapping("/checkout")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN','CITY_SUPERVISOR')")
    public Mono<ResponseEntity<InventoryActionResponse>> checkout(@Valid @RequestBody CheckoutRequest request) {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                        () -> inventoryService.checkout(request, currentUser)
                ))
                .map(ResponseEntity::ok);
    }

    @PostMapping("/evidences")
    @PreAuthorize("isAuthenticated()")
    public Mono<ResponseEntity<InventoryActionResponse>> evidence(@Valid @RequestBody EvidenceRequest request) {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                        () -> inventoryService.addEvidence(request, currentUser)
                ))
                .map(ResponseEntity::ok);
    }

    @PostMapping(path = "/evidences/upload", consumes = {"multipart/form-data"})
    @PreAuthorize("isAuthenticated()")
    public Mono<ResponseEntity<InventoryActionResponse>> uploadEvidence(
            @RequestParam Long reservationId,
            @RequestParam String type,
            @RequestParam(required = false) String observation,
            @RequestParam("file") MultipartFile file
    ) {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                        () -> inventoryService.addEvidenceFile(
                                reservationId,
                                type,
                                observation,
                                file,
                                currentUser
                        )
                ))
                .map(ResponseEntity::ok);
    }
}
