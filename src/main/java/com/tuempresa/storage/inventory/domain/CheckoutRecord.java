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
@Table(name = "checkout_records")
public class CheckoutRecord extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "operator_id", nullable = false)
    private User operator;

    @Column(length = 400)
    private String notes;

    public static CheckoutRecord of(Reservation reservation, User operator, String notes) {
        CheckoutRecord record = new CheckoutRecord();
        record.reservation = reservation;
        record.operator = operator;
        record.notes = notes;
        return record;
    }

    public Reservation getReservation() {
        return reservation;
    }

    public User getOperator() {
        return operator;
    }

    public String getNotes() {
        return notes;
    }
}
