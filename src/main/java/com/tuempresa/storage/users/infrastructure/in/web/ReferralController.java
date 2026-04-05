package com.tuempresa.storage.users.infrastructure.in.web;

import com.tuempresa.storage.shared.infrastructure.reactive.ReactiveBlockingExecutor;
import com.tuempresa.storage.shared.infrastructure.security.SecurityUtils;
import com.tuempresa.storage.users.application.usecase.ReferralService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/referrals")
public class ReferralController {

    private final ReferralService referralService;
    private final SecurityUtils securityUtils;
    private final ReactiveBlockingExecutor reactiveBlockingExecutor;

    public ReferralController(ReferralService referralService,
            SecurityUtils securityUtils,
            ReactiveBlockingExecutor reactiveBlockingExecutor) {
        this.referralService = referralService;
        this.securityUtils = securityUtils;
        this.reactiveBlockingExecutor = reactiveBlockingExecutor;
    }

    @GetMapping("/my-code")
    public Mono<ResponseEntity<Map<String, Object>>> getMyCode() {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(user -> reactiveBlockingExecutor.call(
                        () -> referralService.getMyReferralCode(user)))
                .map(ResponseEntity::ok);
    }

    @PostMapping("/generate")
    public Mono<ResponseEntity<Map<String, Object>>> generateCode() {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(user -> reactiveBlockingExecutor.call(
                        () -> referralService.generateMyReferralCode(user)))
                .map(ResponseEntity::ok);
    }

    @PostMapping("/redeem")
    public Mono<ResponseEntity<Map<String, Object>>> redeemCode(@RequestParam String code) {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(user -> reactiveBlockingExecutor.call(
                        () -> referralService.redeemReferralCode(code, user)))
                .map(ResponseEntity::ok);
    }
}
