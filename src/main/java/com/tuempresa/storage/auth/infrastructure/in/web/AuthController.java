package com.tuempresa.storage.auth.infrastructure.in.web;

import com.tuempresa.storage.auth.application.dto.AuthTokenResponse;
import com.tuempresa.storage.auth.application.dto.EmailVerificationResponse;
import com.tuempresa.storage.auth.application.dto.FirebaseSocialAuthRequest;
import com.tuempresa.storage.auth.application.dto.LoginRequest;
import com.tuempresa.storage.auth.application.dto.LogoutRequest;
import com.tuempresa.storage.auth.application.dto.PasswordResetConfirmRequest;
import com.tuempresa.storage.auth.application.dto.PasswordResetRequest;
import com.tuempresa.storage.auth.application.dto.PasswordResetResponse;
import com.tuempresa.storage.auth.application.dto.RefreshRequest;
import com.tuempresa.storage.auth.application.dto.RegisterRequest;
import com.tuempresa.storage.auth.application.dto.VerifyEmailRequest;
import com.tuempresa.storage.auth.application.usecase.AuthService;
import com.tuempresa.storage.shared.infrastructure.reactive.ReactiveBlockingExecutor;
import com.tuempresa.storage.shared.infrastructure.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final SecurityUtils securityUtils;
    private final ReactiveBlockingExecutor reactiveBlockingExecutor;

    public AuthController(
            AuthService authService,
            SecurityUtils securityUtils,
            ReactiveBlockingExecutor reactiveBlockingExecutor
    ) {
        this.authService = authService;
        this.securityUtils = securityUtils;
        this.reactiveBlockingExecutor = reactiveBlockingExecutor;
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<AuthTokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        return reactiveBlockingExecutor.call(() -> authService.login(request))
                .map(ResponseEntity::ok);
    }

    @PostMapping("/register")
    public Mono<ResponseEntity<AuthTokenResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return reactiveBlockingExecutor.call(() -> authService.register(request))
                .map(ResponseEntity::ok);
    }

    @PostMapping("/firebase/social")
    public Mono<ResponseEntity<AuthTokenResponse>> firebaseSocial(@Valid @RequestBody FirebaseSocialAuthRequest request) {
        return reactiveBlockingExecutor.call(() -> authService.firebaseSocialLogin(request))
                .map(ResponseEntity::ok);
    }

    @PostMapping("/refresh")
    public Mono<ResponseEntity<AuthTokenResponse>> refresh(@Valid @RequestBody RefreshRequest request) {
        return reactiveBlockingExecutor.call(() -> authService.refresh(request))
                .map(ResponseEntity::ok);
    }

    @PostMapping("/logout")
    public Mono<ResponseEntity<Void>> logout(@Valid @RequestBody LogoutRequest request) {
        return reactiveBlockingExecutor.call(() -> {
                    authService.logout(request);
                    return ResponseEntity.noContent().build();
                });
    }

    @PostMapping("/verify-email")
    public Mono<ResponseEntity<EmailVerificationResponse>> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        return securityUtils.currentUserReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(() -> authService.verifyEmail(request, currentUser)))
                .switchIfEmpty(reactiveBlockingExecutor.call(() -> authService.verifyEmail(request, null)))
                .map(ResponseEntity::ok);
    }

    @PostMapping("/resend-verification")
    public Mono<ResponseEntity<EmailVerificationResponse>> resendVerification() {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                        () -> authService.resendVerification(currentUser)
                ))
                .map(ResponseEntity::ok);
    }

    @PostMapping("/password-reset/request")
    public Mono<ResponseEntity<PasswordResetResponse>> requestPasswordReset(
            @Valid @RequestBody PasswordResetRequest request
    ) {
        return reactiveBlockingExecutor.call(() -> authService.requestPasswordReset(request))
                .map(ResponseEntity::ok);
    }

    @PostMapping("/password-reset/confirm")
    public Mono<ResponseEntity<PasswordResetResponse>> confirmPasswordReset(
            @Valid @RequestBody PasswordResetConfirmRequest request
    ) {
        return reactiveBlockingExecutor.call(() -> authService.confirmPasswordReset(request))
                .map(ResponseEntity::ok);
    }
}
