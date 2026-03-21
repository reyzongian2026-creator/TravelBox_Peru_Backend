package com.tuempresa.storage.reservations.application.dto;

import java.math.BigDecimal;

public class WarehouseRatingSummary {

    private Long warehouseId;
    private String warehouseName;
    private BigDecimal averageStars;
    private long totalRatings;
    private int stars1Count;
    private int stars2Count;
    private int stars3Count;
    private int stars4Count;
    private int stars5Count;

    public WarehouseRatingSummary() {
    }

    public WarehouseRatingSummary(Long warehouseId, String warehouseName, BigDecimal averageStars, long totalRatings) {
        this.warehouseId = warehouseId;
        this.warehouseName = warehouseName;
        this.averageStars = averageStars;
        this.totalRatings = totalRatings;
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

    public BigDecimal getAverageStars() {
        return averageStars;
    }

    public void setAverageStars(BigDecimal averageStars) {
        this.averageStars = averageStars;
    }

    public long getTotalRatings() {
        return totalRatings;
    }

    public void setTotalRatings(long totalRatings) {
        this.totalRatings = totalRatings;
    }

    public int getStars1Count() {
        return stars1Count;
    }

    public void setStars1Count(int stars1Count) {
        this.stars1Count = stars1Count;
    }

    public int getStars2Count() {
        return stars2Count;
    }

    public void setStars2Count(int stars2Count) {
        this.stars2Count = stars2Count;
    }

    public int getStars3Count() {
        return stars3Count;
    }

    public void setStars3Count(int stars3Count) {
        this.stars3Count = stars3Count;
    }

    public int getStars4Count() {
        return stars4Count;
    }

    public void setStars4Count(int stars4Count) {
        this.stars4Count = stars4Count;
    }

    public int getStars5Count() {
        return stars5Count;
    }

    public void setStars5Count(int stars5Count) {
        this.stars5Count = stars5Count;
    }
}
