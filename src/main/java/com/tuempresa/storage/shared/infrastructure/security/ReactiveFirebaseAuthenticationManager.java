package com.tuempresa.storage.shared.infrastructure.security;

import com.tuempresa.storage.firebase.application.FirebaseAdminService;
import com.tuempresa.storage.firebase.application.FirebaseClientIdentity;
import com.tuempresa.storage.shared.domain.exception.ApiException;
import com.tuempresa.storage.shared.infrastructure.reactive.ReactiveBlockingExecutor;
import com.tuempresa.storage.users.domain.User;
import com.tuempresa.storage.users.infrastructure.out.persistence.UserRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
public class ReactiveFirebaseAuthenticationManager implements ReactiveAuthenticationManager {

    private final FirebaseAdminService firebaseAdminService;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final ReactiveBlockingExecutor reactiveBlockingExecutor;

    public ReactiveFirebaseAuthenticationManager(
            FirebaseAdminService firebaseAdminService,
            UserRepository userRepository,
            JwtTokenProvider jwtTokenProvider,
            ReactiveBlockingExecutor reactiveBlockingExecutor
    ) {
        this.firebaseAdminService = firebaseAdminService;
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.reactiveBlockingExecutor = reactiveBlockingExecutor;
    }

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) throws AuthenticationException {
        if (authentication == null || authentication.getCredentials() == null) {
            return Mono.empty();
        }
        String token = String.valueOf(authentication.getCredentials());
        if (token.isBlank()) {
            return Mono.empty();
        }
        return resolveUserByToken(token)
                .map(user -> {
                    AuthUserPrincipal principal = AuthUserPrincipal.from(user);
                    return (Authentication) new UsernamePasswordAuthenticationToken(
                            principal,
                            token,
                            principal.getAuthorities()
                    );
                })
                .onErrorMap(ApiException.class, ex -> new BadCredentialsException(ex.getMessage(), ex));
    }

    private Mono<User> resolveUserByToken(String token) {
        if (looksLikeJwt(token)) {
            if (!jwtTokenProvider.isValid(token)) {
                return Mono.error(new BadCredentialsException("Token de autenticacion invalido."));
            }
            return reactiveBlockingExecutor.call(() -> resolveJwtUser(token));
        }
        return reactiveBlockingExecutor.call(() -> firebaseAdminService.verifyClientIdToken(token))
                .flatMap(identity -> reactiveBlockingExecutor.call(() -> resolveFirebaseUser(identity)));
    }

    private boolean looksLikeJwt(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        int firstDot = token.indexOf('.');
        if (firstDot <= 0) {
            return false;
        }
        int secondDot = token.indexOf('.', firstDot + 1);
        return secondDot > firstDot + 1 && secondDot < token.length() - 1;
    }

    private User resolveJwtUser(String token) {
        String email = jwtTokenProvider.extractSubject(token);
        if (email == null || email.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_INVALID", "Token de autenticacion invalido.");
        }
        return userRepository.findByEmailIgnoreCase(email)
                .filter(User::isActive)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        "AUTH_INVALID",
                        "El usuario del token no existe o esta inactivo."
                ));
    }

    private User resolveFirebaseUser(FirebaseClientIdentity identity) {
        User user = userRepository.findByFirebaseUid(identity.uid())
                .or(() -> userRepository.findByEmailIgnoreCase(identity.email()))
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        "FIREBASE_ACCOUNT_NOT_LINKED",
                        "No existe un usuario local vinculado al token de Firebase."
                ));
        if (!user.isActive()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_INACTIVE_USER", "El usuario esta inactivo.");
        }
        if (user.getFirebaseUid() == null || !user.getFirebaseUid().equals(identity.uid())) {
            user.linkFirebaseIdentity(identity.authProvider(), identity.uid());
            user = userRepository.save(user);
        }
        return user;
    }
}
