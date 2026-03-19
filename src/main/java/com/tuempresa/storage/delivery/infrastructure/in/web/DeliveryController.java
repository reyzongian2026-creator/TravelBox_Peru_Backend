package com.tuempresa.storage.delivery.infrastructure.in.web;

import com.tuempresa.storage.delivery.application.dto.CreateDeliveryOrderRequest;
import com.tuempresa.storage.delivery.application.dto.CourierClaimDeliveryRequest;
import com.tuempresa.storage.delivery.application.dto.CourierTrackingUpdateRequest;
import com.tuempresa.storage.delivery.application.dto.DeliveryMonitorItemResponse;
import com.tuempresa.storage.delivery.application.dto.DeliveryOrderResponse;
import com.tuempresa.storage.delivery.application.dto.DeliveryTrackingResponse;
import com.tuempresa.storage.delivery.application.usecase.DeliveryService;
import com.tuempresa.storage.shared.infrastructure.reactive.ReactiveBlockingExecutor;
import com.tuempresa.storage.shared.infrastructure.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/v1/delivery-orders")
public class DeliveryController {

    private final DeliveryService deliveryService;
    private final SecurityUtils securityUtils;
    private final ReactiveBlockingExecutor reactiveBlockingExecutor;

    public DeliveryController(
            DeliveryService deliveryService,
            SecurityUtils securityUtils,
            ReactiveBlockingExecutor reactiveBlockingExecutor
    ) {
        this.deliveryService = deliveryService;
        this.securityUtils = securityUtils;
        this.reactiveBlockingExecutor = reactiveBlockingExecutor;
    }

    @PostMapping
    public Mono<ResponseEntity<DeliveryOrderResponse>> create(@Valid @RequestBody CreateDeliveryOrderRequest request) {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                        () -> deliveryService.create(request, currentUser)
                ))
                .map(ResponseEntity::ok);
    }

    @GetMapping
    public Mono<ResponseEntity<List<DeliveryMonitorItemResponse>>> list(
            @RequestParam(defaultValue = "true") boolean activeOnly,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "all") String scope
    ) {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                        () -> deliveryService.list(currentUser, activeOnly, query, scope)
                ))
                .map(ResponseEntity::ok);
    }

    @GetMapping("/{id}")
    public Mono<DeliveryOrderResponse> detail(@PathVariable Long id) {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                        () -> deliveryService.findById(id, currentUser)
                ));
    }

    @GetMapping("/{id}/tracking")
    public Mono<DeliveryTrackingResponse> tracking(@PathVariable Long id) {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                        () -> deliveryService.tracking(id, currentUser)
                ));
    }

    @GetMapping("/reservation/{reservationId}/tracking")
    public Mono<DeliveryTrackingResponse> trackingByReservation(@PathVariable Long reservationId) {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                        () -> deliveryService.trackingByReservation(reservationId, currentUser)
                ));
    }

    @PostMapping("/{id}/claim")
    public Mono<ResponseEntity<DeliveryOrderResponse>> claim(
            @PathVariable Long id,
            @Valid @RequestBody CourierClaimDeliveryRequest request
    ) {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                        () -> deliveryService.claim(id, request, currentUser)
                ))
                .map(ResponseEntity::ok);
    }

    @PatchMapping("/{id}/progress")
    public Mono<ResponseEntity<DeliveryTrackingResponse>> progress(
            @PathVariable Long id,
            @Valid @RequestBody CourierTrackingUpdateRequest request
    ) {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                        () -> deliveryService.updateProgress(id, request, currentUser)
                ))
                .map(ResponseEntity::ok);
    }
}
