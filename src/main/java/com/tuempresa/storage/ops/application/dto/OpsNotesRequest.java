package com.tuempresa.storage.ops.application.dto;

import jakarta.validation.constraints.Size;

public record OpsNotesRequest(
        @Size(max = 400, message = "Las notas exceden el maximo permitido.")
        String notes
) {
}
