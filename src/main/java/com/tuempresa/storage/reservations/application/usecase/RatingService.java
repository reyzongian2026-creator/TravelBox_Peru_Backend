package com.tuempresa.storage.reservations.application.usecase;

import com.tuempresa.storage.reservations.application.dto.CreateRatingRequest;
import com.tuempresa.storage.reservations.application.dto.RatingResponse;
import com.tuempresa.storage.reservations.application.dto.WarehouseRatingSummary;
import com.tuempresa.storage.reservations.domain.Rating;
import com.tuempresa.storage.reservations.domain.Reservation;
import com.tuempresa.storage.reservations.domain.ReservationStatus;
import com.tuempresa.storage.reservations.infrastructure.out.persistence.RatingRepository;
import com.tuempresa.storage.reservations.infrastructure.out.persistence.ReservationRepository;
import com.tuempresa.storage.shared.domain.exception.ApiException;
import com.tuempresa.storage.shared.infrastructure.security.AuthUserPrincipal;
import com.tuempresa.storage.users.domain.User;
import com.tuempresa.storage.users.infrastructure.out.persistence.UserRepository;
import com.tuempresa.storage.warehouses.domain.Warehouse;
import com.tuempresa.storage.warehouses.infrastructure.out.persistence.WarehouseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RatingService {

    private final RatingRepository ratingRepository;
    private final ReservationRepository reservationRepository;
    private final WarehouseRepository warehouseRepository;
    private final UserRepository userRepository;

    public RatingService(
            RatingRepository ratingRepository,
            ReservationRepository reservationRepository,
            WarehouseRepository warehouseRepository,
            UserRepository userRepository
    ) {
        this.ratingRepository = ratingRepository;
        this.reservationRepository = reservationRepository;
        this.warehouseRepository = warehouseRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public RatingResponse create(CreateRatingRequest request, AuthUserPrincipal principal) {
        User user = userFromPrincipal(principal);
        Warehouse warehouse = warehouseRepository.findById(request.getWarehouseId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "WAREHOUSE_NOT_FOUND",
                        "Warehouse not found with id: " + request.getWarehouseId()
                ));

        Reservation reservation = null;
        if (request.getReservationId() != null) {
            reservation = reservationRepository.findById(request.getReservationId())
                    .orElseThrow(() -> new ApiException(
                            HttpStatus.NOT_FOUND,
                            "RESERVATION_NOT_FOUND",
                            "Reservation not found with id: " + request.getReservationId()
                    ));

            if (!reservation.belongsTo(user.getId())) {
                throw new ApiException(
                        HttpStatus.FORBIDDEN,
                        "NOT_YOUR_RESERVATION",
                        "This reservation does not belong to you"
                );
            }

            if (reservation.getStatus() != ReservationStatus.COMPLETED &&
                reservation.getStatus() != ReservationStatus.CONFIRMED) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_RESERVATION_STATUS",
                        "You can only rate completed or confirmed reservations"
                );
            }
        }

        if (ratingRepository.existsByUserIdAndWarehouseId(user.getId(), warehouse.getId())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "ALREADY_RATED",
                    "You have already rated this warehouse"
            );
        }

        Rating.RatingType type = Rating.RatingType.WAREHOUSE;
        if (request.getType() != null) {
            try {
                type = Rating.RatingType.valueOf(request.getType().toUpperCase());
            } catch (IllegalArgumentException e) {
                type = Rating.RatingType.WAREHOUSE;
            }
        }

        Rating rating = Rating.create(
                user,
                warehouse,
                reservation,
                request.getStars(),
                request.getComment(),
                type
        );

        if (reservation != null) {
            rating.markAsVerified();
        }

        rating = ratingRepository.save(rating);
        return RatingResponse.from(rating);
    }

    @Transactional(readOnly = true)
    public List<RatingResponse> getByWarehouse(Long warehouseId) {
        if (!warehouseRepository.existsById(warehouseId)) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "WAREHOUSE_NOT_FOUND",
                    "Warehouse not found with id: " + warehouseId
            );
        }
        return ratingRepository.findByWarehouseIdOrderByCreatedAtDesc(warehouseId)
                .stream()
                .map(RatingResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public WarehouseRatingSummary getWarehouseSummary(Long warehouseId) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "WAREHOUSE_NOT_FOUND",
                        "Warehouse not found with id: " + warehouseId
                ));

        BigDecimal avgStars = ratingRepository.getAverageStarsByWarehouseId(warehouseId);
        long totalRatings = ratingRepository.countByWarehouseId(warehouseId);

        WarehouseRatingSummary summary = new WarehouseRatingSummary(
                warehouseId,
                warehouse.getName(),
                avgStars != null ? avgStars.setScale(1, RoundingMode.HALF_UP) : BigDecimal.ZERO,
                totalRatings
        );

        List<Rating> ratings = ratingRepository.findByWarehouseIdOrderByCreatedAtDesc(warehouseId);
        summary.setStars1Count((int) ratings.stream().filter(r -> r.getStars() == 1).count());
        summary.setStars2Count((int) ratings.stream().filter(r -> r.getStars() == 2).count());
        summary.setStars3Count((int) ratings.stream().filter(r -> r.getStars() == 3).count());
        summary.setStars4Count((int) ratings.stream().filter(r -> r.getStars() == 4).count());
        summary.setStars5Count((int) ratings.stream().filter(r -> r.getStars() == 5).count());

        return summary;
    }

    @Transactional(readOnly = true)
    public RatingResponse getMyRating(Long warehouseId, AuthUserPrincipal principal) {
        return ratingRepository.findByUserIdAndWarehouseId(principal.getId(), warehouseId)
                .map(RatingResponse::from)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public RatingResponse getByReservation(Long reservationId, AuthUserPrincipal principal) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "RESERVATION_NOT_FOUND",
                        "Reservation not found"
                ));

        if (!reservation.belongsTo(principal.getId())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "NOT_YOUR_RESERVATION",
                    "This reservation does not belong to you"
                );
            }

        return ratingRepository.findByReservationId(reservationId)
                .map(RatingResponse::from)
                .orElse(null);
    }

    private User userFromPrincipal(AuthUserPrincipal principal) {
        return userRepository.findById(principal.getId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        "USER_NOT_FOUND",
                        "User not found"
                ));
    }
}
