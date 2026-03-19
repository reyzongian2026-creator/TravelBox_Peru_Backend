package com.tuempresa.storage.users.application.dto;

import com.tuempresa.storage.users.domain.Role;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public record UpdateUserRolesRequest(
        @NotEmpty Set<Role> roles,
        Set<Long> warehouseIds
) {
}
