package com.tuempresa.storage.users.application.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public record BulkIdsRequest(
        @NotEmpty Set<Long> ids
) {
}
