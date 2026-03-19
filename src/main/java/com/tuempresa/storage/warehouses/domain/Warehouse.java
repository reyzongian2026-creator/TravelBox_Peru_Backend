package com.tuempresa.storage.warehouses.domain;

import com.tuempresa.storage.geo.domain.City;
import com.tuempresa.storage.geo.domain.TouristZone;
import com.tuempresa.storage.shared.infrastructure.persistence.AuditableEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Table(name = "warehouses")
public class Warehouse extends AuditableEntity {

    public static final BigDecimal DEFAULT_PRICE_SMALL_PER_HOUR = new BigDecimal("4.00");
    public static final BigDecimal DEFAULT_PRICE_MEDIUM_PER_HOUR = new BigDecimal("4.50");
    public static final BigDecimal DEFAULT_PRICE_LARGE_PER_HOUR = new BigDecimal("5.50");
    public static final BigDecimal DEFAULT_PRICE_EXTRA_LARGE_PER_HOUR = new BigDecimal("6.50");
    public static final BigDecimal DEFAULT_PICKUP_FEE = new BigDecimal("14.00");
    public static final BigDecimal DEFAULT_DROPOFF_FEE = new BigDecimal("14.00");
    public static final BigDecimal DEFAULT_INSURANCE_FEE = new BigDecimal("7.50");

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "city_id", nullable = false)
    private City city;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private TouristZone zone;

    @Column(nullable = false, length = 140)
    private String name;

    @Column(nullable = false, length = 220)
    private String address;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false)
    private int occupied = 0;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, length = 10)
    private String openHour;

    @Column(nullable = false, length = 10)
    private String closeHour;

    @Column(length = 600)
    private String rules;

    @Column(name = "photo_path", length = 320)
    private String photoPath;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal pricePerHourSmall = DEFAULT_PRICE_SMALL_PER_HOUR;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal pricePerHourMedium = DEFAULT_PRICE_MEDIUM_PER_HOUR;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal pricePerHourLarge = DEFAULT_PRICE_LARGE_PER_HOUR;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal pricePerHourExtraLarge = DEFAULT_PRICE_EXTRA_LARGE_PER_HOUR;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal pickupFee = DEFAULT_PICKUP_FEE;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal dropoffFee = DEFAULT_DROPOFF_FEE;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal insuranceFee = DEFAULT_INSURANCE_FEE;

    public static Warehouse of(
            City city,
            TouristZone zone,
            String name,
            String address,
            double latitude,
            double longitude,
            int capacity,
            String openHour,
            String closeHour,
            String rules,
            BigDecimal pricePerHourSmall,
            BigDecimal pricePerHourMedium,
            BigDecimal pricePerHourLarge,
            BigDecimal pricePerHourExtraLarge,
            BigDecimal pickupFee,
            BigDecimal dropoffFee,
            BigDecimal insuranceFee
    ) {
        Warehouse warehouse = new Warehouse();
        warehouse.city = city;
        warehouse.zone = zone;
        warehouse.name = name;
        warehouse.address = address;
        warehouse.latitude = latitude;
        warehouse.longitude = longitude;
        warehouse.capacity = capacity;
        warehouse.openHour = openHour;
        warehouse.closeHour = closeHour;
        warehouse.rules = rules;
        warehouse.active = true;
        warehouse.occupied = 0;
        warehouse.pricePerHourSmall = normalizeMoney(pricePerHourSmall, DEFAULT_PRICE_SMALL_PER_HOUR);
        warehouse.pricePerHourMedium = normalizeMoney(pricePerHourMedium, DEFAULT_PRICE_MEDIUM_PER_HOUR);
        warehouse.pricePerHourLarge = normalizeMoney(pricePerHourLarge, DEFAULT_PRICE_LARGE_PER_HOUR);
        warehouse.pricePerHourExtraLarge = normalizeMoney(pricePerHourExtraLarge, DEFAULT_PRICE_EXTRA_LARGE_PER_HOUR);
        warehouse.pickupFee = normalizeMoney(pickupFee, DEFAULT_PICKUP_FEE);
        warehouse.dropoffFee = normalizeMoney(dropoffFee, DEFAULT_DROPOFF_FEE);
        warehouse.insuranceFee = normalizeMoney(insuranceFee, DEFAULT_INSURANCE_FEE);
        return warehouse;
    }

    public City getCity() {
        return city;
    }

    public TouristZone getZone() {
        return zone;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getOccupied() {
        return occupied;
    }

    public boolean isActive() {
        return active;
    }

    public String getOpenHour() {
        return openHour;
    }

    public String getCloseHour() {
        return closeHour;
    }

    public String getRules() {
        return rules;
    }

    public String getPhotoPath() {
        return photoPath;
    }

    public BigDecimal getPricePerHourSmall() {
        return normalizeMoney(pricePerHourSmall, DEFAULT_PRICE_SMALL_PER_HOUR);
    }

    public BigDecimal getPricePerHourMedium() {
        return normalizeMoney(pricePerHourMedium, DEFAULT_PRICE_MEDIUM_PER_HOUR);
    }

    public BigDecimal getPricePerHourLarge() {
        return normalizeMoney(pricePerHourLarge, DEFAULT_PRICE_LARGE_PER_HOUR);
    }

    public BigDecimal getPricePerHourExtraLarge() {
        return normalizeMoney(pricePerHourExtraLarge, DEFAULT_PRICE_EXTRA_LARGE_PER_HOUR);
    }

    public BigDecimal getPickupFee() {
        return normalizeMoney(pickupFee, DEFAULT_PICKUP_FEE);
    }

    public BigDecimal getDropoffFee() {
        return normalizeMoney(dropoffFee, DEFAULT_DROPOFF_FEE);
    }

    public BigDecimal getInsuranceFee() {
        return normalizeMoney(insuranceFee, DEFAULT_INSURANCE_FEE);
    }

    public int availableSlots() {
        return Math.max(0, capacity - occupied);
    }

    public boolean hasAvailableCapacity() {
        return availableSlots() > 0;
    }

    public void occupyOneSlot() {
        if (occupied < capacity) {
            occupied += 1;
        }
    }

    public void releaseOneSlot() {
        if (occupied > 0) {
            occupied -= 1;
        }
    }

    public void update(
            City city,
            TouristZone zone,
            String name,
            String address,
            double latitude,
            double longitude,
            int capacity,
            String openHour,
            String closeHour,
            String rules,
            boolean active,
            BigDecimal pricePerHourSmall,
            BigDecimal pricePerHourMedium,
            BigDecimal pricePerHourLarge,
            BigDecimal pricePerHourExtraLarge,
            BigDecimal pickupFee,
            BigDecimal dropoffFee,
            BigDecimal insuranceFee
    ) {
        this.city = city;
        this.zone = zone;
        this.name = name;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.capacity = capacity;
        this.openHour = openHour;
        this.closeHour = closeHour;
        this.rules = rules;
        this.active = active;
        this.pricePerHourSmall = normalizeMoney(
                pricePerHourSmall,
                this.pricePerHourSmall != null ? this.pricePerHourSmall : DEFAULT_PRICE_SMALL_PER_HOUR
        );
        this.pricePerHourMedium = normalizeMoney(
                pricePerHourMedium,
                this.pricePerHourMedium != null ? this.pricePerHourMedium : DEFAULT_PRICE_MEDIUM_PER_HOUR
        );
        this.pricePerHourLarge = normalizeMoney(
                pricePerHourLarge,
                this.pricePerHourLarge != null ? this.pricePerHourLarge : DEFAULT_PRICE_LARGE_PER_HOUR
        );
        this.pricePerHourExtraLarge = normalizeMoney(
                pricePerHourExtraLarge,
                this.pricePerHourExtraLarge != null ? this.pricePerHourExtraLarge : DEFAULT_PRICE_EXTRA_LARGE_PER_HOUR
        );
        this.pickupFee = normalizeMoney(
                pickupFee,
                this.pickupFee != null ? this.pickupFee : DEFAULT_PICKUP_FEE
        );
        this.dropoffFee = normalizeMoney(
                dropoffFee,
                this.dropoffFee != null ? this.dropoffFee : DEFAULT_DROPOFF_FEE
        );
        this.insuranceFee = normalizeMoney(
                insuranceFee,
                this.insuranceFee != null ? this.insuranceFee : DEFAULT_INSURANCE_FEE
        );
        if (this.occupied > this.capacity) {
            this.occupied = this.capacity;
        }
    }

    public void deactivate() {
        this.active = false;
    }

    public void updatePhoto(String photoPath) {
        this.photoPath = photoPath;
    }

    @PostLoad
    @PrePersist
    @PreUpdate
    private void ensurePricingDefaults() {
        this.pricePerHourSmall = normalizeMoney(this.pricePerHourSmall, DEFAULT_PRICE_SMALL_PER_HOUR);
        this.pricePerHourMedium = normalizeMoney(this.pricePerHourMedium, DEFAULT_PRICE_MEDIUM_PER_HOUR);
        this.pricePerHourLarge = normalizeMoney(this.pricePerHourLarge, DEFAULT_PRICE_LARGE_PER_HOUR);
        this.pricePerHourExtraLarge = normalizeMoney(this.pricePerHourExtraLarge, DEFAULT_PRICE_EXTRA_LARGE_PER_HOUR);
        this.pickupFee = normalizeMoney(this.pickupFee, DEFAULT_PICKUP_FEE);
        this.dropoffFee = normalizeMoney(this.dropoffFee, DEFAULT_DROPOFF_FEE);
        this.insuranceFee = normalizeMoney(this.insuranceFee, DEFAULT_INSURANCE_FEE);
    }

    private static BigDecimal normalizeMoney(BigDecimal value, BigDecimal fallback) {
        BigDecimal resolved = value != null ? value : fallback;
        if (resolved == null) {
            resolved = BigDecimal.ZERO;
        }
        if (resolved.signum() < 0) {
            BigDecimal safeFallback = fallback != null ? fallback : BigDecimal.ZERO;
            return safeFallback.setScale(2, RoundingMode.HALF_UP);
        }
        return resolved.setScale(2, RoundingMode.HALF_UP);
    }
}
