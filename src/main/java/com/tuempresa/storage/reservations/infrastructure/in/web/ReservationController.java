package com.tuempresa.storage.reservations.infrastructure.in.web;

import com.tuempresa.storage.reservations.application.dto.CancelReservationRequest;
import com.tuempresa.storage.reservations.application.dto.CreateAssistedReservationRequest;
import com.tuempresa.storage.reservations.application.dto.CreateReservationCheckoutRequest;
import com.tuempresa.storage.reservations.application.dto.CreateReservationRequest;
import com.tuempresa.storage.reservations.application.dto.ReservationResponse;
import com.tuempresa.storage.reservations.application.usecase.ReservationCheckoutService;
import com.tuempresa.storage.reservations.application.usecase.ReservationService;
import com.tuempresa.storage.reservations.domain.ReservationStatus;
import com.tuempresa.storage.shared.infrastructure.reactive.ReactiveBlockingExecutor;
import com.tuempresa.storage.shared.infrastructure.security.SecurityUtils;
import com.tuempresa.storage.shared.infrastructure.web.PagedResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
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
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reservations")
public class ReservationController {

    private final ReservationService reservationService;
    private final ReservationCheckoutService reservationCheckoutService;
    private final SecurityUtils securityUtils;
    private final ReactiveBlockingExecutor reactiveBlockingExecutor;

    public ReservationController(
            ReservationService reservationService,
            ReservationCheckoutService reservationCheckoutService,
            SecurityUtils securityUtils,
            ReactiveBlockingExecutor reactiveBlockingExecutor
    ) {
        this.reservationService = reservationService;
        this.reservationCheckoutService = reservationCheckoutService;
        this.securityUtils = securityUtils;
        this.reactiveBlockingExecutor = reactiveBlockingExecutor;
    }

    @PostMapping
    public Mono<ResponseEntity<ReservationResponse>> create(@Valid @RequestBody CreateReservationRequest request) {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                        () -> reservationService.create(request, currentUser)
                ))
                .map(ResponseEntity::ok);
    }

    @PostMapping("/checkout")
    public Mono<ResponseEntity<ReservationResponse>> checkout(@Valid @RequestBody CreateReservationCheckoutRequest request) {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                        () -> reservationCheckoutService.checkout(request, currentUser)
                ))
                .map(ResponseEntity::ok);
    }

    @PostMapping("/assisted")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','CITY_SUPERVISOR')")
    public Mono<ResponseEntity<ReservationResponse>> createAssisted(
            @Valid @RequestBody CreateAssistedReservationRequest request
    ) {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                        () -> reservationService.createAssisted(request, currentUser)
                ))
                .map(ResponseEntity::ok);
    }

    @GetMapping({"", "/list", "/my"})
    public Mono<List<ReservationResponse>> list() {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                        () -> reservationService.list(currentUser)
                ));
    }

    @GetMapping("/page")
    public Mono<PagedResponse<ReservationResponse>> page(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) ReservationStatus status,
            @RequestParam(required = false) String query
    ) {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                        () -> reservationService.listPage(currentUser, page, size, status, query)
                ));
    }

    @GetMapping("/{id}")
    public Mono<ReservationResponse> detail(@PathVariable Long id) {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                        () -> reservationService.getById(id, currentUser)
                ));
    }

    @PatchMapping("/{id}/cancel")
    public Mono<ResponseEntity<ReservationResponse>> cancel(
            @PathVariable Long id,
            @Valid @RequestBody CancelReservationRequest request
    ) {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                        () -> reservationService.cancel(id, request, currentUser)
                ))
                .map(ResponseEntity::ok);
    }

    @GetMapping(value = "/{id}/qr", produces = MediaType.IMAGE_PNG_VALUE)
    public Mono<ResponseEntity<byte[]>> qr(@PathVariable Long id) {
        return reactiveBlockingExecutor.call(() -> reservationService.qrPngPublic(id))
                .map(png -> ResponseEntity.ok()
                        .header(HttpHeaders.CACHE_CONTROL, "no-store")
                        .contentType(MediaType.IMAGE_PNG)
                        .body(png));
    }
}
