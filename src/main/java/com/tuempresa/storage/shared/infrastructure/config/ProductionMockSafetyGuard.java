package com.tuempresa.storage.shared.infrastructure.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

//@Component
//@Profile("prod")
public class ProductionMockSafetyGuard {

    private final String notificationsProvider;
    private final String routingProvider;
    private final boolean routingAllowMockFallback;
    private final String paymentsProvider;
    private final boolean paymentsAllowMockConfirmation;
    private final String authEmailProvider;
    private final boolean authExposeCodePreview;
    private final String smtpUsername;
    private final String smtpPassword;
    private final String emailFromAddress;

    public ProductionMockSafetyGuard(
            @Value("${app.notifications.provider:mock}") String notificationsProvider,
            @Value("${app.routing.provider:mock}") String routingProvider,
            @Value("${app.routing.allow-mock-fallback:true}") boolean routingAllowMockFallback,
            @Value("${app.payments.provider:culqi}") String paymentsProvider,
            @Value("${app.payments.allow-mock-confirmation:true}") boolean paymentsAllowMockConfirmation,
            @Value("${app.auth.email-provider:mock}") String authEmailProvider,
            @Value("${app.auth.expose-code-preview:false}") boolean authExposeCodePreview,
            @Value("${spring.mail.username:}") String smtpUsername,
            @Value("${spring.mail.password:}") String smtpPassword,
            @Value("${app.email.from-address:}") String emailFromAddress
    ) {
        this.notificationsProvider = normalize(notificationsProvider);
        this.routingProvider = normalize(routingProvider);
        this.routingAllowMockFallback = routingAllowMockFallback;
        this.paymentsProvider = normalize(paymentsProvider);
        this.paymentsAllowMockConfirmation = paymentsAllowMockConfirmation;
        this.authEmailProvider = normalize(authEmailProvider);
        this.authExposeCodePreview = authExposeCodePreview;
        this.smtpUsername = trim(smtpUsername);
        this.smtpPassword = trim(smtpPassword);
        this.emailFromAddress = trim(emailFromAddress);
    }

    @PostConstruct
    void validate() {
        List<String> violations = new ArrayList<>();

        if ("mock".equals(notificationsProvider)) {
            violations.add("app.notifications.provider=mock");
        }
        if ("mock".equals(routingProvider)) {
            violations.add("app.routing.provider=mock");
        }
        if (routingAllowMockFallback) {
            violations.add("app.routing.allow-mock-fallback=true");
        }
        if ("mock".equals(paymentsProvider)) {
            violations.add("app.payments.provider=mock");
        }
        if (paymentsAllowMockConfirmation) {
            violations.add("app.payments.allow-mock-confirmation=true");
        }
        if ("mock".equals(authEmailProvider)) {
            violations.add("app.auth.email-provider=mock");
        }
        if (authExposeCodePreview) {
            violations.add("app.auth.expose-code-preview=true");
        }
        if ("smtp".equals(authEmailProvider)) {
            if (smtpUsername.isEmpty()) {
                violations.add("spring.mail.username is empty");
            }
            if (smtpPassword.isEmpty()) {
                violations.add("spring.mail.password is empty");
            }
            if (emailFromAddress.isEmpty()) {
                violations.add("app.email.from-address is empty");
            }
        }

        if (!violations.isEmpty()) {
            throw new IllegalStateException(
                    "Configuracion insegura detectada en profile=prod. Corrige: " + String.join(", ", violations)
            );
        }
    }

    private String normalize(String rawValue) {
        if (rawValue == null) {
            return "";
        }
        return rawValue.trim().toLowerCase(Locale.ROOT);
    }

    private String trim(String rawValue) {
        if (rawValue == null) {
            return "";
        }
        return rawValue.trim();
    }
}
