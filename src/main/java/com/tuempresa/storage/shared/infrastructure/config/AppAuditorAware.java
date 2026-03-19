package com.tuempresa.storage.shared.infrastructure.config;

import com.tuempresa.storage.shared.infrastructure.security.AuthUserPrincipal;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class AppAuditorAware implements AuditorAware<String> {

    @Override
    public Optional<String> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.of("system");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof AuthUserPrincipal userPrincipal) {
            return Optional.of(userPrincipal.getUsername());
        }
        if (principal instanceof String username) {
            return Optional.of(username);
        }
        return Optional.of("system");
    }
}
