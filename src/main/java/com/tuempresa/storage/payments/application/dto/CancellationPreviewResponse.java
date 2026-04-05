package com.tuempresa.storage.payments.application.dto;

import com.tuempresa.storage.payments.domain.BookingType;
import com.tuempresa.storage.payments.domain.CancellationPolicyType;

import java.math.BigDecimal;

public record CancellationPreviewResponse(
        Long reservationId,
        Long paymentAttemptId,
        BookingType bookingType,
        CancellationPolicyType policyType,
        String policyDescription,
        BigDecimal grossPaid,
        BigDecimal cancellationFee,
        BigDecimal refundToCustomer,
        BigDecimal retainedByBusiness,
        BigDecimal providerFee,
        boolean providerFeeApplied,
        boolean requiresRefund,
        boolean refundAllowed,
        String refundBlockedReason) {
}
