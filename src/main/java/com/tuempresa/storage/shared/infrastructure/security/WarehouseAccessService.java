package com.tuempresa.storage.shared.infrastructure.security;

import com.tuempresa.storage.shared.domain.exception.ApiException;
import com.tuempresa.storage.users.domain.Role;
import com.tuempresa.storage.users.domain.User;
import com.tuempresa.storage.users.infrastructure.out.persistence.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

@Service
public class WarehouseAccessService {

    private static final Set<String> OPERATIONAL_WAREHOUSE_SCOPED_ROLES = Set.of(
            Role.OPERATOR.name(),
            Role.CITY_SUPERVISOR.name(),
            Role.COURIER.name(),
            Role.SUPPORT.name()
    );

    private final UserRepository userRepository;

    public WarehouseAccessService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public boolean isAdmin(AuthUserPrincipal principal) {
        return principal.roleNames().contains(Role.ADMIN.name());
    }

    public boolean isSupport(AuthUserPrincipal principal) {
        return principal.roleNames().contains(Role.SUPPORT.name());
    }

    public boolean isOperatorOrCitySupervisor(AuthUserPrincipal principal) {
        Set<String> roles = principal.roleNames();
        return roles.contains(Role.OPERATOR.name()) || roles.contains(Role.CITY_SUPERVISOR.name());
    }

    public boolean isCourier(AuthUserPrincipal principal) {
        return principal.roleNames().contains(Role.COURIER.name());
    }

    public boolean isWarehouseScopedRole(AuthUserPrincipal principal) {
        return principal.roleNames().stream().anyMatch(OPERATIONAL_WAREHOUSE_SCOPED_ROLES::contains);
    }

    @Transactional(readOnly = true)
    public Set<Long> assignedWarehouseIds(AuthUserPrincipal principal) {
        if (!isWarehouseScopedRole(principal)) {
            return Set.of();
        }
        User user = userRepository.findWithRolesAndWarehouseAssignmentsById(principal.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_INVALID", "Usuario invalido."));
        return user.getWarehouseAssignments()
                .stream()
                .map(warehouse -> warehouse.getId())
                .collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public boolean canAccessWarehouse(AuthUserPrincipal principal, Long warehouseId) {
        if (warehouseId == null) {
            return false;
        }
        if (isAdmin(principal)) {
            return true;
        }
        if (!isWarehouseScopedRole(principal)) {
            return false;
        }
        return assignedWarehouseIds(principal).contains(warehouseId);
    }
}
