package com.tuempresa.storage.payments.application.usecase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuempresa.storage.notifications.application.usecase.NotificationService;
import com.tuempresa.storage.payments.domain.PaymentAttempt;
import com.tuempresa.storage.payments.domain.PaymentStatus;
import com.tuempresa.storage.payments.infrastructure.out.persistence.PaymentAttemptRepository;
import com.tuempresa.storage.reservations.application.usecase.ReservationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class YapeEmailReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(YapeEmailReconciliationService.class);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final String GRAPH_TOKEN_URL = "https://login.microsoftonline.com/%s/oauth2/v2.0/token";
    private static final String GRAPH_MESSAGES_URL = "https://graph.microsoft.com/v1.0/users/%s/messages";
    private static final String GRAPH_MESSAGE_URL = "https://graph.microsoft.com/v1.0/users/%s/messages/%s";
    private static final String GRAPH_SUBSCRIPTIONS_URL = "https://graph.microsoft.com/v1.0/subscriptions";

    // Max subscription lifetime for mail = 4230 minutes. Renew with margin.
    private static final long SUBSCRIPTION_LIFETIME_MINUTES = 4200;
    private static final long SUBSCRIPTION_RENEW_MARGIN_MINUTES = 120;

    // BCP Yape notification patterns
    private static final Pattern AMOUNT_INLINE = Pattern.compile(
            "yapeo de S/\\s*([\\d,]+\\.\\d{2})", Pattern.CASE_INSENSITIVE);
    private static final Pattern AMOUNT_TABLE = Pattern.compile(
            "Monto recibido.*?<b>\\s*S/\\s*([\\d,]+\\.\\d{2})\\s*</b>", Pattern.DOTALL);
    private static final Pattern SENDER_NAME = Pattern.compile(
            "Enviado por.*?<b>(.*?)</b>", Pattern.DOTALL);
    private static final Pattern TX_DATETIME = Pattern.compile(
            "Fecha y hora.*?<b>(.*?)</b>", Pattern.DOTALL);

    private final PaymentAttemptRepository paymentAttemptRepository;
    private final ReservationService reservationService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate txTemplate;
    private final String graphTenantId;
    private final String graphClientId;
    private final String graphClientSecret;
    private final String mailboxUser;
    private final boolean enabled;
    private final String backendBaseUrl;
    private final String webhookClientState;

    private volatile String cachedToken;
    private volatile long cachedTokenExpiry;
    private volatile String subscriptionId;
    private volatile Instant subscriptionExpiry;

    public YapeEmailReconciliationService(
            PaymentAttemptRepository paymentAttemptRepository,
            ReservationService reservationService,
            NotificationService notificationService,
            ObjectMapper objectMapper,
            PlatformTransactionManager txManager,
            @Value("${app.email.graph-tenant-id:}") String graphTenantId,
            @Value("${app.email.graph-client-id:}") String graphClientId,
            @Value("${app.email.graph-client-secret:}") String graphClientSecret,
            @Value("${app.email.from-address:admin@inkavoy.pe}") String mailboxUser,
            @Value("${app.payments.yape-reconciliation.enabled:false}") boolean enabled,
            @Value("${app.payments.yape-reconciliation.backend-base-url:}") String backendBaseUrl) {
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.reservationService = reservationService;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
        this.txTemplate = new TransactionTemplate(txManager);
        this.graphTenantId = norm(graphTenantId);
        this.graphClientId = norm(graphClientId);
        this.graphClientSecret = norm(graphClientSecret);
        this.mailboxUser = norm(mailboxUser);
        this.enabled = enabled;
        this.backendBaseUrl = norm(backendBaseUrl);
        this.webhookClientState = UUID.randomUUID().toString();
    }

    // ──────────────────────────────────────────────────────────────────────
    // Webhook subscription — registers on startup, renews before expiry
    // ──────────────────────────────────────────────────────────────────────

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!enabled || backendBaseUrl == null) {
            log.info("Yape reconciliation: webhook disabled (enabled={}, backendBaseUrl={})",
                    enabled, backendBaseUrl);
            return;
        }
        try {
            createOrRenewSubscription();
        } catch (Exception ex) {
            log.warn("Yape reconciliation: webhook subscription failed on startup: {}", ex.getMessage());
        }
    }

    /** Called every 60 min to renew the subscription before it expires. */
    @Scheduled(fixedDelay = 3600_000, initialDelay = 3600_000)
    public void renewSubscriptionIfNeeded() {
        if (!enabled || backendBaseUrl == null)
            return;
        if (subscriptionExpiry != null
                && Instant.now()
                        .isBefore(subscriptionExpiry.minus(SUBSCRIPTION_RENEW_MARGIN_MINUTES, ChronoUnit.MINUTES))) {
            return; // still valid
        }
        try {
            createOrRenewSubscription();
        } catch (Exception ex) {
            log.error("Yape reconciliation: subscription renewal failed: {}", ex.getMessage());
        }
    }

    private void createOrRenewSubscription() {
        String token = getAccessToken();
        if (token == null)
            return;

        Instant expiry = Instant.now().plus(SUBSCRIPTION_LIFETIME_MINUTES, ChronoUnit.MINUTES);
        String expiryStr = DateTimeFormatter.ISO_INSTANT.format(expiry);
        String notificationUrl = backendBaseUrl.replaceAll("/+$", "")
                + "/api/v1/payments/webhooks/graph-mail";

        // Try renew first if we have an existing subscription
        if (subscriptionId != null) {
            try {
                String patchBody = String.format("{\"expirationDateTime\":\"%s\"}", expiryStr);
                HttpRequest req = HttpRequest.newBuilder(
                        URI.create(GRAPH_SUBSCRIPTIONS_URL + "/" + subscriptionId))
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(20))
                        .method("PATCH", HttpRequest.BodyPublishers.ofString(patchBody, StandardCharsets.UTF_8))
                        .build();
                HttpResponse<String> resp = HTTP_CLIENT.send(req,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                    subscriptionExpiry = expiry;
                    log.info("Yape reconciliation: webhook subscription renewed until {}", expiryStr);
                    return;
                }
                log.warn("Yape reconciliation: renew failed {}, will create new", resp.statusCode());
            } catch (Exception ex) {
                log.warn("Yape reconciliation: renew error: {}", ex.getMessage());
            }
        }

        // Create new subscription
        try {
            String resource = "users/" + mailboxUser + "/messages";
            String createBody = objectMapper.writeValueAsString(Map.of(
                    "changeType", "created",
                    "notificationUrl", notificationUrl,
                    "resource", resource,
                    "expirationDateTime", expiryStr,
                    "clientState", webhookClientState));

            HttpRequest req = HttpRequest.newBuilder(URI.create(GRAPH_SUBSCRIPTIONS_URL))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(createBody, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = HTTP_CLIENT.send(req,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                JsonNode node = objectMapper.readTree(resp.body());
                subscriptionId = node.path("id").asText(null);
                subscriptionExpiry = expiry;
                log.info("Yape reconciliation: webhook subscription created — id={}, expires={}",
                        subscriptionId, expiryStr);
            } else {
                log.error("Yape reconciliation: create subscription failed {}: {}",
                        resp.statusCode(), trunc(resp.body()));
            }
        } catch (Exception ex) {
            log.error("Yape reconciliation: create subscription error: {}", ex.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Webhook processing — called by controller on Graph notification
    // ──────────────────────────────────────────────────────────────────────

    /** Returns the clientState random secret for webhook validation. */
    public String getWebhookClientState() {
        return webhookClientState;
    }

    /** Process a single message by its ID (from webhook notification). */
    public void processMessageById(String messageId) {
        if (!enabled || messageId == null || messageId.isBlank())
            return;
        try {
            String token = getAccessToken();
            if (token == null)
                return;

            String url = String.format(GRAPH_MESSAGE_URL, enc(mailboxUser), messageId)
                    + "?$select=id,subject,from,receivedDateTime,body,isRead";
            HttpResponse<String> resp = graphGet(token, url);
            if (resp.statusCode() != 200) {
                log.warn("Yape webhook: fetch message failed {}: {}", resp.statusCode(), trunc(resp.body()));
                return;
            }

            JsonNode msg = objectMapper.readTree(resp.body());
            String subject = msg.path("subject").asText("");
            if (!subject.toLowerCase().contains("yapeo")) {
                return; // not a Yape notification
            }

            String emailId = msg.path("id").asText();
            String bodyHtml = msg.path("body").path("content").asText("");
            String receivedAt = msg.path("receivedDateTime").asText("");
            try {
                reconcileOneEmail(emailId, subject, bodyHtml, receivedAt);
            } catch (Exception ex) {
                log.error("Yape webhook: error processing email '{}': {}", trunc(subject), ex.getMessage());
            } finally {
                markAsRead(token, emailId);
            }
        } catch (Exception ex) {
            log.error("Yape webhook: processMessageById error: {}", ex.getMessage(), ex);
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Scheduled poll — backup every 5 min for missed webhook events
    // ──────────────────────────────────────────────────────────────────────

    @Scheduled(fixedDelayString = "${app.payments.yape-reconciliation.poll-ms:300000}")
    public void pollAndReconcile() {
        if (!enabled || graphTenantId == null || graphClientId == null || graphClientSecret == null) {
            return;
        }
        try {
            String token = getAccessToken();
            if (token == null) {
                log.warn("Yape reconciliation: cannot obtain Graph API token");
                return;
            }
            processUnreadEmails(token);
        } catch (Exception ex) {
            log.error("Yape reconciliation poll error: {}", ex.getMessage(), ex);
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Email processing
    // ──────────────────────────────────────────────────────────────────────

    private void processUnreadEmails(String token) throws Exception {
        // Graph API doesn't support contains() on subject with $orderby,
        // so we filter by isRead only and check subject in code
        String filter = "isRead eq false";
        String url = String.format(GRAPH_MESSAGES_URL, enc(mailboxUser))
                + "?$filter=" + enc(filter)
                + "&$top=20"
                + "&$select=id,subject,from,receivedDateTime,body"
                + "&$orderby=receivedDateTime%20desc";

        HttpResponse<String> resp = graphGet(token, url);
        if (resp.statusCode() != 200) {
            log.warn("Yape reconciliation: Graph messages API {}: {}",
                    resp.statusCode(), trunc(resp.body()));
            return;
        }

        JsonNode messages = objectMapper.readTree(resp.body()).path("value");
        if (!messages.isArray() || messages.isEmpty())
            return;

        for (JsonNode msg : messages) {
            String subject = msg.path("subject").asText("");

            // Only process Yape payment notification emails
            if (!subject.toLowerCase().contains("yapeo")) {
                continue;
            }

            String emailId = msg.path("id").asText();
            String bodyHtml = msg.path("body").path("content").asText("");
            String receivedAt = msg.path("receivedDateTime").asText("");
            try {
                reconcileOneEmail(emailId, subject, bodyHtml, receivedAt);
            } catch (Exception ex) {
                log.error("Yape reconciliation: error processing email '{}': {}",
                        trunc(subject), ex.getMessage());
            } finally {
                markAsRead(token, emailId);
            }
        }
    }

    private void reconcileOneEmail(String emailId, String subject,
            String bodyHtml, String receivedAt) {
        // 1. Extract amount
        BigDecimal amount = parseAmount(bodyHtml);
        if (amount == null) {
            log.info("Yape reconciliation: no amount in email '{}'", trunc(subject));
            return;
        }

        // 2. Extract sender name & date for audit
        String senderName = parseField(SENDER_NAME, bodyHtml);
        String txDateTime = parseField(TX_DATETIME, bodyHtml);

        log.info("Yape reconciliation: parsed — S/{} | from '{}' | at '{}'",
                amount.toPlainString(), senderName, txDateTime);

        // 3. Match against pending transfers (last 48 h)
        Instant since = Instant.now().minus(48, ChronoUnit.HOURS);
        String auditInfo = buildAuditInfo(amount, senderName, txDateTime, receivedAt, emailId);

        txTemplate.executeWithoutResult(status -> {
            List<PaymentAttempt> candidates = paymentAttemptRepository
                    .findPendingTransfersByAmountSince(PaymentStatus.PENDING, amount, since);

            if (candidates.isEmpty()) {
                log.info("Yape reconciliation: no pending transfer for S/{}",
                        amount.toPlainString());
                return;
            }

            if (candidates.size() > 1) {
                // Multiple matches — flag for manual review, don't auto-confirm
                log.warn("Yape reconciliation: {} transfers match S/{} — manual review",
                        candidates.size(), amount.toPlainString());
                for (PaymentAttempt c : candidates) {
                    c.registerGatewayOutcome("YAPE_EMAIL_MULTIPLE_MATCH",
                            "Email Yape recibido pero multiples pagos coinciden. "
                                    + auditInfo + " | Requiere verificacion manual.");
                }
                return;
            }

            // Exactly one match — auto-confirm
            PaymentAttempt pa = candidates.get(0);
            pa.confirm(pa.getProviderReference());
            pa.registerGatewayOutcome("AUTO_CONFIRMED_YAPE_EMAIL",
                    "Pago confirmado automaticamente por email Yape. " + auditInfo);

            reservationService.markPaymentConfirmed(
                    pa.getReservation().getId(), "yape");

            // Notify user via real-time event so the UI refreshes
            notificationService.emitSilentRealtimeEvent(
                    pa.getReservation().getUser().getId(),
                    "PAYMENT_SYNC",
                    Map.of("reservationId", pa.getReservation().getId()));

            log.info("Yape reconciliation: AUTO-CONFIRMED payment #{} — reservation #{} — S/{}",
                    pa.getId(), pa.getReservation().getId(), amount.toPlainString());
        });
    }

    // ──────────────────────────────────────────────────────────────────────
    // Graph API helpers
    // ──────────────────────────────────────────────────────────────────────

    private synchronized String getAccessToken() {
        if (cachedToken != null && System.currentTimeMillis() < cachedTokenExpiry) {
            return cachedToken;
        }
        String tokenUrl = String.format(GRAPH_TOKEN_URL, graphTenantId);
        String body = String.format(
                "grant_type=client_credentials&client_id=%s&client_secret=%s"
                        + "&scope=https://graph.microsoft.com/.default",
                enc(graphClientId), enc(graphClientSecret));

        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(tokenUrl))
                    .header("content-type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> resp = HTTP_CLIENT.send(req,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                log.error("Yape reconciliation token error {}: {}",
                        resp.statusCode(), trunc(resp.body()));
                return null;
            }

            JsonNode node = objectMapper.readTree(resp.body());
            cachedToken = node.path("access_token").asText(null);
            int expiresIn = node.path("expires_in").asInt(3600);
            cachedTokenExpiry = System.currentTimeMillis() + (expiresIn - 60) * 1000L;
            return cachedToken;
        } catch (Exception ex) {
            log.error("Yape reconciliation token exception: {}", ex.getMessage());
            return null;
        }
    }

    private HttpResponse<String> graphGet(String token, String url)
            throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .timeout(Duration.ofSeconds(30))
                .GET().build();
        return HTTP_CLIENT.send(req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private void markAsRead(String token, String emailId) {
        try {
            String url = String.format(GRAPH_MESSAGE_URL, enc(mailboxUser), emailId);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .header("content-type", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(
                            "{\"isRead\":true}", StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = HTTP_CLIENT.send(req,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() >= 300) {
                log.warn("Yape reconciliation: mark-read failed {}: {}",
                        resp.statusCode(), trunc(resp.body()));
            }
        } catch (Exception ex) {
            log.warn("Yape reconciliation: mark-read error: {}", ex.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // BCP email parsing
    // ──────────────────────────────────────────────────────────────────────

    private BigDecimal parseAmount(String html) {
        Matcher m = AMOUNT_INLINE.matcher(html);
        if (m.find())
            return parseMoney(m.group(1));
        m = AMOUNT_TABLE.matcher(html);
        if (m.find())
            return parseMoney(m.group(1));
        return null;
    }

    private BigDecimal parseMoney(String raw) {
        try {
            return new BigDecimal(raw.replace(",", "").trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String parseField(Pattern pattern, String html) {
        Matcher m = pattern.matcher(html);
        if (m.find())
            return m.group(1).replaceAll("<[^>]+>", "").trim();
        return null;
    }

    private String buildAuditInfo(BigDecimal amount, String sender,
            String txDate, String receivedAt, String emailId) {
        return String.format(
                "Monto: S/%s | Remitente Yape: %s | Fecha operacion: %s | Email recibido: %s",
                amount.toPlainString(),
                sender != null ? sender : "N/A",
                txDate != null ? txDate : "N/A",
                receivedAt);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Utilities
    // ──────────────────────────────────────────────────────────────────────

    private static String enc(String s) {
        return s == null ? "" : URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String norm(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static String trunc(String s) {
        return s == null ? "" : (s.length() > 300 ? s.substring(0, 300) + "..." : s);
    }
}
