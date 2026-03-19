package com.tuempresa.storage.ops.domain;

import com.tuempresa.storage.reservations.domain.ReservationStatus;

import java.util.Set;

public final class OpsWorkflowConstants {

    private OpsWorkflowConstants() {
    }

    public static final String OPS_QR_ROUTE = "/ops/qr-handoff";

    public static final String ERROR_DELIVERY_FLOW_REQUIRED = "DELIVERY_FLOW_REQUIRED";
    public static final String ERROR_DELIVERY_STEP_ORDER_INVALID = "DELIVERY_STEP_ORDER_INVALID";
    public static final String ERROR_PICKUP_FLOW_REQUIRED = "PICKUP_FLOW_REQUIRED";
    public static final String ERROR_DELIVERY_APPROVAL_ALREADY_PENDING = "DELIVERY_APPROVAL_ALREADY_PENDING";

    public static final Set<ReservationStatus> DELIVERY_VALIDATION_ALLOWED_STATUSES = Set.of(
            ReservationStatus.OUT_FOR_DELIVERY
    );
    public static final Set<ReservationStatus> PICKUP_PIN_ALLOWED_STATUSES = Set.of(
            ReservationStatus.READY_FOR_PICKUP
    );
}
