package com.tuempresa.storage.auth.infrastructure.in.web;

import com.tuempresa.storage.auth.application.EmailChangeService;
import com.tuempresa.storage.auth.application.dto.EmailChangeRequest;
import com.tuempresa.storage.auth.application.dto.EmailChangeResponse;
import com.tuempresa.storage.auth.application.dto.EmailChangeVerifyRequest;
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
public class EmailChangeController {

    private final EmailChangeService emailChangeService;
    private final SecurityUtils securityUtils;
    private final ReactiveBlockingExecutor reactiveBlockingExecutor;

    public EmailChangeController(
            EmailChangeService emailChangeService,
            SecurityUtils securityUtils,
            ReactiveBlockingExecutor reactiveBlockingExecutor
    ) {
        this.emailChangeService = emailChangeService;
        this.securityUtils = securityUtils;
        this.reactiveBlockingExecutor = reactiveBlockingExecutor;
    }

    @PostMapping("/email-change/initiate")
    public Mono<ResponseEntity<EmailChangeResponse>> initiateEmailChange(@Valid @RequestBody EmailChangeRequest request) {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                        () -> emailChangeService.initiateEmailChange(request, currentUser)
                ))
                .map(ResponseEntity::ok);
    }

    @PostMapping("/email-change/verify")
    public Mono<ResponseEntity<EmailChangeResponse>> verifyEmailChange(@Valid @RequestBody EmailChangeVerifyRequest request) {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                        () -> emailChangeService.verifyEmailChange(request, currentUser)
                ))
                .map(ResponseEntity::ok);
    }
}
