package com.tuempresa.storage.reservations.domain;

import com.tuempresa.storage.shared.infrastructure.persistence.AuditableEntity;
import com.tuempresa.storage.users.domain.User;
import com.tuempresa.storage.warehouses.domain.Warehouse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "ratings")
public class Rating extends AuditableEntity {

    public enum RatingType {
        WAREHOUSE,
        SERVICE
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "reservation_id", nullable = true)
    private Reservation reservation;

    @Column(nullable = false)
    private int stars;

    @Column(length = 500)
    private String comment;

    @Column(nullable = false, unique = true, length = 60)
    private String reviewToken;

    @Column(nullable = false)
    private boolean verified = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RatingType type = RatingType.WAREHOUSE;

    public static Rating create(
            User user,
            Warehouse warehouse,
            Reservation reservation,
            int stars,
            String comment,
            RatingType type
    ) {
        validateStars(stars);
        Rating rating = new Rating();
        rating.user = user;
        rating.warehouse = warehouse;
        rating.reservation = reservation;
        rating.stars = stars;
        rating.comment = comment != null ? comment.trim() : null;
        rating.type = type != null ? type : RatingType.WAREHOUSE;
        rating.reviewToken = UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        return rating;
    }

    private static void validateStars(int stars) {
        if (stars < 1 || stars > 5) {
            throw new com.tuempresa.storage.shared.domain.exception.ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_STARS",
                    "Stars must be between 1 and 5"
            );
        }
    }

    public void markAsVerified() {
        this.verified = true;
    }

    public User getUser() {
        return user;
    }

    public Warehouse getWarehouse() {
        return warehouse;
    }

    public Reservation getReservation() {
        return reservation;
    }

    public int getStars() {
        return stars;
    }

    public String getComment() {
        return comment;
    }

    public String getReviewToken() {
        return reviewToken;
    }

    public boolean isVerified() {
        return verified;
    }

    public RatingType getType() {
        return type;
    }

    public void updateStars(int stars) {
        validateStars(stars);
        this.stars = stars;
    }

    public void updateComment(String comment) {
        this.comment = comment != null ? comment.trim() : null;
    }
}
