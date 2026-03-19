package com.tuempresa.storage.ops.application.dto;

import com.tuempresa.storage.ops.domain.QrHandoffApprovalStatus;

import java.time.Instant;

public record OpsApprovalItemResponse(
        Long id,
        Long reservationId,
        String reservationCode,
        QrHandoffApprovalStatus status,
        String messageForOperator,
        String messageForCustomer,
        String messageForCustomerTranslated,
        Long requestedByUserId,
        Long approvedByUserId,
        Instant createdAt,
        Instant approvedAt
) {
}
