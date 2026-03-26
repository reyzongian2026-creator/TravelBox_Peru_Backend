package com.tuempresa.storage.notifications.application.email;

import com.tuempresa.storage.users.domain.User;
import com.tuempresa.storage.shared.infrastructure.security.SensitiveDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class SensitiveDataNotificationService {

    private static final Logger LOG = LoggerFactory.getLogger(SensitiveDataNotificationService.class);
    private static final ZoneId LIMA_ZONE = ZoneId.of("America/Lima");

    private final EmailOutboxService emailOutboxService;
    private final SensitiveDataService sensitiveDataService;
    private final String brandName;
    private final String frontendBaseUrl;

    public SensitiveDataNotificationService(
            EmailOutboxService emailOutboxService,
            SensitiveDataService sensitiveDataService,
            @Value("${app.email.brand-name:TravelBox Peru}") String brandName,
            @Value("${app.frontend-base-url:}") String frontendBaseUrl
    ) {
        this.emailOutboxService = emailOutboxService;
        this.sensitiveDataService = sensitiveDataService;
        this.brandName = brandName != null ? brandName : "TravelBox Peru";
        this.frontendBaseUrl = frontendBaseUrl != null ? frontendBaseUrl : "";
    }

    public void sendSensitiveDataChangedNotification(User user, Map<String, String> changes) {
        if (user == null) {
            return;
        }

        String locale = user.getPreferredLanguage() != null ? user.getPreferredLanguage() : "es";
        Map<String, Object> templateData = new HashMap<>();
        templateData.put("userName", user.getFullName());
        templateData.put("changes", changes);
        templateData.put("timestamp", DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                .withLocale(new Locale(locale))
                .withZone(LIMA_ZONE)
                .format(java.time.Instant.now()));

        String subject = getLocalizedText(locale,
                "Aviso: Datos actualizados",
                "Notice: Data updated",
                "Hinweis: Daten aktualisiert",
                "Avis: Données mises à jour",
                "Avviso: Dati aggiornati",
                "Aviso: Dados atualizados");
        String body = buildSensitiveDataChangedBody(locale, templateData);

        String dedupKey = "SENSITIVE_DATA_CHANGED:" + user.getId() + ":" + System.currentTimeMillis();

        emailOutboxService.enqueue(
                user.getEmail(),
                brandName + " | " + subject,
                body,
                null,
                "SENSITIVE_DATA_CHANGED",
                dedupKey
        );
    }

    public void sendEmailChangeRequestNotification(User user, String newEmail) {
        if (user == null) {
            return;
        }

        String locale = user.getPreferredLanguage() != null ? user.getPreferredLanguage() : "es";
        String maskedNewEmail = sensitiveDataService.maskEmail(newEmail);

        Map<String, Object> templateData = new HashMap<>();
        templateData.put("userName", user.getFullName());
        templateData.put("newEmail", maskedNewEmail);
        templateData.put("oldEmail", sensitiveDataService.maskEmail(user.getEmail()));
        templateData.put("timestamp", DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                .withLocale(new Locale(locale))
                .withZone(LIMA_ZONE)
                .format(java.time.Instant.now()));

        String subject = getLocalizedText(locale,
                "Solicitud de cambio de email",
                "Email change request",
                "Anfrage zur E-Mail-Änderung",
                "Demande de changement d'email",
                "Richiesta di cambio email",
                "Solicitação de mudança de email");
        String body = buildEmailChangeRequestBody(locale, templateData);

        String dedupKey = "EMAIL_CHANGE_REQUEST:" + user.getId();

        emailOutboxService.enqueue(
                user.getEmail(),
                brandName + " | " + subject,
                body,
                null,
                "EMAIL_CHANGE_REQUEST",
                dedupKey
        );
    }

    public void sendEmailChangeConfirmationToBoth(User user, String oldEmail, String newEmail) {
        if (user == null) {
            return;
        }

        String locale = user.getPreferredLanguage() != null ? user.getPreferredLanguage() : "es";

        Map<String, Object> templateData = new HashMap<>();
        templateData.put("userName", user.getFullName());
        templateData.put("oldEmail", sensitiveDataService.maskEmail(oldEmail));
        templateData.put("newEmail", sensitiveDataService.maskEmail(newEmail));

        String subject = getLocalizedText(locale,
                "Email actualizado correctamente",
                "Email updated successfully",
                "E-Mail erfolgreich aktualisiert",
                "Email mis à jour avec succès",
                "Email aggiornato con successo",
                "Email atualizado com sucesso");
        String body = buildEmailChangeConfirmedBody(locale, templateData);

        emailOutboxService.enqueue(
                newEmail,
                brandName + " | " + subject,
                body,
                null,
                "EMAIL_CHANGE_CONFIRMED_NEW",
                "EMAIL_CHANGE_NEW:" + newEmail + ":" + System.currentTimeMillis()
        );
    }

    public void sendAdminAccessNotification(User user, String adminName, String action, String reason) {
        if (user == null) {
            return;
        }

        String locale = user.getPreferredLanguage() != null ? user.getPreferredLanguage() : "es";

        Map<String, Object> templateData = new HashMap<>();
        templateData.put("userName", user.getFullName());
        templateData.put("adminName", adminName);
        templateData.put("action", action);
        templateData.put("reason", reason != null ? reason : "No especificada");
        templateData.put("timestamp", DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                .withLocale(new Locale(locale))
                .withZone(LIMA_ZONE)
                .format(java.time.Instant.now()));

        String subject = getLocalizedText(locale,
                "Acceso a tus datos personales",
                "Access to your personal data",
                "Zugriff auf Ihre persönlichen Daten",
                "Accès à vos données personnelles",
                "Accesso ai tuoi dati personali",
                "Acesso aos seus dados pessoais");
        String body = buildAdminAccessBody(locale, templateData);

        String dedupKey = "ADMIN_ACCESS:" + user.getId() + ":" + System.currentTimeMillis();

        emailOutboxService.enqueue(
                user.getEmail(),
                brandName + " | " + subject,
                body,
                null,
                "ADMIN_ACCESS",
                dedupKey
        );
    }

    private String getLocalizedText(String locale, String spanish, String english, String german, String french, String italian, String portuguese) {
        return switch (locale.toLowerCase()) {
            case "en" -> english;
            default -> spanish;
        };
    }

    private String buildSensitiveDataChangedBody(String locale, Map<String, Object> data) {
        String title = getLocalizedText(locale,
                "⚠️ Aviso: Datos Actualizados",
                "⚠️ Notice: Data Updated",
                "⚠️ Hinweis: Daten aktualisiert",
                "⚠️ Avis: Données mises à jour",
                "⚠️ Avviso: Dati aggiornati",
                "⚠️ Aviso: Dados Atualizados");

        String intro = getLocalizedText(locale,
                "Te informamos que se han realizado cambios en tus datos sensibles:",
                "We inform you that changes have been made to your sensitive data:",
                "Wir informieren Sie, dass Änderungen an Ihren sensiblen Daten vorgenommen wurden:",
                "Nous vous informons que des modifications ont été apportées à vos données sensibles:",
                "Ti informiamo che sono state apportate modifiche ai tuoi dati sensibili:",
                "Informamos que foram realizadas alterações nos seus dados sensíveis:");

        String contact = getLocalizedText(locale,
                "Si no realizaste estos cambios, contacta inmediatamente a soporte.",
                "If you did not make these changes, contact support immediately.",
                "Wenn Sie diese Änderungen nicht vorgenommen haben, wenden Sie sich sofort an den Support.",
                "Si vous n'avez pas effectué ces modifications, contactez immédiatement le support.",
                "Se non hai apportato queste modifiche, contatta immediatamente il supporto.",
                "Se você não fez essas alterações, entre em contato com suporte imediatamente.");

        String html = """
            <!DOCTYPE html>
            <html lang="%s">
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; margin: 0; padding: 0; }
                    .header { background: #FF9800; color: white; padding: 20px; }
                    .content { padding: 20px; }
                    .warning { background: #FFF3E0; border-left: 4px solid #FF9800; padding: 15px; margin: 15px 0; }
                    .footer { color: #666; font-size: 12px; padding: 20px; border-top: 1px solid #eee; }
                </style>
            </head>
            <body>
                <div class="header">
                    <h1>%s</h1>
                </div>
                <div class="content">
                    <p>Hola %s,</p>
                    <p>%s</p>
                    <div class="warning">
                        <strong>%s:</strong>
                        <ul>
            """.formatted(
                locale,
                title,
                data.get("userName"),
                intro,
                getLocalizedText(locale, "Cambios realizados", "Changes made", "Änderungen vorgenommen", "Modifications apportées", "Modifiche apportate", "Alterações realizadas")
            );

        @SuppressWarnings("unchecked")
        Map<String, String> changes = (Map<String, String>) data.get("changes");
        if (changes != null) {
            for (Map.Entry<String, String> entry : changes.entrySet()) {
                html += "<li><strong>" + entry.getKey() + ":</strong> " + entry.getValue() + "</li>";
            }
        }

        html += """
                        </ul>
                    </div>
                    <p>%s</p>
                    <p>%s: %s</p>
                </div>
                <div class="footer">
                    <p>%s - %s</p>
                    <p>%s</p>
                </div>
            </body>
            </html>
            """.formatted(
                contact,
                getLocalizedText(locale, "Fecha y hora", "Date and time", "Datum und Uhrzeit", "Date et heure", "Data e ora", "Data e hora"),
                data.get("timestamp"),
                brandName,
                getLocalizedText(locale, "Sistema de Seguridad", "Security System", "Sicherheitssystem", "Système de sécurité", "Sistema di sicurezza", "Sistema de Segurança"),
                getLocalizedText(locale, "Este es un email automatico. No responder.", "This is an automatic email. Do not reply.", "Dies ist eine automatische E-Mail. Nicht antworten.", "Ceci est un email automatique. Ne pas répondre.", "Questa è un'email automatica. Non rispondere.", "Este é um email automático. Não responder.")
            );

        return html;
    }

    private String buildEmailChangeRequestBody(String locale, Map<String, Object> data) {
        String title = getLocalizedText(locale,
                "Solicitud de cambio de email",
                "Email change request",
                "Anfrage zur E-Mail-Änderung",
                "Demande de changement d'email",
                "Richiesta di cambio email",
                "Solicitação de mudança de email");

        String intro = getLocalizedText(locale,
                "Recibimos una solicitud para cambiar tu email a:",
                "We received a request to change your email to:",
                "Wir haben eine Anfrage erhalten, Ihre E-Mail-Adresse zu ändern zu:",
                "Nous avons reçu une demande pour changer votre email en:",
                "Abbiamo ricevuto una richiesta per cambiare la tua email in:",
                "Recebemos uma solicitação para alterar seu email para:");

        String instruction = getLocalizedText(locale,
                "Si fuiste tu, ignora este email. Si no fuiste tu, contacta a soporte inmediatamente.",
                "If this was you, ignore this email. If it was not you, contact support immediately.",
                "Wenn Sie es waren, ignorieren Sie diese E-Mail. Wenn Sie es nicht waren, wenden Sie sich sofort an den Support.",
                "Si c'était vous, ignorez cet email. Si ce n'était pas vous, contactez immédiatement le support.",
                "Se eri tu, ignora questa email. Se non eri tu, contatta immediatamente il supporto.",
                "Se foi você, ignore este email. Se não foi você, entre em contato com suporte imediatamente.");

        return """
            <!DOCTYPE html>
            <html lang="%s">
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; margin: 0; padding: 0; }
                    .header { background: #2196F3; color: white; padding: 20px; }
                    .content { padding: 20px; }
                    .email-box { background: #E3F2FD; padding: 15px; margin: 15px 0; font-size: 18px; }
                    .footer { color: #666; font-size: 12px; padding: 20px; border-top: 1px solid #eee; }
                </style>
            </head>
            <body>
                <div class="header">
                    <h1>%s</h1>
                </div>
                <div class="content">
                    <p>Hola %s,</p>
                    <p>%s</p>
                    <div class="email-box">
                        <strong>%s:</strong> %s
                    </div>
                    <p>%s</p>
                    <p>%s: %s</p>
                </div>
                <div class="footer">
                    <p>%s</p>
                </div>
            </body>
            </html>
            """            .formatted(
                locale, title, data.get("userName"), intro,
                getLocalizedText(locale, "Nuevo email", "New email", "Neue E-Mail", "Nouvel email", "Nuova email", "Novo email"),
                data.get("newEmail"),
                instruction,
                getLocalizedText(locale, "Fecha", "Date", "Datum", "Date", "Data", "Data"),
                data.get("timestamp"),
                brandName
        );
    }

    private String buildEmailChangeConfirmedBody(String locale, Map<String, Object> data) {
        String title = getLocalizedText(locale,
                "Email actualizado correctamente",
                "Email updated successfully",
                "E-Mail erfolgreich aktualisiert",
                "Email mis à jour avec succès",
                "Email aggiornato con successo",
                "Email atualizado com sucesso");

        String message = getLocalizedText(locale,
                "Tu email ha sido actualizado exitosamente.",
                "Your email has been successfully updated.",
                "Ihre E-Mail-Adresse wurde erfolgreich aktualisiert.",
                "Votre email a été mis à jour avec succès.",
                "La tua email è stata aggiornata con successo.",
                "Seu email foi atualizado com sucesso.");

        return """
            <!DOCTYPE html>
            <html lang="%s">
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; margin: 0; padding: 0; }
                    .header { background: #4CAF50; color: white; padding: 20px; }
                    .content { padding: 20px; }
                    .footer { color: #666; font-size: 12px; padding: 20px; border-top: 1px solid #eee; }
                </style>
            </head>
            <body>
                <div class="header">
                    <h1>%s</h1>
                </div>
                <div class="content">
                    <p>Hola %s,</p>
                    <p>%s</p>
                    <p><strong>%s:</strong> %s</p>
                    <p><strong>%s:</strong> %s</p>
                </div>
                <div class="footer">
                    <p>%s</p>
                </div>
            </body>
            </html>
            """            .formatted(
                locale, title, data.get("userName"), message,
                getLocalizedText(locale, "Email anterior", "Previous email", "Vorherige E-Mail", "Email précédent", "Email precedente", "Email anterior"),
                data.get("oldEmail"),
                getLocalizedText(locale, "Nuevo email", "New email", "Neue E-Mail", "Nouvel email", "Nuova email", "Novo email"),
                data.get("newEmail"),
                brandName
        );
    }

    private String buildAdminAccessBody(String locale, Map<String, Object> data) {
        String title = getLocalizedText(locale,
                "🔐 Acceso a tus datos personales",
                "🔐 Access to your personal data",
                "🔐 Zugriff auf Ihre persönlichen Daten",
                "🔐 Accès à vos données personnelles",
                "🔐 Accesso ai tuoi dati personali",
                "🔐 Acesso aos seus dados pessoais");

        String intro = getLocalizedText(locale,
                "Un administrador accedio a tus datos personales:",
                "An administrator accessed your personal data:",
                "Ein Administrator hat auf Ihre persönlichen Daten zugegriffen:",
                "Un administrateur a accédé à vos données personnelles:",
                "Un amministratore ha acceduto ai tuoi dati personali:",
                "Um administrador acessou seus dados pessoais:");

        String warning = getLocalizedText(locale,
                "Si no autorizaste este acceso, contacta a soporte inmediatamente.",
                "If you did not authorize this access, contact support immediately.",
                "Wenn Sie diesen Zugriff nicht autorisiert haben, wenden Sie sich sofort an den Support.",
                "Si vous n'avez pas autorisé cet accès, contactez immédiatement le support.",
                "Se non hai autorizzato questo accesso, contatta immediatamente il supporto.",
                "Se você não autorizou este acesso, entre em contato com suporte imediatamente.");

        return """
            <!DOCTYPE html>
            <html lang="%s">
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; margin: 0; padding: 0; }
                    .header { background: #F44336; color: white; padding: 20px; }
                    .content { padding: 20px; }
                    .info-box { background: #FFEBEE; border-left: 4px solid #F44336; padding: 15px; margin: 15px 0; }
                    .footer { color: #666; font-size: 12px; padding: 20px; border-top: 1px solid #eee; }
                </style>
            </head>
            <body>
                <div class="header">
                    <h1>%s</h1>
                </div>
                <div class="content">
                    <p>Hola %s,</p>
                    <p>%s</p>
                    <div class="info-box">
                        <p><strong>%s:</strong> %s</p>
                        <p><strong>%s:</strong> %s</p>
                        <p><strong>%s:</strong> %s</p>
                        <p><strong>%s:</strong> %s</p>
                    </div>
                    <p>%s</p>
                </div>
                <div class="footer">
                    <p>%s</p>
                </div>
            </body>
            </html>
            """            .formatted(
                locale, title, data.get("userName"), intro,
                getLocalizedText(locale, "Administrador", "Administrator", "Administrator", "Administrateur", "Amministratore", "Administrador"),
                data.get("adminName"),
                getLocalizedText(locale, "Accion", "Action", "Aktion", "Action", "Azione", "Ação"),
                data.get("action"),
                getLocalizedText(locale, "Razon", "Reason", "Grund", "Raison", "Motivo", "Razão"),
                data.get("reason"),
                getLocalizedText(locale, "Fecha y hora", "Date and time", "Datum und Uhrzeit", "Date et heure", "Data e ora", "Data e hora"),
                data.get("timestamp"),
                warning,
                brandName
        );
    }
}
