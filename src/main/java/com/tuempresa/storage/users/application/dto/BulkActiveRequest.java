package com.tuempresa.storage.users.application.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record BulkActiveRequest(
        @NotNull Set<Long> ids,
        @NotNull Boolean active
) {
}
