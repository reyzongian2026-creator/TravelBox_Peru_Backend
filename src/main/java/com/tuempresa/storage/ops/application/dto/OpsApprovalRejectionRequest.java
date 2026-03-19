package com.tuempresa.storage.ops.application.dto;

import jakarta.validation.constraints.Size;

public record OpsApprovalRejectionRequest(
        @Size(max = 260, message = "La razon excede el maximo permitido.")
        String reason
) {
}
