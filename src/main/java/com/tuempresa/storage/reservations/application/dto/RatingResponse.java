package com.tuempresa.storage.reservations.application.dto;

import com.tuempresa.storage.reservations.domain.Rating;

import java.math.BigDecimal;
import java.time.Instant;

public class RatingResponse {

    private Long id;
    private Long warehouseId;
    private String warehouseName;
    private Long reservationId;
    private Integer stars;
    private String comment;
    private String userName;
    private String userAvatar;
    private Instant createdAt;
    private boolean verified;
    private String type;

    public static RatingResponse from(Rating rating) {
        RatingResponse response = new RatingResponse();
        response.id = rating.getId();
        response.warehouseId = rating.getWarehouse().getId();
        response.warehouseName = rating.getWarehouse().getName();
        response.reservationId = rating.getReservation() != null ? rating.getReservation().getId() : null;
        response.stars = rating.getStars();
        response.comment = rating.getComment();
        response.userName = formatUserName(rating);
        response.userAvatar = null;
        response.createdAt = rating.getCreatedAt();
        response.verified = rating.isVerified();
        response.type = rating.getType().name();
        return response;
    }

    private static String formatUserName(Rating rating) {
        String firstName = rating.getUser().getFirstName();
        String lastName = rating.getUser().getLastName();
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        } else if (firstName != null) {
            return firstName;
        } else if (lastName != null) {
            return lastName;
        }
        return "Usuario";
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    public String getWarehouseName() {
        return warehouseName;
    }

    public void setWarehouseName(String warehouseName) {
        this.warehouseName = warehouseName;
    }

    public Long getReservationId() {
        return reservationId;
    }

    public void setReservationId(Long reservationId) {
        this.reservationId = reservationId;
    }

    public Integer getStars() {
        return stars;
    }

    public void setStars(Integer stars) {
        this.stars = stars;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserAvatar() {
        return userAvatar;
    }

    public void setUserAvatar(String userAvatar) {
        this.userAvatar = userAvatar;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
