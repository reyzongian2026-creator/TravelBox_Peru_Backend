package com.tuempresa.storage.shared.infrastructure.security;

import com.tuempresa.storage.shared.domain.exception.ApiException;
import com.tuempresa.storage.users.domain.Role;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class SecurityUtils {

    public AuthUserPrincipal currentUserOrThrow() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUserPrincipal principal)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Debes iniciar sesion.");
        }
        return principal;
    }

    public Mono<AuthUserPrincipal> currentUserOrThrowReactive() {
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication())
                .filter(authentication -> authentication != null && authentication.getPrincipal() instanceof AuthUserPrincipal)
                .map(authentication -> (AuthUserPrincipal) authentication.getPrincipal())
                .switchIfEmpty(Mono.fromCallable(this::currentUserOrThrow));
    }

    public Mono<AuthUserPrincipal> currentUserReactive() {
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication())
                .filter(authentication -> authentication != null && authentication.getPrincipal() instanceof AuthUserPrincipal)
                .map(authentication -> (AuthUserPrincipal) authentication.getPrincipal())
                .switchIfEmpty(Mono.fromCallable(this::currentUserOrNull).flatMap(Mono::justOrEmpty));
    }

    public boolean hasAnyRole(Role... roles) {
        Set<String> requiredAuthorities = Arrays.stream(roles)
                .map(Role::asAuthority)
                .collect(Collectors.toSet());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> requiredAuthorities.contains(authority.getAuthority()));
    }

    private AuthUserPrincipal currentUserOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUserPrincipal principal)) {
            return null;
        }
        return principal;
    }
}
