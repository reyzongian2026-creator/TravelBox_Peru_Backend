package com.tuempresa.storage.payments.infrastructure.in.web;

import com.tuempresa.storage.payments.application.dto.CashDecisionRequest;
import com.tuempresa.storage.payments.application.dto.ConfirmPaymentRequest;
import com.tuempresa.storage.payments.application.dto.CreatePaymentIntentRequest;
import com.tuempresa.storage.payments.application.dto.CashPendingPaymentResponse;
import com.tuempresa.storage.payments.application.dto.PaymentHistoryItemResponse;
import com.tuempresa.storage.payments.application.dto.PaymentIntentResponse;
import com.tuempresa.storage.payments.application.dto.RefundPaymentRequest;
import com.tuempresa.storage.payments.application.dto.PaymentStatusResponse;
import com.tuempresa.storage.payments.application.dto.PaymentWebhookResponse;
import com.tuempresa.storage.payments.application.usecase.PaymentService;
import com.tuempresa.storage.shared.infrastructure.reactive.ReactiveBlockingExecutor;
import com.tuempresa.storage.shared.infrastructure.security.SecurityUtils;
import com.tuempresa.storage.shared.infrastructure.web.PagedResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final SecurityUtils securityUtils;
    private final ReactiveBlockingExecutor reactiveBlockingExecutor;

    public PaymentController(
            PaymentService paymentService,
            SecurityUtils securityUtils,
            ReactiveBlockingExecutor reactiveBlockingExecutor
    ) {
        this.paymentService = paymentService;
        this.securityUtils = securityUtils;
        this.reactiveBlockingExecutor = reactiveBlockingExecutor;
    }

    @PostMapping({"/intents", "/intent"})
    public Mono<ResponseEntity<PaymentIntentResponse>> createIntent(@Valid @RequestBody CreatePaymentIntentRequest request) {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                        () -> paymentService.createIntent(request, currentUser)
                ))
                .map(ResponseEntity::ok);
    }

    @PostMapping({"/confirm", "/checkout", "/process"})
    public Mono<ResponseEntity<PaymentIntentResponse>> confirm(@Valid @RequestBody ConfirmPaymentRequest request) {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                        () -> paymentService.confirm(request, currentUser)
                ))
                .map(ResponseEntity::ok);
    }

    @GetMapping("/status")
    public Mono<ResponseEntity<PaymentStatusResponse>> status(
            @RequestParam(required = false) Long paymentIntentId,
            @RequestParam(required = false) Long reservationId
    ) {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                        () -> paymentService.status(paymentIntentId, reservationId, currentUser)
                ))
                .map(ResponseEntity::ok);
    }

    @GetMapping("/history")
    public Mono<ResponseEntity<PagedResponse<PaymentHistoryItemResponse>>> history(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                        () -> paymentService.history(currentUser, page, size)
                ))
                .map(ResponseEntity::ok);
    }

    @GetMapping("/cash/pending")
    public Mono<ResponseEntity<PagedResponse<CashPendingPaymentResponse>>> cashPending(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                        () -> paymentService.listCashPending(currentUser, page, size)
                ))
                .map(ResponseEntity::ok);
    }

    @PostMapping("/cash/{paymentIntentId}/approve")
    public Mono<ResponseEntity<PaymentIntentResponse>> approveCashPayment(
            @PathVariable Long paymentIntentId,
            @Valid @RequestBody(required = false) CashDecisionRequest request
    ) {
        String reference = request != null ? request.providerReference() : null;
        String reason = request != null ? request.reason() : null;
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                        () -> paymentService.approveCashPayment(paymentIntentId, reference, reason, currentUser)
                ))
                .map(ResponseEntity::ok);
    }

    @PostMapping("/cash/{paymentIntentId}/reject")
    public Mono<ResponseEntity<PaymentIntentResponse>> rejectCashPayment(
            @PathVariable Long paymentIntentId,
            @Valid @RequestBody(required = false) CashDecisionRequest request
    ) {
        String reference = request != null ? request.providerReference() : null;
        String reason = request != null ? request.reason() : null;
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                        () -> paymentService.rejectCashPayment(paymentIntentId, reference, reason, currentUser)
                ))
                .map(ResponseEntity::ok);
    }

    @PostMapping("/{paymentIntentId}/refund")
    public Mono<ResponseEntity<PaymentIntentResponse>> refundPayment(
            @PathVariable Long paymentIntentId,
            @Valid @RequestBody(required = false) RefundPaymentRequest request
    ) {
        String reason = request != null ? request.reason() : null;
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                        () -> paymentService.refund(paymentIntentId, reason, currentUser)
                ))
                .map(ResponseEntity::ok);
    }

    @PostMapping("/webhooks/culqi")
    public Mono<ResponseEntity<PaymentWebhookResponse>> culqiWebhook(
            @RequestBody(required = false) String payload,
            @RequestHeader(name = "X-Culqi-Webhook-Secret", required = false) String webhookSecret,
            @RequestHeader(name = "X-Culqi-Signature", required = false) String signature
    ) {
        String secret = StringUtils.hasText(webhookSecret) ? webhookSecret : signature;
        return reactiveBlockingExecutor.call(() -> paymentService.processCulqiWebhook(payload, secret))
                .map(ResponseEntity::ok);
    }
}
