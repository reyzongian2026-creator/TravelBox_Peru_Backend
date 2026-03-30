package com.tuempresa.storage.notifications.application.email;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.MessagingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class EmailDispatchService {

    private static final Logger log = LoggerFactory.getLogger(EmailDispatchService.class);
    private static final URI BREVO_SEND_EMAIL_URI = URI.create("https://api.brevo.com/v3/smtp/email");
    private static final String GRAPH_TOKEN_URI_TEMPLATE = "https://login.microsoftonline.com/%s/oauth2/v2.0/token";
    private static final String GRAPH_SEND_MAIL_URI_TEMPLATE = "https://graph.microsoft.com/v1.0/users/%s/sendMail";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String provider;
    private final String smtpHost;
    private final String smtpPassword;
    private final String fromAddress;
    private final String fromName;
    private final ObjectMapper objectMapper;
    private final String graphTenantId;
    private final String graphClientId;
    private final String graphClientSecret;
    private volatile String cachedGraphAccessToken;
    private volatile long cachedGraphTokenExpiry;

    public EmailDispatchService(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${app.email.provider:graph}") String provider,
            @Value("${spring.mail.host:}") String smtpHost,
            @Value("${spring.mail.password:}") String smtpPassword,
            @Value("${app.email.from-address:admin@inkavoy.pe}") String fromAddress,
            @Value("${app.email.from-name:Inkavoy}") String fromName,
            @Value("${app.email.graph-tenant-id:}") String graphTenantId,
            @Value("${app.email.graph-client-id:}") String graphClientId,
            @Value("${app.email.graph-client-secret:}") String graphClientSecret
    ) {
        this.mailSenderProvider = mailSenderProvider;
        this.provider = normalize(provider) == null ? "graph" : normalize(provider).toLowerCase(Locale.ROOT);
        this.smtpHost = normalize(smtpHost) == null ? "" : normalize(smtpHost).toLowerCase(Locale.ROOT);
        this.smtpPassword = normalize(smtpPassword);
        this.fromAddress = normalize(fromAddress);
        this.fromName = normalize(fromName);
        this.objectMapper = new ObjectMapper();
        this.graphTenantId = normalize(graphTenantId);
        this.graphClientId = normalize(graphClientId);
        this.graphClientSecret = normalize(graphClientSecret);
    }

    public boolean isMockProvider() {
        return !"smtp".equals(provider) && !"graph".equals(provider);
    }

    public DispatchResult sendHtml(String to, String subject, String htmlBody, String textBody) {
        String recipient = normalize(to);
        String safeSubject = normalize(subject);
        if (recipient == null || safeSubject == null) {
            return DispatchResult.failed(provider, "EMAIL_RECIPIENT_OR_SUBJECT_REQUIRED");
        }

        if (isMockProvider()) {
            log.info("Mock email [{}] para {}", safeSubject, recipient);
            return DispatchResult.sent("mock");
        }

        if ("graph".equals(provider)) {
            DispatchResult graphResult = sendViaGraphApi(recipient, safeSubject, htmlBody, textBody);
            if (graphResult.sent()) {
                return graphResult;
            }
            log.warn("Graph API failed for {}: {}", recipient, graphResult.errorMessage());
        }

        if ("smtp-relay.brevo.com".equals(smtpHost)) {
            DispatchResult brevoResult = sendViaBrevoApi(recipient, safeSubject, htmlBody, textBody);
            if (brevoResult.sent()) {
                return brevoResult;
            }
            log.warn("Brevo API failed for {}: {}", recipient, brevoResult.errorMessage());
        }

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            return DispatchResult.failed("smtp", "SMTP_NOT_CONFIGURED");
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            if (fromAddress != null && fromName != null) {
                helper.setFrom(fromAddress, fromName);
            } else if (fromAddress != null) {
                helper.setFrom(fromAddress);
            } else {
                helper.setFrom("admin@inkavoy.pe");
            }
            helper.setTo(recipient);
            helper.setSubject(safeSubject);
            helper.setText(
                    textBody == null ? "" : textBody,
                    htmlBody == null ? "" : htmlBody
            );
            mailSender.send(message);
            return DispatchResult.sent("smtp");
        } catch (MessagingException | RuntimeException | java.io.UnsupportedEncodingException ex) {
            log.error("No se pudo enviar correo a {}: {}", recipient, ex.getMessage(), ex);
            return DispatchResult.failed("smtp", ex.getMessage());
        }
    }

    private synchronized String getGraphAccessToken() {
        if (cachedGraphAccessToken != null && System.currentTimeMillis() < cachedGraphTokenExpiry) {
            return cachedGraphAccessToken;
        }
        if (graphTenantId == null || graphClientId == null || graphClientSecret == null) {
            return null;
        }

        String tokenUrl = String.format(GRAPH_TOKEN_URI_TEMPLATE, graphTenantId);
        String requestBody = String.format(
                "grant_type=client_credentials&client_id=%s&client_secret=%s&scope=https://graph.microsoft.com/.default",
                URLEncoder.encode(graphClientId, StandardCharsets.UTF_8),
                URLEncoder.encode(graphClientSecret, StandardCharsets.UTF_8)
        );

        HttpRequest request = HttpRequest.newBuilder(URI.create(tokenUrl))
                .timeout(Duration.ofSeconds(20))
                .header("content-type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = HTTP_CLIENT.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                var node = objectMapper.readTree(response.body());
                String accessToken = node.get("access_token").asText();
                int expiresIn = node.get("expires_in").asInt();
                cachedGraphAccessToken = accessToken;
                cachedGraphTokenExpiry = System.currentTimeMillis() + (expiresIn - 60) * 1000L;
                return accessToken;
            }
            log.error("Graph token error: {} - {}", response.statusCode(), truncate(response.body()));
            return null;
        } catch (Exception ex) {
            log.error("Graph token exception: {}", ex.getMessage(), ex);
            return null;
        }
    }

    private DispatchResult sendViaGraphApi(String recipient, String subject, String htmlBody, String textBody) {
        String accessToken = getGraphAccessToken();
        if (accessToken == null) {
            return DispatchResult.failed("graph", "GRAPH_TOKEN_FAILED");
        }
        if (fromAddress == null) {
            return DispatchResult.failed("graph", "GRAPH_FROM_ADDRESS_MISSING");
        }

        String senderEmail = fromAddress;
        String sendMailUrl = String.format(GRAPH_SEND_MAIL_URI_TEMPLATE, URLEncoder.encode(senderEmail, StandardCharsets.UTF_8));

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("toRecipients", List.of(Map.of("emailAddress", Map.of("address", recipient))));
        message.put("subject", subject);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contentType", htmlBody != null ? "HTML" : "Text");
        body.put("content", htmlBody != null ? htmlBody : (textBody != null ? textBody : ""));
        message.put("body", body);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("message", message);
        payload.put("saveToSentItems", false);

        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return DispatchResult.failed("graph", "GRAPH_PAYLOAD_SERIALIZATION_ERROR");
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(sendMailUrl))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + accessToken)
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = HTTP_CLIENT.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return DispatchResult.sent("graph");
            }
            return DispatchResult.failed(
                    "graph",
                    "GRAPH_API_" + response.statusCode() + ": " + truncate(response.body())
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return DispatchResult.failed("graph", "GRAPH_API_INTERRUPTED");
        } catch (IOException ex) {
            return DispatchResult.failed("graph", "GRAPH_API_IO_ERROR: " + truncate(ex.getMessage()));
        }
    }

    private DispatchResult sendViaBrevoApi(String recipient, String subject, String htmlBody, String textBody) {
        if (smtpPassword == null) {
            return DispatchResult.failed("brevo", "BREVO_API_KEY_MISSING");
        }
        if (fromAddress == null) {
            return DispatchResult.failed("brevo", "BREVO_FROM_ADDRESS_MISSING");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        Map<String, String> sender = new LinkedHashMap<>();
        sender.put("email", fromAddress);
        if (fromName != null) {
            sender.put("name", fromName);
        }
        payload.put("sender", sender);
        payload.put("to", List.of(Map.of("email", recipient)));
        payload.put("subject", subject);
        payload.put("htmlContent", htmlBody == null ? "" : htmlBody);
        payload.put("textContent", textBody == null ? "" : textBody);

        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return DispatchResult.failed("brevo", "BREVO_PAYLOAD_SERIALIZATION_ERROR");
        }

        HttpRequest request = HttpRequest.newBuilder(BREVO_SEND_EMAIL_URI)
                .timeout(Duration.ofSeconds(20))
                .header("accept", "application/json")
                .header("content-type", "application/json")
                .header("api-key", smtpPassword)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = HTTP_CLIENT.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return DispatchResult.sent("brevo");
            }
            return DispatchResult.failed(
                    "brevo",
                    "BREVO_API_" + response.statusCode() + ": " + truncate(response.body())
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return DispatchResult.failed("brevo", "BREVO_API_INTERRUPTED");
        } catch (IOException ex) {
            return DispatchResult.failed("brevo", "BREVO_API_IO_ERROR: " + truncate(ex.getMessage()));
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        if (normalized.length() <= 300) {
            return normalized;
        }
        return normalized.substring(0, 300);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    public record DispatchResult(
            boolean sent,
            String provider,
            String errorMessage
    ) {
        public static DispatchResult sent(String provider) {
            return new DispatchResult(true, provider, null);
        }

        public static DispatchResult failed(String provider, String errorMessage) {
            return new DispatchResult(false, provider, errorMessage);
        }
    }
}
