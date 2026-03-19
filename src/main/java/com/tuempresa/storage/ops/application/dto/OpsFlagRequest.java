package com.tuempresa.storage.ops.application.dto;

import jakarta.validation.constraints.NotNull;

public record OpsFlagRequest(
        @NotNull(message = "El valor booleano es obligatorio.")
        Boolean value
) {
}
