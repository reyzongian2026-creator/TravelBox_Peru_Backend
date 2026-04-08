package com.tuempresa.storage.payments.infrastructure.in.web;

import com.tuempresa.storage.payments.application.dto.CancellationPreviewResponse;
import com.tuempresa.storage.payments.application.dto.CashDecisionRequest;
import com.tuempresa.storage.payments.application.dto.ConfirmPaymentRequest;
import com.tuempresa.storage.payments.application.dto.CreatePaymentIntentRequest;
import com.tuempresa.storage.payments.application.dto.CashPendingPaymentResponse;
import com.tuempresa.storage.payments.application.dto.PaymentHistoryItemResponse;
import com.tuempresa.storage.payments.application.dto.PaymentIntentResponse;
import com.tuempresa.storage.payments.application.dto.PromoCodeResponse;
import com.tuempresa.storage.payments.application.dto.RefundPaymentRequest;
import com.tuempresa.storage.payments.application.dto.PaymentStatusResponse;
import com.tuempresa.storage.payments.application.dto.PaymentWebhookResponse;
import com.tuempresa.storage.payments.application.dto.SavedCardResponse;
import com.tuempresa.storage.payments.application.dto.ValidateCheckoutResultRequest;
import com.tuempresa.storage.payments.application.usecase.PaymentService;
import com.tuempresa.storage.payments.application.usecase.YapeEmailReconciliationService;
import com.tuempresa.storage.users.infrastructure.out.persistence.UserRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.tuempresa.storage.payments.domain.PaymentStatus;
import com.tuempresa.storage.shared.infrastructure.reactive.ReactiveBlockingExecutor;
import com.tuempresa.storage.shared.infrastructure.security.SecurityUtils;
import com.tuempresa.storage.shared.infrastructure.web.PagedResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
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
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

        private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

        private final PaymentService paymentService;
        private final SecurityUtils securityUtils;
        private final ReactiveBlockingExecutor reactiveBlockingExecutor;
        private final UserRepository userRepository;
        private final YapeEmailReconciliationService yapeReconciliationService;
        private final ObjectMapper objectMapper;

        public PaymentController(
                        PaymentService paymentService,
                        SecurityUtils securityUtils,
                        ReactiveBlockingExecutor reactiveBlockingExecutor,
                        UserRepository userRepository,
                        YapeEmailReconciliationService yapeReconciliationService,
                        ObjectMapper objectMapper) {
                this.paymentService = paymentService;
                this.securityUtils = securityUtils;
                this.reactiveBlockingExecutor = reactiveBlockingExecutor;
                this.userRepository = userRepository;
                this.yapeReconciliationService = yapeReconciliationService;
                this.objectMapper = objectMapper;
        }

        @PostMapping({ "/intents", "/intent" })
        public Mono<ResponseEntity<PaymentIntentResponse>> createIntent(
                        @Valid @RequestBody CreatePaymentIntentRequest request) {
                return securityUtils.currentUserOrThrowReactive()
                                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                                                () -> paymentService.createIntent(request, currentUser)))
                                .map(ResponseEntity::ok)
                                .doOnError(ex -> log.error("createIntent failed for reservationId={}: {}",
                                                request.reservationId(), ex.getMessage(), ex));
        }

        @PostMapping({ "/confirm", "/checkout", "/process" })
        public Mono<ResponseEntity<PaymentIntentResponse>> confirm(@Valid @RequestBody ConfirmPaymentRequest request) {
                return securityUtils.currentUserOrThrowReactive()
                                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                                                () -> paymentService.confirm(request, currentUser)))
                                .map(ResponseEntity::ok)
                                .doOnError(ex -> log.error("confirm failed: {}", ex.getMessage(), ex));
        }

        @GetMapping("/validate-promo")
        public Mono<ResponseEntity<PromoCodeResponse>> validatePromoCode(
                        @RequestParam String code,
                        @RequestParam BigDecimal amount) {
                return securityUtils.currentUserOrThrowReactive()
                                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                                                () -> paymentService.validatePromoCode(code, amount)))
                                .map(ResponseEntity::ok);
        }

        @GetMapping("/wallet-balance")
        public Mono<ResponseEntity<Map<String, Object>>> getWalletBalance() {
                return securityUtils.currentUserOrThrowReactive()
                                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                                                () -> {
                                                        Map<String, Object> result = userRepository
                                                                        .findById(currentUser.getId())
                                                                        .map(u -> Map.<String, Object>of(
                                                                                        "walletBalance",
                                                                                        (Object) u.getWalletBalance()))
                                                                        .orElse(Map.of("walletBalance",
                                                                                        (Object) BigDecimal.ZERO));
                                                        return result;
                                                }))
                                .map(ResponseEntity::ok);
        }

        @GetMapping("/status")
        public Mono<ResponseEntity<PaymentStatusResponse>> status(
                        @RequestParam(required = false) Long paymentIntentId,
                        @RequestParam(required = false) Long reservationId) {
                return securityUtils.currentUserOrThrowReactive()
                                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                                                () -> paymentService.status(paymentIntentId, reservationId,
                                                                currentUser)))
                                .map(ResponseEntity::ok)
                                .doOnError(ex -> log.error("status failed for intentId={} reservationId={}: {}",
                                                paymentIntentId, reservationId, ex.getMessage(), ex));
        }

        @PostMapping("/{paymentIntentId}/sync")
        public Mono<ResponseEntity<PaymentIntentResponse>> syncStatus(@PathVariable Long paymentIntentId) {
                return securityUtils.currentUserOrThrowReactive()
                                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                                                () -> paymentService.syncStatus(paymentIntentId, currentUser)))
                                .map(ResponseEntity::ok);
        }

        @GetMapping("/cards")
        public Mono<ResponseEntity<List<SavedCardResponse>>> listSavedCards() {
                return securityUtils.currentUserOrThrowReactive()
                                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                                                () -> paymentService.listSavedCards(currentUser)))
                                .map(ResponseEntity::ok);
        }

        @PostMapping("/one-click")
        public Mono<ResponseEntity<PaymentIntentResponse>> payWithSavedCard(
                        @RequestParam Long reservationId,
                        @RequestParam Long savedCardId) {
                return securityUtils.currentUserOrThrowReactive()
                                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                                                () -> paymentService.payWithSavedCard(reservationId, savedCardId,
                                                                currentUser)))
                                .map(ResponseEntity::ok);
        }

        @GetMapping("/history")
        public Mono<ResponseEntity<PagedResponse<PaymentHistoryItemResponse>>> history(
                        @RequestParam(defaultValue = "0") @Min(0) int page,
                        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
                        @RequestParam(required = false) PaymentStatus status) {
                return securityUtils.currentUserOrThrowReactive()
                                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                                                () -> paymentService.history(currentUser, page, size, status)))
                                .map(ResponseEntity::ok)
                                .doOnError(ex -> log.error("history failed page={} size={}: {}",
                                                page, size, ex.getMessage(), ex));
        }

        @GetMapping("/cash/pending")
        public Mono<ResponseEntity<PagedResponse<CashPendingPaymentResponse>>> cashPending(
                        @RequestParam(defaultValue = "0") @Min(0) int page,
                        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
                return securityUtils.currentUserOrThrowReactive()
                                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                                                () -> paymentService.listCashPending(currentUser, page, size)))
                                .map(ResponseEntity::ok);
        }

        @PostMapping("/cash/{paymentIntentId}/approve")
        public Mono<ResponseEntity<PaymentIntentResponse>> approveCashPayment(
                        @PathVariable Long paymentIntentId,
                        @Valid @RequestBody(required = false) CashDecisionRequest request) {
                String reference = request != null ? request.providerReference() : null;
                String reason = request != null ? request.reason() : null;
                return securityUtils.currentUserOrThrowReactive()
                                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                                                () -> paymentService.approveCashPayment(paymentIntentId, reference,
                                                                reason, currentUser)))
                                .map(ResponseEntity::ok);
        }

        @PostMapping("/cash/{paymentIntentId}/reject")
        public Mono<ResponseEntity<PaymentIntentResponse>> rejectCashPayment(
                        @PathVariable Long paymentIntentId,
                        @Valid @RequestBody(required = false) CashDecisionRequest request) {
                String reference = request != null ? request.providerReference() : null;
                String reason = request != null ? request.reason() : null;
                return securityUtils.currentUserOrThrowReactive()
                                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                                                () -> paymentService.rejectCashPayment(paymentIntentId, reference,
                                                                reason, currentUser)))
                                .map(ResponseEntity::ok);
        }

        @PostMapping("/{paymentIntentId}/refund")
        public Mono<ResponseEntity<PaymentIntentResponse>> refundPayment(
                        @PathVariable Long paymentIntentId,
                        @Valid @RequestBody(required = false) RefundPaymentRequest request) {
                String reason = request != null ? request.reason() : null;
                return securityUtils.currentUserOrThrowReactive()
                                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                                                () -> paymentService.refund(paymentIntentId, reason, currentUser)))
                                .map(ResponseEntity::ok);
        }

        @GetMapping("/cancellation-preview/{reservationId}")
        public Mono<ResponseEntity<CancellationPreviewResponse>> cancellationPreview(
                        @PathVariable Long reservationId) {
                return securityUtils.currentUserOrThrowReactive()
                                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                                                () -> paymentService.cancellationPreview(reservationId, currentUser)))
                                .map(ResponseEntity::ok);
        }

        @PostMapping("/cancellation-confirm/{reservationId}")
        public Mono<ResponseEntity<PaymentIntentResponse>> cancellationConfirm(
                        @PathVariable Long reservationId,
                        @Valid @RequestBody(required = false) RefundPaymentRequest request) {
                String reason = request != null ? request.reason() : null;
                return securityUtils.currentUserOrThrowReactive()
                                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                                                () -> paymentService.cancellationConfirm(reservationId, reason,
                                                                currentUser)))
                                .map(resp -> resp != null ? ResponseEntity.ok(resp)
                                                : ResponseEntity.noContent().build());
        }

        /**
         * Izipay IPN webhook. Izipay sends IPN as application/x-www-form-urlencoded
         * with fields: kr-answer (JSON), kr-hash (HMAC-SHA256 hex).
         * Also supports JSON body for manual retrigger / testing.
         */
        @PostMapping(value = "/webhooks/izipay", consumes = MediaType.ALL_VALUE)
        public Mono<ResponseEntity<PaymentWebhookResponse>> izipayWebhook(
                        ServerWebExchange exchange) {
                MediaType contentType = exchange.getRequest().getHeaders().getContentType();
                boolean isFormData = contentType != null
                                && contentType.isCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED);

                if (isFormData) {
                        return exchange.getFormData()
                                        .flatMap(form -> {
                                                String krAnswer = form.getFirst("kr-answer");
                                                String krHash = form.getFirst("kr-hash");
                                                if (!StringUtils.hasText(krHash)) {
                                                        return Mono.just(new PaymentWebhookResponse(false, false,
                                                                        null, null, null, null,
                                                                        "kr-hash is required", null,
                                                                        null));
                                                }
                                                return reactiveBlockingExecutor.call(
                                                                () -> paymentService.processIzipayWebhook(
                                                                                krAnswer != null ? krAnswer : "",
                                                                                krHash));
                                        })
                                        .map(resp -> {
                                                if ("kr-hash is required".equals(resp.message())) {
                                                        return ResponseEntity.badRequest().body(resp);
                                                }
                                                return ResponseEntity.ok(resp);
                                        });
                }

                // JSON or other content type: read raw body
                return DataBufferUtils.join(exchange.getRequest().getBody())
                                .map(buf -> {
                                        String body = buf.toString(StandardCharsets.UTF_8);
                                        DataBufferUtils.release(buf);
                                        return body;
                                })
                                .defaultIfEmpty("")
                                .flatMap(payload -> {
                                        String sig = exchange.getRequest().getHeaders()
                                                        .getFirst("X-Izipay-Signature");
                                        return reactiveBlockingExecutor.call(
                                                        () -> paymentService.processIzipayWebhook(
                                                                        payload,
                                                                        StringUtils.hasText(sig) ? sig : null));
                                })
                                .map(ResponseEntity::ok);
        }

        @PostMapping({ "/validate-checkout", "/validate" })
        public Mono<ResponseEntity<PaymentIntentResponse>> validateCheckoutResult(
                        @Valid @RequestBody ValidateCheckoutResultRequest request) {
                return securityUtils.currentUserOrThrowReactive()
                                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                                                () -> paymentService.validateCheckoutResult(request, currentUser)))
                                .map(ResponseEntity::ok);
        }

        /**
         * Graph API webhook for mail change notifications.
         * - Validation: Graph sends GET with ?validationToken=... → must echo it back.
         * - Notification: Graph sends POST with JSON body containing new message IDs.
         */
        @PostMapping(value = "/webhooks/graph-mail", consumes = MediaType.ALL_VALUE)
        public Mono<ResponseEntity<String>> graphMailWebhook(
                        ServerWebExchange exchange,
                        @RequestParam(name = "validationToken", required = false) String validationToken) {

                // Subscription validation handshake — echo the token
                if (validationToken != null && !validationToken.isBlank()) {
                        return Mono.just(ResponseEntity.ok()
                                        .contentType(MediaType.TEXT_PLAIN)
                                        .body(validationToken));
                }

                // Process notification payload
                return DataBufferUtils.join(exchange.getRequest().getBody())
                                .map(buf -> {
                                        String body = buf.toString(StandardCharsets.UTF_8);
                                        DataBufferUtils.release(buf);
                                        return body;
                                })
                                .defaultIfEmpty("")
                                .flatMap(payload -> reactiveBlockingExecutor.<String>call(() -> {
                                        handleGraphMailNotification(payload);
                                        return "";
                                }))
                                .map(r -> ResponseEntity.accepted().<String>build())
                                .onErrorReturn(ResponseEntity.accepted().build());
        }

        private void handleGraphMailNotification(String payload) {
                try {
                        JsonNode root = objectMapper.readTree(payload);
                        JsonNode notifications = root.path("value");
                        if (!notifications.isArray())
                                return;

                        String expectedState = yapeReconciliationService.getWebhookClientState();
                        for (JsonNode notif : notifications) {
                                // Validate clientState to ensure it's from our subscription
                                String clientState = notif.path("clientState").asText("");
                                if (!expectedState.equals(clientState))
                                        continue;

                                String resourceData = notif.path("resource").asText("");
                                // resource = "users/{mailbox}/messages/{messageId}"
                                int lastSlash = resourceData.lastIndexOf('/');
                                if (lastSlash < 0)
                                        continue;
                                String messageId = resourceData.substring(lastSlash + 1);
                                if (messageId.isBlank())
                                        continue;

                                yapeReconciliationService.processMessageById(messageId);
                        }
                } catch (Exception ex) {
                        log.error("Failed to process Graph Mail notification. Payload length={}",
                                        payload != null ? payload.length() : 0, ex);
                }
        }
}
