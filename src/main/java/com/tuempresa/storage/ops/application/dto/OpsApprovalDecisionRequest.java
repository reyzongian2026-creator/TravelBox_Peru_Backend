package com.tuempresa.storage.ops.application.dto;

import jakarta.validation.constraints.Pattern;

public record OpsApprovalDecisionRequest(
        @Pattern(regexp = "^\\d{4,8}$", message = "El PIN debe contener solo numeros (4 a 8 digitos).")
        String pin
) {
}
