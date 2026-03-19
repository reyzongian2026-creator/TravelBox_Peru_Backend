package com.tuempresa.storage.delivery.domain;

import com.tuempresa.storage.shared.infrastructure.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "delivery_tracking_events")
public class DeliveryTrackingEvent extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delivery_order_id", nullable = false)
    private DeliveryOrder deliveryOrder;

    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DeliveryStatus status;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(name = "eta_minutes")
    private Integer etaMinutes;

    @Column(length = 220)
    private String message;

    public static DeliveryTrackingEvent of(
            DeliveryOrder deliveryOrder,
            int sequenceNumber,
            DeliveryStatus status,
            double latitude,
            double longitude,
            Integer etaMinutes,
            String message
    ) {
        DeliveryTrackingEvent event = new DeliveryTrackingEvent();
        event.deliveryOrder = deliveryOrder;
        event.sequenceNumber = sequenceNumber;
        event.status = status;
        event.latitude = latitude;
        event.longitude = longitude;
        event.etaMinutes = etaMinutes;
        event.message = message;
        return event;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public DeliveryStatus getStatus() {
        return status;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public Integer getEtaMinutes() {
        return etaMinutes;
    }

    public String getMessage() {
        return message;
    }
}
