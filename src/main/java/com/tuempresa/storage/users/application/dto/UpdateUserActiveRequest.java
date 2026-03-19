package com.tuempresa.storage.users.application.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateUserActiveRequest(
        @NotNull Boolean active
) {
}
