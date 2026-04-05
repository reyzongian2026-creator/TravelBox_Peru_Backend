package com.tuempresa.storage.shared.infrastructure.security;

import com.tuempresa.storage.shared.infrastructure.reactive.ReactiveBlockingExecutor;
import com.tuempresa.storage.users.domain.User;
import com.tuempresa.storage.users.infrastructure.out.persistence.UserRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
public class ReactiveJwtAuthenticationManager implements ReactiveAuthenticationManager {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final ReactiveBlockingExecutor reactiveBlockingExecutor;

    public ReactiveJwtAuthenticationManager(
            UserRepository userRepository,
            JwtTokenProvider jwtTokenProvider,
            ReactiveBlockingExecutor reactiveBlockingExecutor) {
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.reactiveBlockingExecutor = reactiveBlockingExecutor;
    }

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) throws AuthenticationException {
        if (authentication == null || authentication.getCredentials() == null) {
            return Mono.empty();
        }
        String token = String.valueOf(authentication.getCredentials()).trim();
        if (token.isEmpty()) {
            return Mono.empty();
        }
        // Handle SSE tokens resolved by converter
        if (ReactiveBearerTokenServerAuthenticationConverter.isSseResolved(token)) {
            String username = ReactiveBearerTokenServerAuthenticationConverter.extractSseUsername(token);
            return reactiveBlockingExecutor.call(() -> resolveUserByEmail(username))
                    .map(user -> {
                        AuthUserPrincipal principal = AuthUserPrincipal.from(user);
                        return (Authentication) new UsernamePasswordAuthenticationToken(
                                principal, null, principal.getAuthorities());
                    });
        }
        if (!jwtTokenProvider.isValid(token)) {
            return Mono.error(new BadCredentialsException("Token de autenticacion invalido."));
        }
        return reactiveBlockingExecutor.call(() -> resolveUser(token))
                .map(user -> {
                    AuthUserPrincipal principal = AuthUserPrincipal.from(user);
                    return (Authentication) new UsernamePasswordAuthenticationToken(
                            principal,
                            token,
                            principal.getAuthorities());
                });
    }

    private User resolveUser(String token) {
        String email = jwtTokenProvider.extractSubject(token);
        if (email == null || email.isBlank()) {
            throw new BadCredentialsException("Token de autenticacion invalido.");
        }
        return resolveUserByEmail(email);
    }

    private User resolveUserByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .filter(User::isActive)
                .orElseThrow(() -> new BadCredentialsException(
                        "El usuario del token no existe o esta inactivo."));
    }
}
