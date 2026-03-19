package com.tuempresa.storage.incidents.domain;

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

import java.time.Instant;

@Entity
@Table(name = "incidents")
public class Incident extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "opened_by", nullable = false)
    private User openedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by")
    private User resolvedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private IncidentStatus status;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(length = 500)
    private String resolution;

    @Column
    private Instant resolvedAt;

    public static Incident open(Reservation reservation, User openedBy, String description) {
        Incident incident = new Incident();
        incident.reservation = reservation;
        incident.openedBy = openedBy;
        incident.status = IncidentStatus.OPEN;
        incident.description = description;
        return incident;
    }

    public Reservation getReservation() {
        return reservation;
    }

    public User getOpenedBy() {
        return openedBy;
    }

    public User getResolvedBy() {
        return resolvedBy;
    }

    public IncidentStatus getStatus() {
        return status;
    }

    public String getDescription() {
        return description;
    }

    public String getResolution() {
        return resolution;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void resolve(User resolver, String resolution) {
        this.resolvedBy = resolver;
        this.resolution = resolution;
        this.status = IncidentStatus.RESOLVED;
        this.resolvedAt = Instant.now();
    }
}
