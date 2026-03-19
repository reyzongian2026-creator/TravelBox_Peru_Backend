package com.tuempresa.storage.users.domain;

public enum Role {
    CLIENT,
    COURIER,
    OPERATOR,
    CITY_SUPERVISOR,
    ADMIN,
    SUPPORT;

    public String asAuthority() {
        return "ROLE_" + name();
    }
}
