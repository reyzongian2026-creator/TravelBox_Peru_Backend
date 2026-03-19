package com.tuempresa.storage.payments.infrastructure.out.gateway;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuempresa.storage.shared.domain.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Component
public class CulqiGatewayClient {

    private static final java.util.Set<String> CULQI_REFUND_REASONS = java.util.Set.of(
            "solicitud_comprador",
            "duplicidad",
            "fraudulenta"
    );

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String secretKey;
    private final String publicKey;
    private final String webhookSecret;

    public CulqiGatewayClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${app.payments.culqi.base-url:https://api.culqi.com/v2}") String baseUrl,
            @Value("${app.payments.culqi.secret-key:}") String secretKey,
            @Value("${app.payments.culqi.public-key:}") String publicKey,
            @Value("${app.payments.culqi.webhook-secret:}") String webhookSecret
    ) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
        this.secretKey = safe(secretKey);
        this.publicKey = safe(publicKey);
        this.webhookSecret = safe(webhookSecret);
    }

    public boolean isConfigured() {
        return StringUtils.hasText(secretKey);
    }

    public String publicKey() {
        return publicKey;
    }

    public boolean hasWebhookSecret() {
        return StringUtils.hasText(webhookSecret);
    }

    public boolean validateWebhookSecret(String providedSecret) {
        if (!hasWebhookSecret()) {
            return true;
        }
        return webhookSecret.equals(safe(providedSecret));
    }

    public boolean validateWebhookSignature(String rawPayload, String providedSignature) {
        if (!hasWebhookSecret()) {
            return true;
        }
        if (!StringUtils.hasText(providedSignature)) {
            return false;
        }
        String provided = safe(providedSignature);
        String normalized = provided.startsWith("sha256=") ? provided.substring("sha256=".length()) : provided;
        String expected = hmacSha256Hex(rawPayload, webhookSecret);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                normalized.getBytes(StandardCharsets.UTF_8)
        );
    }

    public CulqiChargeResult createCharge(CulqiChargeRequest request) {
        requireConfigured();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("amount", request.amountInCents());
        payload.put("currency_code", request.currencyCode());
        payload.put("email", request.email());
        payload.put("source_id", request.sourceTokenId());
        payload.put("description", request.description());
        payload.put("metadata", request.metadata());

        ResponseEntity<JsonNode> entity = postJsonWithStatus("/charges", payload);
        JsonNode response = entity.getBody();
        String providerPaymentId = textAt(response, "id");
        String providerStatus = firstNonBlank(
                textAt(response, "outcome.type"),
                textAt(response, "status"),
                textAt(response, "paid")
        );
        String message = firstNonBlank(
                textAt(response, "outcome.user_message"),
                textAt(response, "merchant_message"),
                textAt(response, "user_message"),
                "Operación procesada por Culqi."
        );
        boolean approved = isApprovedStatus(providerStatus, response);
        boolean requires3ds = entity.getStatusCode().value() == 200 && !approved;
        return new CulqiChargeResult(
                providerPaymentId,
                providerStatus,
                message,
                approved,
                requires3ds,
                requires3ds ? buildThreeDsPayload(response, providerPaymentId, providerStatus, message) : null
        );
    }

    public CulqiOrderResult createOrder(CulqiOrderRequest request) {
        requireConfigured();
        requirePublicKeyForCheckout();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("amount", request.amountInCents());
        payload.put("currency_code", request.currencyCode());
        payload.put("description", request.description());
        payload.put("order_number", request.orderNumber());
        payload.put("expiration_date", request.expirationEpochSeconds());

        Map<String, Object> clientDetails = new LinkedHashMap<>();
        clientDetails.put("first_name", request.customerFirstName());
        clientDetails.put("last_name", request.customerLastName());
        clientDetails.put("email", request.customerEmail());
        clientDetails.put("phone_number", request.customerPhone());
        payload.put("client_details", clientDetails);
        payload.put("metadata", request.metadata());

        JsonNode response = postJson("/orders", payload);
        String orderId = firstNonBlank(textAt(response, "id"), textAt(response, "object.id"));
        String providerStatus = firstNonBlank(textAt(response, "state"), textAt(response, "status"), "pending");
        String message = firstNonBlank(
                textAt(response, "payment_code"),
                textAt(response, "response_message"),
                "Orden creada. Completa el pago desde checkout."
        );
        return new CulqiOrderResult(orderId, providerStatus, message, publicKey());
    }

    public CulqiRefundResult createRefund(CulqiRefundRequest request) {
        requireConfigured();
        if (!StringUtils.hasText(request.chargeId())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PAYMENT_REFUND_CHARGE_REQUIRED",
                    "No se puede reembolsar sin chargeId de Culqi."
            );
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("charge_id", request.chargeId());
        payload.put("amount", Math.max(0, request.amountInCents()));
        payload.put("reason", normalizeCulqiRefundReason(request.reason()));

        JsonNode response = postJson("/refunds", payload);
        String refundId = firstNonBlank(textAt(response, "id"), textAt(response, "object.id"));
        String providerStatus = firstNonBlank(textAt(response, "status"), textAt(response, "state"), "refunded");
        String message = firstNonBlank(
                textAt(response, "response_message"),
                textAt(response, "message"),
                "Reembolso procesado por Culqi."
        );
        return new CulqiRefundResult(refundId, providerStatus, message);
    }

    private String normalizeCulqiRefundReason(String requestedReason) {
        String normalized = safe(requestedReason);
        if (!StringUtils.hasText(normalized)) {
            return "solicitud_comprador";
        }
        String candidate = normalized.toLowerCase(Locale.ROOT).replace(" ", "_");
        if (CULQI_REFUND_REASONS.contains(candidate)) {
            return candidate;
        }
        return "solicitud_comprador";
    }

    public long defaultOrderExpirationEpochSeconds() {
        return Instant.now().plus(15, ChronoUnit.MINUTES).getEpochSecond();
    }

    private JsonNode postJson(String uri, Object payload) {
        return postJsonWithStatus(uri, payload).getBody();
    }

    private ResponseEntity<JsonNode> postJsonWithStatus(String uri, Object payload) {
        try {
            return restClient.post()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toEntity(JsonNode.class);
        } catch (RestClientResponseException ex) {
            throw mapProviderError(ex);
        } catch (RestClientException ex) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "PAYMENT_PROVIDER_UNAVAILABLE",
                    "No se pudo conectar con la pasarela de pago."
            );
        }
    }

    private ApiException mapProviderError(RestClientResponseException ex) {
        String responseBody = ex.getResponseBodyAsString();
        String message = "Pasarela de pago rechazó la operación.";
        if (responseBody != null && !responseBody.isBlank()) {
            message = responseBody.length() > 240 ? responseBody.substring(0, 240) : responseBody;
        }

        if (ex.getStatusCode().is4xxClientError()) {
            return new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_PROVIDER_REJECTED", message);
        }
        return new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_PROVIDER_ERROR", message);
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new ApiException(
                    HttpStatus.PRECONDITION_REQUIRED,
                    "PAYMENT_PROVIDER_NOT_CONFIGURED",
                    "Falta configurar APP_CULQI_SECRET_KEY para pagos reales."
            );
        }
    }

    private void requirePublicKeyForCheckout() {
        if (!StringUtils.hasText(publicKey)) {
            throw new ApiException(
                    HttpStatus.PRECONDITION_REQUIRED,
                    "PAYMENT_PUBLIC_KEY_MISSING",
                    "Falta configurar APP_CULQI_PUBLIC_KEY para abrir el checkout real."
            );
        }
    }

    private String textAt(JsonNode node, String dottedPath) {
        if (node == null || dottedPath == null) {
            return null;
        }
        JsonNode cursor = node;
        String[] parts = dottedPath.split("\\.");
        for (String part : parts) {
            if (cursor == null || cursor.isMissingNode() || cursor.isNull()) {
                return null;
            }
            cursor = cursor.path(part);
        }
        if (cursor.isMissingNode() || cursor.isNull()) {
            return null;
        }
        if (cursor.isTextual() || cursor.isNumber() || cursor.isBoolean()) {
            return cursor.asText();
        }
        return null;
    }

    private boolean isApprovedStatus(String providerStatus, JsonNode response) {
        String normalized = providerStatus == null ? "" : providerStatus.toLowerCase(Locale.ROOT);
        if (normalized.equals("venta_exitosa")
                || normalized.equals("successful")
                || normalized.equals("paid")
                || normalized.equals("captured")
                || normalized.equals("approved")) {
            return true;
        }
        String paidValue = textAt(response, "paid");
        return "true".equalsIgnoreCase(paidValue);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private Map<String, Object> buildThreeDsPayload(
            JsonNode response,
            String providerPaymentId,
            String providerStatus,
            String message
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        putIfPresent(payload, "providerPaymentId", providerPaymentId);
        putIfPresent(payload, "providerStatus", providerStatus);
        putIfPresent(payload, "message", message);
        putIfPresent(payload, "actionCode", textAt(response, "action_code"));
        putIfPresent(payload, "authenticationTransactionId", firstNonBlank(
                textAt(response, "authentication_3DS.transaction_id"),
                textAt(response, "authentication_3DS.id")
        ));
        putIfPresent(payload, "authenticationUrl", firstNonBlank(
                textAt(response, "authentication_3DS.redirect_url"),
                textAt(response, "authentication_3DS.url"),
                textAt(response, "authentication_3DS.authentication_url")
        ));

        JsonNode auth3dsNode = response == null ? null : response.path("authentication_3DS");
        if (auth3dsNode != null && !auth3dsNode.isMissingNode() && !auth3dsNode.isNull()) {
            payload.put(
                    "authentication3ds",
                    objectMapper.convertValue(auth3dsNode, new TypeReference<Map<String, Object>>() {
                    })
            );
        }
        return payload;
    }

    private void putIfPresent(Map<String, Object> payload, String key, String value) {
        if (StringUtils.hasText(value)) {
            payload.put(key, value);
        }
    }

    public String sha256Hex(String rawPayload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(rawPayload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception ex) {
            return String.valueOf(rawPayload.hashCode());
        }
    }

    private String hmacSha256Hex(String rawPayload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(rawPayload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception ex) {
            return "";
        }
    }

    public record CulqiChargeRequest(
            long amountInCents,
            String currencyCode,
            String email,
            String sourceTokenId,
            String description,
            Map<String, String> metadata
    ) {
    }

    public record CulqiOrderRequest(
            long amountInCents,
            String currencyCode,
            String description,
            String orderNumber,
            long expirationEpochSeconds,
            String customerFirstName,
            String customerLastName,
            String customerEmail,
            String customerPhone,
            Map<String, String> metadata
    ) {
    }

    public record CulqiChargeResult(
            String providerPaymentId,
            String providerStatus,
            String message,
            boolean approved,
            boolean requires3ds,
            Map<String, Object> actionData
    ) {
    }

    public record CulqiOrderResult(
            String orderId,
            String providerStatus,
            String message,
            String publicKey
    ) {
    }

    public record CulqiRefundRequest(
            String chargeId,
            long amountInCents,
            String reason
    ) {
    }

    public record CulqiRefundResult(
            String refundId,
            String providerStatus,
            String message
    ) {
    }
}