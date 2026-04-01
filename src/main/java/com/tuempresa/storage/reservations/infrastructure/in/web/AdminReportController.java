package com.tuempresa.storage.reservations.infrastructure.in.web;

import com.tuempresa.storage.reservations.application.dto.AdminRatingResponse;
import com.tuempresa.storage.reservations.application.dto.RevenueReportResponse;
import com.tuempresa.storage.reservations.application.dto.UpdateRatingRequest;
import com.tuempresa.storage.reservations.application.usecase.RatingService;
import com.tuempresa.storage.reservations.application.usecase.ReservationService;
import com.tuempresa.storage.shared.infrastructure.reactive.ReactiveBlockingExecutor;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@RestController
@RequestMapping({"/api/v1/admin/reports", "/api/v1/admin/reportes"})
@PreAuthorize("hasRole('ADMIN')")
public class AdminReportController {

    private final ReservationService reservationService;
    private final RatingService ratingService;
    private final ReactiveBlockingExecutor reactiveBlockingExecutor;

    public AdminReportController(
            ReservationService reservationService,
            RatingService ratingService,
            ReactiveBlockingExecutor reactiveBlockingExecutor
    ) {
        this.reservationService = reservationService;
        this.ratingService = ratingService;
        this.reactiveBlockingExecutor = reactiveBlockingExecutor;
    }

    @GetMapping("/revenue")
    public Mono<RevenueReportResponse> getRevenueReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        Instant start = startDate.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = endDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        return reactiveBlockingExecutor.call(() -> reservationService.generateRevenueReport(start, end));
    }

    @GetMapping("/ratings")
    public Mono<List<AdminRatingResponse>> getAllRatings() {
        return reactiveBlockingExecutor.call(() -> ratingService.getAllRatings());
    }

    @PatchMapping("/ratings/{id}")
    public Mono<AdminRatingResponse> updateRating(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRatingRequest request
    ) {
        return reactiveBlockingExecutor.call(() -> 
                ratingService.updateRating(id, request.stars(), request.comment()));
    }

    @DeleteMapping("/ratings/{id}")
    public Mono<Void> deleteRating(@PathVariable Long id) {
        return reactiveBlockingExecutor.call(() -> {
            ratingService.deleteRating(id);
            return true;
        }).then();
    }
}
