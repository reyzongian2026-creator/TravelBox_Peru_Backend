package com.tuempresa.storage.delivery.domain;

import com.tuempresa.storage.reservations.domain.Reservation;
import com.tuempresa.storage.shared.infrastructure.persistence.AuditableEntity;
import com.tuempresa.storage.users.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "delivery_orders")
public class DeliveryOrder extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @Column(nullable = false, length = 30)
    private String type;

    @Column(nullable = false, length = 220)
    private String address;

    @Column(length = 120)
    private String zone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DeliveryStatus status;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal cost;

    @Column(name = "driver_name", length = 120)
    private String driverName;

    @Column(name = "driver_phone", length = 30)
    private String driverPhone;

    @Column(name = "vehicle_type", length = 40)
    private String vehicleType;

    @Column(name = "vehicle_plate", length = 30)
    private String vehiclePlate;

    @Column(name = "current_latitude")
    private Double currentLatitude;

    @Column(name = "current_longitude")
    private Double currentLongitude;

    @Column(name = "eta_minutes")
    private Integer etaMinutes;

    @Column(name = "tracking_stage", nullable = false)
    private int trackingStage;

    @Column(name = "destination_latitude")
    private Double destinationLatitude;

    @Column(name = "destination_longitude")
    private Double destinationLongitude;

    @Column(name = "next_tracking_at")
    private Instant nextTrackingAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_courier_id")
    private User assignedCourier;

    public static DeliveryOrder create(
            Reservation reservation,
            String type,
            String address,
            String zone,
            BigDecimal cost) {
        DeliveryOrder order = new DeliveryOrder();
        order.reservation = reservation;
        order.type = type;
        order.address = address;
        order.zone = zone;
        order.cost = cost;
        order.status = DeliveryStatus.REQUESTED;
        order.trackingStage = 0;
        return order;
    }

    public void configureMockTracking(
            String driverName,
            String driverPhone,
            String vehicleType,
            String vehiclePlate,
            double originLatitude,
            double originLongitude,
            double destinationLatitude,
            double destinationLongitude,
            int etaMinutes) {
        this.driverName = trim(driverName, 120);
        this.driverPhone = trim(driverPhone, 30);
        this.vehicleType = trim(vehicleType, 40);
        this.vehiclePlate = trim(vehiclePlate, 30);
        this.currentLatitude = originLatitude;
        this.currentLongitude = originLongitude;
        this.destinationLatitude = destinationLatitude;
        this.destinationLongitude = destinationLongitude;
        this.etaMinutes = Math.max(etaMinutes, 0);
        this.trackingStage = 0;
        this.nextTrackingAt = Instant.now();
    }

    public void advanceTracking(double latitude, double longitude, DeliveryStatus status, int etaMinutes,
            Instant nextTrackingAt) {
        this.currentLatitude = latitude;
        this.currentLongitude = longitude;
        this.status = status;
        this.etaMinutes = Math.max(etaMinutes, 0);
        this.nextTrackingAt = nextTrackingAt;
        this.trackingStage = Math.min(this.trackingStage + 1, 3);
    }

    public void assignCourier(User courier) {
        this.assignedCourier = courier;
        if (courier != null) {
            this.driverName = trim(courier.getFullName(), 120);
            this.driverPhone = trim(courier.getPhone(), 30);
        }
    }

    public void resetAssignment() {
        this.assignedCourier = null;
        this.driverName = null;
        this.driverPhone = null;
        this.vehicleType = null;
        this.vehiclePlate = null;
        this.status = DeliveryStatus.REQUESTED;
        this.trackingStage = 0;
    }

    public void updateVehicle(String vehicleType, String vehiclePlate) {
        if (vehicleType != null) {
            this.vehicleType = trim(vehicleType, 40);
        }
        if (vehiclePlate != null) {
            this.vehiclePlate = trim(vehiclePlate, 30);
        }
    }

    public void updateDriverPhone(String phone) {
        if (phone != null) {
            this.driverPhone = trim(phone, 30);
        }
    }

    public Reservation getReservation() {
        return reservation;
    }

    public String getType() {
        return type;
    }

    public String getAddress() {
        return address;
    }

    public String getZone() {
        return zone;
    }

    public DeliveryStatus getStatus() {
        return status;
    }

    public BigDecimal getCost() {
        return cost;
    }

    public String getDriverName() {
        return driverName;
    }

    public String getDriverPhone() {
        return driverPhone;
    }

    public String getVehicleType() {
        return vehicleType;
    }

    public String getVehiclePlate() {
        return vehiclePlate;
    }

    public Double getCurrentLatitude() {
        return currentLatitude;
    }

    public Double getCurrentLongitude() {
        return currentLongitude;
    }

    public Integer getEtaMinutes() {
        return etaMinutes;
    }

    public int getTrackingStage() {
        return trackingStage;
    }

    public Double getDestinationLatitude() {
        return destinationLatitude;
    }

    public Double getDestinationLongitude() {
        return destinationLongitude;
    }

    public Instant getNextTrackingAt() {
        return nextTrackingAt;
    }

    public User getAssignedCourier() {
        return assignedCourier;
    }

    private String trim(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized.length() > maxLength ? normalized.substring(0, maxLength) : normalized;
    }
}
