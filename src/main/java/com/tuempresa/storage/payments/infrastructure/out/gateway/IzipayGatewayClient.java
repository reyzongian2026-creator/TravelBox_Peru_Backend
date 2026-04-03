package com.tuempresa.storage.payments.infrastructure.out.gateway;

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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Component
public class IzipayGatewayClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String merchantCode;
    private final String publicKey;
    private final String hashKey;
    private final String apiUser;
    private final String apiPassword;
    private final String keyRsa;
    private final String requestSource;
    private final String processType;
    private final String checkoutScriptUrl;

    public IzipayGatewayClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${app.payments.izipay.api-base-url:https://api.micuentaweb.pe}") String apiBaseUrl,
            @Value("${app.payments.izipay.merchant-code:}") String merchantCode,
            @Value("${app.payments.izipay.public-key:}") String publicKey,
            @Value("${app.payments.izipay.hash-key:}") String hashKey,
            @Value("${app.payments.izipay.api-user:}") String apiUser,
            @Value("${app.payments.izipay.api-password:}") String apiPassword,
            @Value("${app.payments.izipay.key-rsa:RSA}") String keyRsa,
            @Value("${app.payments.izipay.request-source:ECOMMERCE}") String requestSource,
            @Value("${app.payments.izipay.process-type:AT}") String processType,
            @Value("${app.payments.izipay.checkout-script-url:https://static.micuentaweb.pe/static/js/krypton-client/V4.0/stable/kr-payment-form.min.js}") String checkoutScriptUrl
    ) {
        this.restClient = restClientBuilder.clone().baseUrl(safe(apiBaseUrl)).build();
        this.objectMapper = objectMapper;
        this.merchantCode = safe(merchantCode);
        this.publicKey = safe(publicKey);
        this.hashKey = safe(hashKey);
        this.apiUser = safe(apiUser);
        this.apiPassword = safe(apiPassword);
        this.keyRsa = safe(keyRsa);
        this.requestSource = firstNonBlank(safe(requestSource), "ECOMMERCE");
        this.processType = firstNonBlank(safe(processType).toUpperCase(Locale.ROOT), "AT");
        this.checkoutScriptUrl = safe(checkoutScriptUrl);
    }

    public boolean isConfigured() {
        return StringUtils.hasText(apiUser) && StringUtils.hasText(apiPassword)
                && StringUtils.hasText(merchantCode) && StringUtils.hasText(publicKey);
    }

    public boolean hasHashKey() {
        return StringUtils.hasText(hashKey);
    }

    public String merchantCode() {
        return merchantCode;
    }

    public String publicKey() {
        return publicKey;
    }

    public String keyRsa() {
        return firstNonBlank(keyRsa, "RSA");
    }

    public String requestSource() {
        return requestSource;
    }

    public String processType() {
        return processType;
    }

    public String checkoutScriptUrl() {
        return checkoutScriptUrl;
    }

    public IzipaySessionResult createSession(IzipaySessionRequest request) {
        requireConfigured();

        // Lyra V4 API: amount must be in cents (integer)
        long amountInCents = new BigDecimal(request.amount())
                .multiply(new BigDecimal("100"))
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("amount", amountInCents);
        payload.put("currency", "PEN");
        payload.put("orderId", request.orderNumber());

        JsonNode response = postJson(
                "/api-payment/V4/Charge/CreatePayment",
                payload,
                request.transactionId()
        );

        String formToken = textAt(response, "answer.formToken");
        if (!StringUtils.hasText(formToken)) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "PAYMENT_PROVIDER_ERROR",
                    "Izipay V4 no devolvio formToken. Response: " +
                            firstNonBlank(textAt(response, "answer.errorMessage"),
                                    textAt(response, "status"), "sin detalle")
            );
        }

        return new IzipaySessionResult(
                formToken,
                request.transactionId(),
                request.orderNumber(),
                merchantCode,
                publicKey,
                keyRsa(),
                checkoutScriptUrl,
                response
        );
    }

    public JsonNode refund(String transactionId, String amount, String reason) {
        requireConfigured();
        long amountInCents = new BigDecimal(amount)
                .multiply(new BigDecimal("100"))
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("uuid", transactionId);
        payload.put("amount", amountInCents);
        payload.put("currency", "PEN");
        payload.put("comment", firstNonBlank(reason, "Reembolso"));

        return postJson("/api-payment/V4/Transaction/CancelOrRefund", payload, transactionId + "-refund");
    }

    public JsonNode voidTransaction(String transactionId) {
        requireConfigured();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("uuid", transactionId);

        return postJson("/api-payment/V4/Transaction/Cancel", payload, transactionId + "-void");
    }

    public JsonNode checkOrderStatus(String orderNumber) {
        requireConfigured();
        // En V4 se consulta por orderId o uuid
        return postJson("/api-payment/V4/Transaction/Get", Map.of("orderId", orderNumber), orderNumber + "-status");
    }

    public JsonNode payWithToken(Map<String, Object> request) {
        requireConfigured();
        // Convertir amount de string decimal ("150.00") a entero en centavos (15000) que exige Izipay V4
        Map<String, Object> payload = new LinkedHashMap<>(request);
        Object rawAmount = payload.get("amount");
        if (rawAmount != null) {
            long amountInCents = new BigDecimal(rawAmount.toString())
                    .multiply(new BigDecimal("100"))
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValue();
            payload.put("amount", amountInCents);
        }
        return postJson("/api-payment/V4/Charge/CreatePayment", payload, String.valueOf(request.get("transactionId")) + "-token");
    }

    public String generateWebhookSignature(String payloadHttp) {
        if (!StringUtils.hasText(hashKey) || !StringUtils.hasText(payloadHttp)) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hashKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payloadHttp.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception ex) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "PAYMENT_SIGNATURE_ERROR",
                    "No se pudo generar la firma para Izipay."
            );
        }
    }

    public boolean validateWebhookSignature(String payloadHttp, String providedSignature) {
        if (!hasHashKey()) {
            return true;
        }
        if (!StringUtils.hasText(payloadHttp) || !StringUtils.hasText(providedSignature)) {
            return false;
        }
        String expected = generateWebhookSignature(payloadHttp);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                safe(providedSignature).getBytes(StandardCharsets.UTF_8)
        );
    }

    public String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(safe(value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "PAYMENT_HASH_ERROR", "No se pudo calcular hash.");
        }
    }

    private JsonNode postJson(String uri, Object payload, String transactionId) {
        try {
            String auth = apiUser + ":" + apiPassword;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));

            ResponseEntity<JsonNode> entity = restClient.post()
                    .uri(uri)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + encodedAuth)
                    .body(payload)
                    .retrieve()
                    .toEntity(JsonNode.class);
            return entity.getBody();
        } catch (RestClientResponseException ex) {
            throw mapProviderError(ex);
        } catch (RestClientException ex) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "PAYMENT_PROVIDER_UNAVAILABLE",
                    "No se pudo conectar con Izipay V4."
            );
        }
    }

    private ApiException mapProviderError(RestClientResponseException ex) {
        String message = "Izipay V4 rechazo la operacion.";
        String responseBody = ex.getResponseBodyAsString();
        if (StringUtils.hasText(responseBody)) {
            try {
                JsonNode json = objectMapper.readTree(responseBody);
                message = firstNonBlank(
                        textAt(json, "answer.errorMessage"),
                        textAt(json, "message"),
                        responseBody
                );
            } catch (Exception ignored) {
                message = responseBody.length() > 240 ? responseBody.substring(0, 240) : responseBody;
            }
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
                    "Falta configurar API User o Password de Micuentaweb V4."
            );
        }
    }

    private String textAt(JsonNode node, String dottedPath) {
        if (node == null || !StringUtils.hasText(dottedPath)) {
            return null;
        }
        JsonNode current = node;
        for (String part : dottedPath.split("\\.")) {
            if (current == null || current.isMissingNode() || current.isNull()) {
                return null;
            }
            if (current.isArray()) {
                try {
                    current = current.path(Integer.parseInt(part));
                    continue;
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
            current = current.path(part);
        }
        if (current == null || current.isMissingNode() || current.isNull()) {
            return null;
        }
        if (current.isTextual() || current.isNumber() || current.isBoolean()) {
            return current.asText();
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public record IzipaySessionRequest(
            String transactionId,
            String orderNumber,
            String amount,
            String requestSource
    ) {
    }

    public record IzipaySessionResult(
            String token,
            String transactionId,
            String orderNumber,
            String merchantCode,
            String publicKey,
            String keyRsa,
            String checkoutScriptUrl,
            JsonNode rawResponse
    ) {
    }
}
