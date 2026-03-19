package com.tuempresa.storage.ops.application.dto;

import com.tuempresa.storage.ops.domain.QrHandoffStage;
import com.tuempresa.storage.reservations.domain.ReservationStatus;

import java.time.Instant;
import java.util.List;

public record OpsQrCaseResponse(
        Long reservationId,
        String reservationCode,
        ReservationStatus reservationStatus,
        Long userId,
        String customerLanguage,
        String customerQrPayload,
        QrHandoffStage stage,
        String bagTagId,
        String bagTagQrPayload,
        Integer bagUnits,
        Boolean identityValidated,
        Boolean luggageMatched,
        Boolean operatorApprovalRequested,
        Boolean operatorApprovalGranted,
        Boolean deliveryCompleted,
        Instant pinExpiresAt,
        Boolean pinLocked,
        Instant pinLockedUntil,
        String latestMessageForCustomer,
        String latestMessageTranslated,
        String pickupPinPreview,
        Instant updatedAt,
        List<OpsApprovalItemResponse> approvals
) {
}
