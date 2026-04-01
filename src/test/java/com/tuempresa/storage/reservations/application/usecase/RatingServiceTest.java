package com.tuempresa.storage.reservations.application.usecase;

import com.tuempresa.storage.reservations.application.dto.AdminRatingResponse;
import com.tuempresa.storage.reservations.application.dto.CreateRatingRequest;
import com.tuempresa.storage.reservations.application.dto.WarehouseRatingSummary;
import com.tuempresa.storage.reservations.domain.Rating;
import com.tuempresa.storage.reservations.domain.Reservation;
import com.tuempresa.storage.reservations.domain.ReservationStatus;
import com.tuempresa.storage.reservations.infrastructure.out.persistence.RatingRepository;
import com.tuempresa.storage.reservations.infrastructure.out.persistence.ReservationRepository;
import com.tuempresa.storage.shared.domain.exception.ApiException;
import com.tuempresa.storage.shared.infrastructure.security.AuthUserPrincipal;
import com.tuempresa.storage.users.domain.User;
import com.tuempresa.storage.warehouses.domain.Warehouse;
import com.tuempresa.storage.warehouses.infrastructure.out.persistence.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import com.tuempresa.storage.users.infrastructure.out.persistence.UserRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RatingServiceTest {

    @Mock private RatingRepository ratingRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private UserRepository userRepository;

    @Mock private AuthUserPrincipal principal;
    @Mock private User mockUser;
    @Mock private Warehouse mockWarehouse;
    @Mock private Reservation mockReservation;
    @Mock private Rating mockRating;

    private RatingService ratingService;

    // Fixed IDs used across tests
    private static final long USER_ID = 1L;
    private static final long WAREHOUSE_ID = 10L;
    private static final long RESERVATION_ID = 20L;
    private static final long RATING_ID = 30L;

    @BeforeEach
    void setUp() {
        ratingService = new RatingService(ratingRepository, reservationRepository, warehouseRepository, userRepository);

        when(principal.getId()).thenReturn(USER_ID);
        when(mockUser.getId()).thenReturn(USER_ID);
        when(mockWarehouse.getId()).thenReturn(WAREHOUSE_ID);
        when(mockWarehouse.getName()).thenReturn("Test Warehouse");
        when(mockReservation.getId()).thenReturn(RESERVATION_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(mockUser));
    }

    // ── create ─────────────────────────────────────────────────────────────────

    @Test
    void create_warehouseNotFound_throwsNotFound() {
        when(warehouseRepository.findById(WAREHOUSE_ID)).thenReturn(Optional.empty());

        CreateRatingRequest req = request(WAREHOUSE_ID, null, 5);

        ApiException ex = assertThrows(ApiException.class,
                () -> ratingService.create(req, principal));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertEquals("WAREHOUSE_NOT_FOUND", ex.getCode());
    }

    @Test
    void create_reservationNotFound_throwsNotFound() {
        when(warehouseRepository.findById(WAREHOUSE_ID)).thenReturn(Optional.of(mockWarehouse));
        when(reservationRepository.findById(RESERVATION_ID)).thenReturn(Optional.empty());

        CreateRatingRequest req = request(WAREHOUSE_ID, RESERVATION_ID, 4);

        ApiException ex = assertThrows(ApiException.class,
                () -> ratingService.create(req, principal));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertEquals("RESERVATION_NOT_FOUND", ex.getCode());
    }

    @Test
    void create_reservationBelongsToAnotherUser_throwsForbidden() {
        when(warehouseRepository.findById(WAREHOUSE_ID)).thenReturn(Optional.of(mockWarehouse));
        when(reservationRepository.findById(RESERVATION_ID)).thenReturn(Optional.of(mockReservation));
        when(mockReservation.belongsTo(USER_ID)).thenReturn(false);

        CreateRatingRequest req = request(WAREHOUSE_ID, RESERVATION_ID, 4);

        ApiException ex = assertThrows(ApiException.class,
                () -> ratingService.create(req, principal));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertEquals("NOT_YOUR_RESERVATION", ex.getCode());
    }

    @Test
    void create_reservationInInvalidStatus_throwsBadRequest() {
        when(warehouseRepository.findById(WAREHOUSE_ID)).thenReturn(Optional.of(mockWarehouse));
        when(reservationRepository.findById(RESERVATION_ID)).thenReturn(Optional.of(mockReservation));
        when(mockReservation.belongsTo(USER_ID)).thenReturn(true);
        // CANCELLED is not COMPLETED or CONFIRMED
        when(mockReservation.getStatus()).thenReturn(ReservationStatus.CANCELLED);

        CreateRatingRequest req = request(WAREHOUSE_ID, RESERVATION_ID, 3);

        ApiException ex = assertThrows(ApiException.class,
                () -> ratingService.create(req, principal));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("INVALID_RESERVATION_STATUS", ex.getCode());
    }

    @Test
    void create_alreadyRated_throwsConflict() {
        when(warehouseRepository.findById(WAREHOUSE_ID)).thenReturn(Optional.of(mockWarehouse));
        when(ratingRepository.existsByUserIdAndWarehouseId(USER_ID, WAREHOUSE_ID)).thenReturn(true);

        CreateRatingRequest req = request(WAREHOUSE_ID, null, 5);

        ApiException ex = assertThrows(ApiException.class,
                () -> ratingService.create(req, principal));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("ALREADY_RATED", ex.getCode());
    }

    @Test
    void create_validRequestWithoutReservation_savesRating() {
        when(warehouseRepository.findById(WAREHOUSE_ID)).thenReturn(Optional.of(mockWarehouse));
        when(ratingRepository.existsByUserIdAndWarehouseId(USER_ID, WAREHOUSE_ID)).thenReturn(false);
        stubSavedRating(5, "Great place", false);

        CreateRatingRequest req = request(WAREHOUSE_ID, null, 5);
        req.setComment("Great place");

        var response = ratingService.create(req, principal);

        assertNotNull(response);
        verify(ratingRepository).save(any(Rating.class));
    }

    @Test
    void create_validRequestWithCompletedReservation_savesVerifiedRating() {
        when(warehouseRepository.findById(WAREHOUSE_ID)).thenReturn(Optional.of(mockWarehouse));
        when(reservationRepository.findById(RESERVATION_ID)).thenReturn(Optional.of(mockReservation));
        when(mockReservation.belongsTo(USER_ID)).thenReturn(true);
        when(mockReservation.getStatus()).thenReturn(ReservationStatus.COMPLETED);
        when(ratingRepository.existsByUserIdAndWarehouseId(USER_ID, WAREHOUSE_ID)).thenReturn(false);
        stubSavedRating(4, "Good", true);

        CreateRatingRequest req = request(WAREHOUSE_ID, RESERVATION_ID, 4);

        var response = ratingService.create(req, principal);

        assertNotNull(response);
        verify(ratingRepository).save(any(Rating.class));
    }

    // ── getByWarehouse ──────────────────────────────────────────────────────────

    @Test
    void getByWarehouse_warehouseNotFound_throwsNotFound() {
        when(warehouseRepository.existsById(WAREHOUSE_ID)).thenReturn(false);

        ApiException ex = assertThrows(ApiException.class,
                () -> ratingService.getByWarehouse(WAREHOUSE_ID));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertEquals("WAREHOUSE_NOT_FOUND", ex.getCode());
    }

    @Test
    void getByWarehouse_warehouseExists_returnsList() {
        when(warehouseRepository.existsById(WAREHOUSE_ID)).thenReturn(true);
        when(ratingRepository.findByWarehouseIdOrderByCreatedAtDesc(WAREHOUSE_ID)).thenReturn(List.of());

        var result = ratingService.getByWarehouse(WAREHOUSE_ID);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ── getWarehouseSummary ─────────────────────────────────────────────────────

    @Test
    void getWarehouseSummary_warehouseNotFound_throwsNotFound() {
        when(warehouseRepository.findById(WAREHOUSE_ID)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class,
                () -> ratingService.getWarehouseSummary(WAREHOUSE_ID));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertEquals("WAREHOUSE_NOT_FOUND", ex.getCode());
    }

    @Test
    void getWarehouseSummary_noRatings_returnsZeroAverage() {
        when(warehouseRepository.findById(WAREHOUSE_ID)).thenReturn(Optional.of(mockWarehouse));
        when(ratingRepository.getAverageStarsByWarehouseId(WAREHOUSE_ID)).thenReturn(null);
        when(ratingRepository.countByWarehouseId(WAREHOUSE_ID)).thenReturn(0L);
        when(ratingRepository.findByWarehouseIdOrderByCreatedAtDesc(WAREHOUSE_ID)).thenReturn(List.of());

        WarehouseRatingSummary summary = ratingService.getWarehouseSummary(WAREHOUSE_ID);

        assertEquals(BigDecimal.ZERO, summary.getAverageStars());
        assertEquals(0L, summary.getTotalRatings());
    }

    // ── updateRating ────────────────────────────────────────────────────────────

    @Test
    void updateRating_ratingNotFound_throwsNotFound() {
        when(ratingRepository.findById(RATING_ID)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class,
                () -> ratingService.updateRating(RATING_ID, 4, "comment"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertEquals("RATING_NOT_FOUND", ex.getCode());
    }

    @Test
    void updateRating_invalidStarsBelow1_throwsBadRequest() {
        when(ratingRepository.findById(RATING_ID)).thenReturn(Optional.of(mockRating));

        ApiException ex = assertThrows(ApiException.class,
                () -> ratingService.updateRating(RATING_ID, 0, null));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("INVALID_STARS", ex.getCode());
    }

    @Test
    void updateRating_invalidStarsAbove5_throwsBadRequest() {
        when(ratingRepository.findById(RATING_ID)).thenReturn(Optional.of(mockRating));

        ApiException ex = assertThrows(ApiException.class,
                () -> ratingService.updateRating(RATING_ID, 6, null));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("INVALID_STARS", ex.getCode());
    }

    @Test
    void updateRating_validStars_updatesAndReturns() {
        when(ratingRepository.findById(RATING_ID)).thenReturn(Optional.of(mockRating));
        stubAdminRatingResponse();
        when(ratingRepository.save(mockRating)).thenReturn(mockRating);

        var response = ratingService.updateRating(RATING_ID, 3, "Updated comment");

        assertNotNull(response);
        verify(mockRating).updateStars(3);
        verify(mockRating).updateComment("Updated comment");
        verify(ratingRepository).save(mockRating);
    }

    // ── deleteRating ────────────────────────────────────────────────────────────

    @Test
    void deleteRating_ratingNotFound_throwsNotFound() {
        when(ratingRepository.findById(RATING_ID)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class,
                () -> ratingService.deleteRating(RATING_ID));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertEquals("RATING_NOT_FOUND", ex.getCode());
    }

    @Test
    void deleteRating_ratingExists_deletesIt() {
        when(ratingRepository.findById(RATING_ID)).thenReturn(Optional.of(mockRating));

        ratingService.deleteRating(RATING_ID);

        verify(ratingRepository).delete(mockRating);
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private static CreateRatingRequest request(Long warehouseId, Long reservationId, int stars) {
        CreateRatingRequest req = new CreateRatingRequest();
        req.setWarehouseId(warehouseId);
        req.setReservationId(reservationId);
        req.setStars(stars);
        return req;
    }

    /** Stubs the rating mock to allow RatingResponse.from(rating) to succeed. */
    private void stubSavedRating(int stars, String comment, boolean verified) {
        when(mockRating.getId()).thenReturn(RATING_ID);
        when(mockRating.getWarehouse()).thenReturn(mockWarehouse);
        when(mockRating.getReservation()).thenReturn(null);
        when(mockRating.getStars()).thenReturn(stars);
        when(mockRating.getComment()).thenReturn(comment);
        when(mockRating.getUser()).thenReturn(mockUser);
        when(mockUser.getFirstName()).thenReturn("John");
        when(mockUser.getLastName()).thenReturn("Doe");
        when(mockRating.getCreatedAt()).thenReturn(null);
        when(mockRating.isVerified()).thenReturn(verified);
        when(mockRating.getType()).thenReturn(Rating.RatingType.WAREHOUSE);
        when(ratingRepository.save(any(Rating.class))).thenReturn(mockRating);
    }

    /** Stubs the rating mock to allow AdminRatingResponse.from(rating) to succeed. */
    private void stubAdminRatingResponse() {
        when(mockRating.getId()).thenReturn(RATING_ID);
        when(mockRating.getWarehouse()).thenReturn(mockWarehouse);
        when(mockRating.getReservation()).thenReturn(null);
        when(mockRating.getStars()).thenReturn(3);
        when(mockRating.getComment()).thenReturn("Updated comment");
        when(mockRating.getUser()).thenReturn(mockUser);
        when(mockUser.getFullName()).thenReturn("John Doe");
        when(mockUser.getEmail()).thenReturn("john@example.com");
        when(mockRating.getCreatedAt()).thenReturn(null);
        when(mockRating.isVerified()).thenReturn(false);
        when(mockRating.getType()).thenReturn(Rating.RatingType.WAREHOUSE);
    }
}
