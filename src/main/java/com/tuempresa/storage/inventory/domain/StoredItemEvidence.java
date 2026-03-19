package com.tuempresa.storage.inventory.domain;

import com.tuempresa.storage.reservations.domain.Reservation;
import com.tuempresa.storage.shared.infrastructure.persistence.AuditableEntity;
import com.tuempresa.storage.users.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "stored_item_evidences")
public class StoredItemEvidence extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "operator_id", nullable = false)
    private User operator;

    @Column(nullable = false, length = 30)
    private String type;

    @Column(nullable = false, length = 300)
    private String url;

    @Column(length = 240)
    private String observation;

    @Column(name = "bag_unit_index")
    private Integer bagUnitIndex;

    @Column(nullable = false)
    private boolean locked = false;

    public static StoredItemEvidence of(
            Reservation reservation,
            User operator,
            String type,
            String url,
            String observation
    ) {
        StoredItemEvidence evidence = new StoredItemEvidence();
        evidence.reservation = reservation;
        evidence.operator = operator;
        evidence.type = type;
        evidence.url = url;
        evidence.observation = observation;
        return evidence;
    }

    public static StoredItemEvidence luggagePhoto(
            Reservation reservation,
            User operator,
            String url,
            Integer bagUnitIndex,
            String observation
    ) {
        StoredItemEvidence evidence = of(
                reservation,
                operator,
                "CHECKIN_BAG_PHOTO",
                url,
                observation
        );
        evidence.bagUnitIndex = bagUnitIndex;
        evidence.locked = true;
        return evidence;
    }

    public Reservation getReservation() {
        return reservation;
    }

    public User getOperator() {
        return operator;
    }

    public String getType() {
        return type;
    }

    public String getUrl() {
        return url;
    }

    public String getObservation() {
        return observation;
    }

    public Integer getBagUnitIndex() {
        return bagUnitIndex;
    }

    public boolean isLocked() {
        return locked;
    }
}
