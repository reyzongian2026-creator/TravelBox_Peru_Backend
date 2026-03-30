package com.tuempresa.storage.auth.infrastructure.in.web;

import com.tuempresa.storage.auth.application.dto.AuthTokenResponse;
import com.tuempresa.storage.auth.application.dto.EntraSocialAuthRequest;
import com.tuempresa.storage.auth.application.dto.EmailVerificationResponse;
import com.tuempresa.storage.auth.application.dto.LoginRequest;
import com.tuempresa.storage.auth.application.dto.LogoutRequest;
import com.tuempresa.storage.auth.application.dto.PasswordResetConfirmRequest;
import com.tuempresa.storage.auth.application.dto.PasswordResetRequest;
import com.tuempresa.storage.auth.application.dto.PasswordResetResponse;
import com.tuempresa.storage.auth.application.dto.RefreshRequest;
import com.tuempresa.storage.auth.application.dto.RealEmailRequest;
import com.tuempresa.storage.auth.application.dto.RegisterRequest;
import com.tuempresa.storage.auth.application.dto.VerifyEmailRequest;
import com.tuempresa.storage.auth.application.usecase.AuthService;
import com.tuempresa.storage.auth.application.usecase.SocialOAuthService;
import com.tuempresa.storage.shared.infrastructure.reactive.ReactiveBlockingExecutor;
import com.tuempresa.storage.shared.infrastructure.security.SecurityUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final SocialOAuthService socialOAuthService;
    private final SecurityUtils securityUtils;
    private final ReactiveBlockingExecutor reactiveBlockingExecutor;

    public AuthController(
            AuthService authService,
            SocialOAuthService socialOAuthService,
            SecurityUtils securityUtils,
            ReactiveBlockingExecutor reactiveBlockingExecutor
    ) {
        this.authService = authService;
        this.socialOAuthService = socialOAuthService;
        this.securityUtils = securityUtils;
        this.reactiveBlockingExecutor = reactiveBlockingExecutor;
    }

    @GetMapping("/oauth/{provider}/start")
    public ResponseEntity<Void> startSocialOAuth(
            @PathVariable String provider,
            @RequestParam(required = false) String redirectUri
    ) {
        URI location = socialOAuthService.buildAuthorizationRedirect(provider, redirectUri);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, location.toString())
                .build();
    }

    @GetMapping("/oauth/{provider}/callback")
    public ResponseEntity<Void> finishSocialOAuth(
            @PathVariable String provider,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String error_description
    ) {
        URI location = socialOAuthService.handleCallback(provider, code, state, error, error_description);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, location.toString())
                .build();
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

    @PostMapping("/entra/social")
    public Mono<ResponseEntity<AuthTokenResponse>> entraSocial(@Valid @RequestBody EntraSocialAuthRequest request) {
        return reactiveBlockingExecutor.call(() -> authService.entraSocialLogin(request))
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

    @PostMapping("/real-email/request")
    public Mono<ResponseEntity<EmailVerificationResponse>> requestRealEmail(
            @Valid @RequestBody RealEmailRequest request
    ) {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                        () -> authService.requestRealEmailCompletion(request, currentUser)
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
