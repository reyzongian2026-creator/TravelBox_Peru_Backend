package com.tuempresa.storage.reservations.infrastructure.in.web;

import com.tuempresa.storage.reservations.application.dto.CreateRatingRequest;
import com.tuempresa.storage.reservations.application.dto.RatingResponse;
import com.tuempresa.storage.reservations.application.dto.WarehouseRatingSummary;
import com.tuempresa.storage.reservations.application.usecase.RatingService;
import com.tuempresa.storage.shared.infrastructure.reactive.ReactiveBlockingExecutor;
import com.tuempresa.storage.shared.infrastructure.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ratings")
public class RatingController {

    private final RatingService ratingService;
    private final SecurityUtils securityUtils;
    private final ReactiveBlockingExecutor reactiveBlockingExecutor;

    public RatingController(
            RatingService ratingService,
            SecurityUtils securityUtils,
            ReactiveBlockingExecutor reactiveBlockingExecutor
    ) {
        this.ratingService = ratingService;
        this.securityUtils = securityUtils;
        this.reactiveBlockingExecutor = reactiveBlockingExecutor;
    }

    @PostMapping
    public Mono<ResponseEntity<RatingResponse>> create(@Valid @RequestBody CreateRatingRequest request) {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                        () -> ratingService.create(request, currentUser)
                ))
                .map(ResponseEntity::ok);
    }

    @GetMapping("/warehouse/{warehouseId}")
    public Mono<List<RatingResponse>> getByWarehouse(@PathVariable Long warehouseId) {
        return reactiveBlockingExecutor.call(
                () -> ratingService.getByWarehouse(warehouseId)
        );
    }

    @GetMapping("/warehouse/{warehouseId}/summary")
    public Mono<WarehouseRatingSummary> getWarehouseSummary(@PathVariable Long warehouseId) {
        return reactiveBlockingExecutor.call(
                () -> ratingService.getWarehouseSummary(warehouseId)
        );
    }

    @GetMapping("/warehouse/{warehouseId}/me")
    public Mono<RatingResponse> getMyRating(@PathVariable Long warehouseId) {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                        () -> ratingService.getMyRating(warehouseId, currentUser)
                ));
    }

    @GetMapping("/reservation/{reservationId}")
    public Mono<RatingResponse> getByReservation(@PathVariable Long reservationId) {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                        () -> ratingService.getByReservation(reservationId, currentUser)
                ));
    }
}
