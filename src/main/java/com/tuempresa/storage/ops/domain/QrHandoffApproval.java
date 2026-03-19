package com.tuempresa.storage.ops.domain;

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
@Table(name = "qr_handoff_approvals")
public class QrHandoffApproval extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by_user_id", nullable = false)
    private User requestedByUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_user_id")
    private User approvedByUser;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private QrHandoffApprovalStatus status;

    @Column(name = "message_for_operator", nullable = false, length = 260)
    private String messageForOperator;

    @Column(name = "message_for_customer", length = 320)
    private String messageForCustomer;

    @Column(name = "message_for_customer_translated", length = 320)
    private String messageForCustomerTranslated;

    @Column(name = "approved_at")
    private Instant approvedAt;

    public static QrHandoffApproval pending(
            Reservation reservation,
            User requestedByUser,
            String messageForOperator,
            String messageForCustomer,
            String messageForCustomerTranslated
    ) {
        QrHandoffApproval approval = new QrHandoffApproval();
        approval.reservation = reservation;
        approval.requestedByUser = requestedByUser;
        approval.status = QrHandoffApprovalStatus.PENDING;
        approval.messageForOperator = trimToLength(messageForOperator, 260);
        approval.messageForCustomer = trimToLength(messageForCustomer, 320);
        approval.messageForCustomerTranslated = trimToLength(messageForCustomerTranslated, 320);
        return approval;
    }

    public Reservation getReservation() {
        return reservation;
    }

    public User getRequestedByUser() {
        return requestedByUser;
    }

    public User getApprovedByUser() {
        return approvedByUser;
    }

    public QrHandoffApprovalStatus getStatus() {
        return status;
    }

    public String getMessageForOperator() {
        return messageForOperator;
    }

    public String getMessageForCustomer() {
        return messageForCustomer;
    }

    public String getMessageForCustomerTranslated() {
        return messageForCustomerTranslated;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public void approve(User approvedByUser) {
        this.approvedByUser = approvedByUser;
        this.status = QrHandoffApprovalStatus.APPROVED;
        this.approvedAt = Instant.now();
    }

    public void reject(User approvedByUser) {
        this.approvedByUser = approvedByUser;
        this.status = QrHandoffApprovalStatus.REJECTED;
        this.approvedAt = Instant.now();
    }

    private static String trimToLength(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, maxLength);
    }
}
