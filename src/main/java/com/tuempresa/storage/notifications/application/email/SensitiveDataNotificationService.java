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

        String subject = getLocalizedText(locale, "sensitive_data_changed_subject",
                "Aviso: Datos actualizados", 
                "Notice: Data updated",
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

        String subject = getLocalizedText(locale, "email_change_request_subject",
                "Solicitud de cambio de email", 
                "Email change request",
                "Solicitacao de mudanca de email");
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

        String subject = getLocalizedText(locale, "email_change_confirmed_subject",
                "Email actualizado correctamente", 
                "Email updated successfully",
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

        String subject = getLocalizedText(locale, "admin_access_subject",
                "Acceso a tus datos personales", 
                "Access to your personal data",
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

    private String getLocalizedText(String locale, String key, String spanish, String english, String portuguese) {
        return switch (locale.toLowerCase()) {
            case "en" -> english;
            case "pt" -> portuguese;
            default -> spanish;
        };
    }

    private String buildSensitiveDataChangedBody(String locale, Map<String, Object> data) {
        String title = getLocalizedText(locale, "sensitive_data_changed_title",
                "⚠️ Aviso: Datos Actualizados",
                "⚠️ Notice: Data Updated",
                "⚠️ Aviso: Dados Atualizados");

        String intro = getLocalizedText(locale, "sensitive_data_changed_intro",
                "Te informamos que se han realizado cambios en tus datos sensibles:",
                "We inform you that changes have been made to your sensitive data:",
                "Informamos que foram realizadas alteracoes nos seus dados sensiveis:");

        String contact = getLocalizedText(locale, "sensitive_data_changed_contact",
                "Si no realizaste estos cambios, contacta inmediatamente a soporte.",
                "If you did not make these changes, contact support immediately.",
                "Se voce nao fez essas alteracoes, entre em contato com suporte imediatamente.");

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
                        <strong>Cambios realizados:</strong>
                        <ul>
            """.formatted(locale, title, data.get("userName"), intro);

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
                    <p>Fecha y hora: %s</p>
                </div>
                <div class="footer">
                    <p>%s - Sistema de Seguridad</p>
                    <p>Este es un email automatico. No responder.</p>
                </div>
            </body>
            </html>
            """.formatted(contact, data.get("timestamp"), brandName);

        return html;
    }

    private String buildEmailChangeRequestBody(String locale, Map<String, Object> data) {
        String title = getLocalizedText(locale, "email_change_request_title",
                "Solicitud de cambio de email",
                "Email change request",
                "Solicitacao de mudanca de email");

        String intro = getLocalizedText(locale, "email_change_request_intro",
                "Recibimos una solicitud para cambiar tu email a:",
                "We received a request to change your email to:",
                "Recebemos uma solicitacao para alterar seu email para:");

        String instruction = getLocalizedText(locale, "email_change_request_instruction",
                "Si fuiste tu, ignora este email. Si no fuiste tu, contacta a soporte inmediatamente.",
                "If this was you, ignore this email. If it was not you, contact support immediately.",
                "Se foi voce, ignore este email. Se nao foi voce, entre em contato com suporte imediatamente.");

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
                        <strong>Nuevo email:</strong> %s
                    </div>
                    <p>%s</p>
                    <p>Fecha: %s</p>
                </div>
                <div class="footer">
                    <p>%s</p>
                </div>
            </body>
            </html>
            """.formatted(
                locale, title, data.get("userName"), intro, data.get("newEmail"),
                instruction, data.get("timestamp"), brandName
        );
    }

    private String buildEmailChangeConfirmedBody(String locale, Map<String, Object> data) {
        String title = getLocalizedText(locale, "email_change_confirmed_title",
                "Email actualizado correctamente",
                "Email updated successfully",
                "Email atualizado com sucesso");

        String message = getLocalizedText(locale, "email_change_confirmed_message",
                "Tu email ha sido actualizado exitosamente.",
                "Your email has been successfully updated.",
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
                    <p><strong>Email anterior:</strong> %s</p>
                    <p><strong>Nuevo email:</strong> %s</p>
                </div>
                <div class="footer">
                    <p>%s</p>
                </div>
            </body>
            </html>
            """.formatted(
                locale, title, data.get("userName"), message, 
                data.get("oldEmail"), data.get("newEmail"), brandName
        );
    }

    private String buildAdminAccessBody(String locale, Map<String, Object> data) {
        String title = getLocalizedText(locale, "admin_access_title",
                "🔐 Acceso a tus datos personales",
                "🔐 Access to your personal data",
                "🔐 Acesso aos seus dados pessoais");

        String intro = getLocalizedText(locale, "admin_access_intro",
                "Un administrador accedio a tus datos personales:",
                "An administrator accessed your personal data:",
                "Um administrador acessou seus dados pessoais:");

        String warning = getLocalizedText(locale, "admin_access_warning",
                "Si no autorizaste este acceso, contacta a soporte inmediatamente.",
                "If you did not authorize this access, contact support immediately.",
                "Se voce nao autorizou este acesso, entre em contato com suporte imediatamente.");

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
                        <p><strong>Administrador:</strong> %s</p>
                        <p><strong>Accion:</strong> %s</p>
                        <p><strong>Razon:</strong> %s</p>
                        <p><strong>Fecha y hora:</strong> %s</p>
                    </div>
                    <p>%s</p>
                </div>
                <div class="footer">
                    <p>%s</p>
                </div>
            </body>
            </html>
            """.formatted(
                locale, title, data.get("userName"), intro,
                data.get("adminName"), data.get("action"), data.get("reason"),
                data.get("timestamp"), warning, brandName
        );
    }
}
