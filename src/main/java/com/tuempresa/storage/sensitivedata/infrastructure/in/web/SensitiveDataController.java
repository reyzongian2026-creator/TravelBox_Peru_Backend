package com.tuempresa.storage.sensitivedata.infrastructure.in.web;

import com.tuempresa.storage.sensitivedata.application.SensitiveDataServiceApp;
import com.tuempresa.storage.sensitivedata.application.dto.SensitiveDataDecryptRequest;
import com.tuempresa.storage.sensitivedata.application.dto.SensitiveDataResponse;
import com.tuempresa.storage.shared.infrastructure.reactive.ReactiveBlockingExecutor;
import com.tuempresa.storage.shared.infrastructure.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/sensitive-data")
@PreAuthorize("isAuthenticated()")
public class SensitiveDataController {

    private final SensitiveDataServiceApp sensitiveDataServiceApp;
    private final SecurityUtils securityUtils;
    private final ReactiveBlockingExecutor reactiveBlockingExecutor;

    public SensitiveDataController(
            SensitiveDataServiceApp sensitiveDataServiceApp,
            SecurityUtils securityUtils,
            ReactiveBlockingExecutor reactiveBlockingExecutor
    ) {
        this.sensitiveDataServiceApp = sensitiveDataServiceApp;
        this.securityUtils = securityUtils;
        this.reactiveBlockingExecutor = reactiveBlockingExecutor;
    }

    @PostMapping("/decrypt")
    public Mono<SensitiveDataResponse> decryptSensitiveData(@Valid @RequestBody SensitiveDataDecryptRequest request) {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                        () -> sensitiveDataServiceApp.decryptSensitiveData(request, currentUser)
                ));
    }
}
