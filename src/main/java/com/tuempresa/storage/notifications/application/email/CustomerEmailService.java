package com.tuempresa.storage.notifications.application.email;

import com.tuempresa.storage.reservations.domain.Reservation;
import com.tuempresa.storage.users.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class CustomerEmailService {

    private static final Logger log = LoggerFactory.getLogger(CustomerEmailService.class);
    private static final ZoneId LIMA_ZONE = ZoneId.of("America/Lima");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withLocale(new Locale("es", "PE"))
            .withZone(LIMA_ZONE);

    private final EmailOutboxService emailOutboxService;
    private final String frontendBaseUrl;
    private final String brandName;

    public CustomerEmailService(
            EmailOutboxService emailOutboxService,
            @Value("${app.frontend-base-url:}") String frontendBaseUrl,
            @Value("${app.email.brand-name:TravelBox Peru}") String brandName
    ) {
        this.emailOutboxService = emailOutboxService;
        this.frontendBaseUrl = normalize(frontendBaseUrl);
        this.brandName = normalize(brandName) == null ? "TravelBox Peru" : normalize(brandName);
    }

    public void sendReservationCreated(User user, Reservation reservation) {
        if (user == null || reservation == null) {
            return;
        }
        String locale = getUserLocale(user);
        List<String> details = new ArrayList<>();
        details.add(getLocalizedText(locale, "Reserva", "Reservation", "Reserva") + ": #" + reservation.getId());
        details.add(getLocalizedText(locale, "Sede", "Location", "Local") + ": " + safeText(reservation.getWarehouse().getName(), "-"));
        details.add(getLocalizedText(locale, "Inicio", "Start", "Inicio") + ": " + formatInstant(reservation.getStartAt()));
        details.add(getLocalizedText(locale, "Fin", "End", "Fim") + ": " + formatInstant(reservation.getEndAt()));
        details.add(getLocalizedText(locale, "Total estimado", "Total estimated", "Total estimado") + ": " + formatMoney(reservation.getTotalPrice()));

        EmailContent content = renderTemplate(
                locale,
                getLocalizedText(locale, "Reserva registrada", "Reservation registered", "Reserva registrada"),
                getLocalizedText(locale, "Tu reserva fue registrada correctamente", "Your reservation was registered", "Sua reserva foi registrada"),
                "Hola " + displayName(user) + ", ya registramos tu reserva en " + brandName + ".",
                details,
                null,
                null,
                getLocalizedText(locale, "Ver mi reserva", "View my reservation", "Ver minha reserva"),
                reservationRoute(reservation.getId()),
                getLocalizedText(locale, "Completa el pago para activar el servicio", "Complete payment to activate the service", "Complete o pagamento para ativar")
        );
        send(
                user.getEmail(),
                "TravelBox | " + getLocalizedText(locale, "Reserva registrada", "Reservation registered", "Reserva registrada") + " #" + reservation.getId(),
                content,
                "RESERVATION_CREATED",
                "reservation-created:" + reservation.getId()
        );
    }

    public void sendPaymentConfirmed(User user, Reservation reservation, String paymentMethod) {
        if (user == null || reservation == null) {
            return;
        }
        String locale = getUserLocale(user);
        List<String> details = new ArrayList<>();
        details.add(getLocalizedText(locale, "Reservation", "Reserva", "Reservation", "Reserva") + ": #" + reservation.getId());
        details.add(getLocalizedText(locale, "Amount paid", "Monto pagado", "Amount paid", "Valor pago") + ": " + formatMoney(reservation.getTotalPrice()));
        details.add(getLocalizedText(locale, "Method", "Metodo", "Method", "Metodo") + ": " + safeText(paymentMethod, "online"));
        details.add(getLocalizedText(locale, "Status", "Estado", "Status", "Status") + ": " + getLocalizedText(locale, "Payment confirmed", "Pago confirmado", "Payment confirmed", "Pagamento confirmado"));

        EmailContent content = renderTemplate(
                locale,
                getLocalizedText(locale, "Payment approved", "Pago aprobado", "Payment approved", "Pagamento aprovado"),
                getLocalizedText(locale, "We received your payment", "Recibimos tu pago", "We received your payment", "Recebemos seu pagamento"),
                "Hola " + displayName(user) + ", confirmamos el pago de tu reserva.",
                details,
                null,
                null,
                getLocalizedText(locale, "View details", "Ver detalle", "View details", "Ver detalhes"),
                reservationRoute(reservation.getId()),
                "Gracias por confiar en " + brandName + "."
        );
        send(
                user.getEmail(),
                "TravelBox | " + getLocalizedText(locale, "Payment confirmed", "Pago confirmado", "Payment confirmed", "Pagamento confirmado") + " #" + reservation.getId(),
                content,
                "PAYMENT_CONFIRMED",
                "payment-confirmed:" + reservation.getId()
        );
    }

    public void sendPickupThankYou(User user, Reservation reservation) {
        if (user == null || reservation == null) {
            return;
        }
        String locale = getUserLocale(user);
        List<String> details = new ArrayList<>();
        details.add(getLocalizedText(locale, "Reservation", "Reserva", "Reservation", "Reserva") + ": #" + reservation.getId());
        details.add(getLocalizedText(locale, "Location", "Sede", "Location", "Local") + ": " + safeText(reservation.getWarehouse().getName(), "-"));
        details.add(getLocalizedText(locale, "Status", "Estado", "Status", "Status") + ": " + getLocalizedText(locale, "Service completed", "Servicio completado", "Service completed", "Servico concluido"));

        EmailContent content = renderTemplate(
                locale,
                getLocalizedText(locale, "Thank you for pickup", "Gracias por tu recojo", "Thank you for pickup", "Obrigado pela coleta"),
                getLocalizedText(locale, "Your service was completed", "Tu servicio fue completado", "Your service was completed", "Seu servico foi concluido"),
                "Hola " + displayName(user) + ", tu equipaje ya fue entregado correctamente.",
                details,
                null,
                null,
                getLocalizedText(locale, "Rate experience", "Calificar experiencia", "Rate experience", "Avaliar experiencia"),
                feedbackRoute(),
                getLocalizedText(locale, "Thank you for choosing", "Gracias por elegir", "Thank you for choosing", "Obrigado por escolher") + " " + brandName + ". " + getLocalizedText(locale, "See you soon", "Te esperamos pronto", "See you soon", "Ate logo")
        );
        send(
                user.getEmail(),
                "TravelBox | " + getLocalizedText(locale, "Thank you for your trust", "Gracias por tu confianza", "Thank you for your trust", "Obrigado pela sua confianca"),
                content,
                "PICKUP_THANK_YOU",
                "pickup-thank-you:" + reservation.getId()
        );
    }

    public void sendEmailVerification(
            User user,
            String verificationCode,
            Instant expiresAt,
            String verificationReason
    ) {
        if (user == null || verificationCode == null || verificationCode.isBlank()) {
            return;
        }
        String locale = getUserLocale(user);
        List<String> details = new ArrayList<>();
        details.add(getLocalizedText(locale, "Email", "Correo", "Email", "Email") + ": " + safeText(user.getEmail(), "-"));
        details.add(getLocalizedText(locale, "Valid until", "Validez", "Valid until", "Valido ate") + ": " + formatInstant(expiresAt) + " (" + getLocalizedText(locale, "Lima time", "hora Lima", "Lima time", "hora Lima") + ")");
        if (verificationReason != null && !verificationReason.isBlank()) {
            details.add(getLocalizedText(locale, "Reason", "Motivo", "Reason", "Motivo") + ": " + verificationReason.trim());
        }

        EmailContent content = renderTemplate(
                locale,
                getLocalizedText(locale, "Verification required", "Verificacion requerida", "Verification required", "Verificacao obrigatoria"),
                getLocalizedText(locale, "Confirm your email", "Confirma tu correo", "Confirm your email", "Confirme seu email"),
                "Hola " + displayName(user) + ", usa este codigo para validar tu correo.",
                details,
                verificationCode,
                expiresAt,
                getLocalizedText(locale, "Go to login", "Ir al inicio de sesion", "Go to login", "Ir para login"),
                loginRoute(),
                getLocalizedText(locale, "If you did not request", "Si no solicitaste esta accion, ignora este mensaje", "If you did not request this action, ignore this message", "Se voce nao solicitou esta acao, ignore esta mensagem")
        );
        send(
                user.getEmail(),
                "TravelBox | " + getLocalizedText(locale, "Verification code", "Codigo de verificacion", "Verification code", "Codigo de verificacao"),
                content,
                "EMAIL_VERIFICATION_CODE",
                "email-verification:" + safeId(user) + ":" + verificationCode.trim()
        );
    }

    public void sendPasswordResetCode(User user, String resetCode, Instant expiresAt) {
        if (user == null || resetCode == null || resetCode.isBlank()) {
            return;
        }
        String locale = getUserLocale(user);
        List<String> details = new ArrayList<>();
        details.add(getLocalizedText(locale, "Email", "Correo", "Email", "Email") + ": " + safeText(user.getEmail(), "-"));
        details.add(getLocalizedText(locale, "Valid until", "Validez", "Valid until", "Valido ate") + ": " + formatInstant(expiresAt) + " (" + getLocalizedText(locale, "Lima time", "hora Lima", "Lima time", "hora Lima") + ")");

        EmailContent content = renderTemplate(
                locale,
                getLocalizedText(locale, "Password recovery", "Recuperacion de contrasena", "Password recovery", "Recuperacao de senha"),
                getLocalizedText(locale, "Reset your password", "Restablece tu contrasena", "Reset your password", "Redefina sua senha"),
                "Hola " + displayName(user) + ", usa este codigo para recuperar el acceso.",
                details,
                resetCode,
                expiresAt,
                getLocalizedText(locale, "Open recovery", "Abrir recuperacion", "Open recovery", "Abrir recuperacao"),
                passwordResetRoute(),
                getLocalizedText(locale, "If you did not request change", "Si no solicitaste el cambio, protege tu cuenta", "If you did not request the change, protect your account", "Se voce nao solicitou a mudanca, proteja sua conta")
        );
        send(
                user.getEmail(),
                "TravelBox | " + getLocalizedText(locale, "Code to reset password", "Codigo para restablecer contrasena", "Code to reset password", "Codigo para redefinir senha"),
                content,
                "PASSWORD_RESET_CODE",
                "password-reset:" + safeId(user) + ":" + resetCode.trim()
        );
    }

    public void sendPasswordChangedConfirmation(User user) {
        if (user == null) {
            return;
        }
        String locale = getUserLocale(user);
        List<String> details = new ArrayList<>();
        details.add(getLocalizedText(locale, "Email", "Correo", "Email", "Email") + ": " + safeText(user.getEmail(), "-"));
        details.add(getLocalizedText(locale, "Date", "Fecha", "Date", "Data") + ": " + formatInstant(Instant.now()) + " (" + getLocalizedText(locale, "Lima time", "hora Lima", "Lima time", "hora Lima") + ")");

        EmailContent content = renderTemplate(
                locale,
                getLocalizedText(locale, "Password updated", "Contrasena actualizada", "Password updated", "Senha atualizada"),
                getLocalizedText(locale, "Your password was changed", "Tu contrasena fue cambiada", "Your password was changed", "Sua senha foi alterada"),
                "Hola " + displayName(user) + ", registramos un cambio de contrasena exitoso.",
                details,
                null,
                null,
                getLocalizedText(locale, "Login to my account", "Ingresar a mi cuenta", "Login to my account", "Entrar na minha conta"),
                loginRoute(),
                getLocalizedText(locale, "If it was not you", "Si no fuiste tu, contacta soporte inmediatamente", "If it was not you, contact support immediately", "Se nao foi voce, entre em contato com suporte imediatamente")
        );
        String passwordFingerprint = user.getPasswordHash() == null
                ? String.valueOf(Instant.now().toEpochMilli())
                : Integer.toHexString(user.getPasswordHash().hashCode());
        send(
                user.getEmail(),
                "TravelBox | " + getLocalizedText(locale, "Password updated", "Contrasena actualizada", "Password updated", "Senha atualizada"),
                content,
                "PASSWORD_CHANGED",
                "password-changed:" + safeId(user) + ":" + passwordFingerprint
        );
    }

    public void sendProfileUpdateVerification(
            User user,
            List<String> changedFields,
            String verificationCode,
            Instant expiresAt
    ) {
        if (user == null || verificationCode == null || verificationCode.isBlank()) {
            return;
        }
        String locale = getUserLocale(user);
        List<String> details = new ArrayList<>();
        details.add(getLocalizedText(locale, "Account email", "Correo de cuenta", "Account email", "Email da conta") + ": " + safeText(user.getEmail(), "-"));
        details.add(getLocalizedText(locale, "Changes detected", "Cambios detectados", "Changes detected", "Mudancas detectadas") + ": " + changedFieldsLine(changedFields));
        details.add(getLocalizedText(locale, "Code validity", "Validez del codigo", "Code validity", "Validade do codigo") + ": " + formatInstant(expiresAt) + " (" + getLocalizedText(locale, "Lima time", "hora Lima", "Lima time", "hora Lima") + ")");

        EmailContent content = renderTemplate(
                locale,
                getLocalizedText(locale, "Profile confirmation", "Confirmacion de perfil", "Profile confirmation", "Confirmacao do perfil"),
                getLocalizedText(locale, "Validate your profile changes", "Valida los cambios de tu perfil", "Validate your profile changes", "Valide as mudancas do seu perfil"),
                "Hola " + displayName(user) + ", confirma los cambios de tu perfil con este codigo.",
                details,
                verificationCode,
                expiresAt,
                getLocalizedText(locale, "Open profile", "Abrir perfil", "Open profile", "Abrir perfil"),
                profileRoute(),
                getLocalizedText(locale, "Until confirming code", "Hasta confirmar el codigo, la cuenta quedara pendiente", "Until confirming the code, the account will remain pending", "Ate confirmar o codigo, a conta permanecera pendente")
        );
        send(
                user.getEmail(),
                "TravelBox | " + getLocalizedText(locale, "Confirm your profile update", "Confirma la actualizacion de tu perfil", "Confirm your profile update", "Confirme a atualizacao do seu perfil"),
                content,
                "PROFILE_UPDATE_VERIFICATION",
                "profile-update-verification:" + safeId(user) + ":" + verificationCode.trim()
        );
    }

    public void sendProfileUpdatedNotice(User user, List<String> changedFields) {
        if (user == null || changedFields == null || changedFields.isEmpty()) {
            return;
        }
        String locale = getUserLocale(user);
        List<String> details = new ArrayList<>();
        details.add(getLocalizedText(locale, "Account email", "Correo de cuenta", "Account email", "Email da conta") + ": " + safeText(user.getEmail(), "-"));
        details.add(getLocalizedText(locale, "Updated fields", "Campos actualizados", "Updated fields", "Campos atualizados") + ": " + changedFieldsLine(changedFields));
        details.add(getLocalizedText(locale, "Date", "Fecha", "Date", "Data") + ": " + formatInstant(Instant.now()) + " (" + getLocalizedText(locale, "Lima time", "hora Lima", "Lima time", "hora Lima") + ")");

        EmailContent content = renderTemplate(
                locale,
                getLocalizedText(locale, "Profile updated", "Perfil actualizado", "Profile updated", "Perfil atualizado"),
                getLocalizedText(locale, "Your data was updated", "Tus datos fueron actualizados", "Your data was updated", "Seus dados foram atualizados"),
                "Hola " + displayName(user) + ", registramos cambios en tu perfil.",
                details,
                null,
                null,
                getLocalizedText(locale, "Review profile", "Revisar perfil", "Review profile", "Revisar perfil"),
                profileRoute(),
                getLocalizedText(locale, "If you do not recognize", "Si no reconoces esta actividad, cambia tu contrasena", "If you do not recognize this activity, change your password", "Se voce nao reconhece esta atividade, altere sua senha")
        );
        String fieldDigest = changedFields.stream()
                .map(this::normalize)
                .filter(value -> value != null)
                .collect(Collectors.joining(","));
        String updateFingerprint = (user.getUpdatedAt() == null
                ? String.valueOf(Instant.now().toEpochMilli())
                : String.valueOf(user.getUpdatedAt().toEpochMilli())) + ":" + fieldDigest.hashCode();
        send(
                user.getEmail(),
                "TravelBox | " + getLocalizedText(locale, "Profile update recorded", "Actualizacion de perfil registrada", "Profile update recorded", "Atualizacao de perfil registrada"),
                content,
                "PROFILE_UPDATED",
                "profile-updated:" + safeId(user) + ":" + updateFingerprint
        );
    }

    public void sendEmailChangeVerification(
            User user,
            String pendingEmail,
            String verificationCode,
            Instant expiresAt
    ) {
        if (user == null || pendingEmail == null || pendingEmail.isBlank() || verificationCode == null || verificationCode.isBlank()) {
            return;
        }
        String locale = getUserLocale(user);
        List<String> details = new ArrayList<>();
        details.add(getLocalizedText(locale, "New email", "Nuevo correo", "New email", "Novo email") + ": " + safeText(pendingEmail, "-"));
        details.add(getLocalizedText(locale, "Current email", "Correo actual", "Current email", "Email atual") + ": " + safeText(user.getEmail(), "-"));
        details.add(getLocalizedText(locale, "Valid until", "Validez", "Valid until", "Valido ate") + ": " + formatInstant(expiresAt) + " (" + getLocalizedText(locale, "Lima time", "hora Lima", "Lima time", "hora Lima") + ")");

        EmailContent content = renderTemplate(
                locale,
                getLocalizedText(locale, "Email change", "Cambio de correo", "Email change", "Mudanca de email"),
                getLocalizedText(locale, "Verify your new email", "Verifica tu nuevo correo", "Verify your new email", "Verifique seu novo email"),
                "Hola " + displayName(user) + ", solicitaste cambiar tu correo. Usa este codigo para confirmar el cambio.",
                details,
                verificationCode,
                expiresAt,
                getLocalizedText(locale, "Confirm change", "Confirmar cambio", "Confirm change", "Confirmar mudanca"),
                profileRoute(),
                getLocalizedText(locale, "If you did not request", "Si no solicitaste este cambio, ignora este mensaje", "If you did not request this change, ignore this message", "Se voce nao solicitou esta mudanca, ignore esta mensagem")
        );
        send(
                pendingEmail,
                "TravelBox | " + getLocalizedText(locale, "Code to change your email", "Codigo para cambiar tu correo", "Code to change your email", "Codigo para alterar seu email"),
                content,
                "EMAIL_CHANGE_VERIFICATION",
                "email-change:" + safeId(user) + ":" + verificationCode.trim()
        );
    }

    public void sendEmailChangeConfirmation(User user) {
        if (user == null) {
            return;
        }
        String locale = getUserLocale(user);
        List<String> details = new ArrayList<>();
        details.add(getLocalizedText(locale, "New email", "Nuevo correo", "New email", "Novo email") + ": " + safeText(user.getEmail(), "-"));
        details.add(getLocalizedText(locale, "Date", "Fecha", "Date", "Data") + ": " + formatInstant(Instant.now()) + " (" + getLocalizedText(locale, "Lima time", "hora Lima", "Lima time", "hora Lima") + ")");

        EmailContent content = renderTemplate(
                locale,
                getLocalizedText(locale, "Email updated", "Correo actualizado", "Email updated", "Email atualizado"),
                getLocalizedText(locale, "Your email was changed", "Tu correo fue cambiado", "Your email was changed", "Seu email foi alterado"),
                "Hola " + displayName(user) + ", confirmamos el cambio de tu correo electronico.",
                details,
                null,
                null,
                getLocalizedText(locale, "Go to my account", "Ir a mi cuenta", "Go to my account", "Ir para minha conta"),
                profileRoute(),
                getLocalizedText(locale, "If you did not make this change", "Si no realizaste este cambio, contacta soporte", "If you did not make this change, contact support", "Se voce nao fez esta mudanca, entre em contato com suporte")
        );
        send(
                user.getEmail(),
                "TravelBox | " + getLocalizedText(locale, "Email updated", "Correo electronico actualizado", "Email updated", "Email eletronico atualizado"),
                content,
                "EMAIL_CHANGE_CONFIRMED",
                "email-change-confirmed:" + safeId(user)
        );
    }

    private String getUserLocale(User user) {
        return user.getPreferredLanguage() != null ? user.getPreferredLanguage() : "es";
    }

    private String getLocalizedText(String locale, String spanish, String english, String portuguese) {
        return switch (locale.toLowerCase()) {
            case "en" -> english;
            case "pt" -> portuguese;
            default -> spanish;
        };
    }

    private String getLocalizedText(String locale, String key, String spanish, String english, String portuguese) {
        return getLocalizedText(locale, spanish, english, portuguese);
    }

    private String getLocalizedText(String locale, String key, String spanish, String english, String portuguese, String german, String french, String italian) {
        return switch (locale.toLowerCase()) {
            case "en" -> english;
            case "pt" -> portuguese;
            case "de" -> german != null ? german : spanish;
            case "fr" -> french != null ? french : spanish;
            case "it" -> italian != null ? italian : spanish;
            default -> spanish;
        };
    }

    private void send(
            String to,
            String subject,
            EmailContent content,
            String eventType,
            String dedupKey
    ) {
        if (normalize(to) == null || content == null) {
            return;
        }
        boolean queued = emailOutboxService.enqueue(
                to,
                subject,
                content.html(),
                content.text(),
                eventType,
                dedupKey
        );
        if (!queued) {
            log.info("Correo omitido por idempotencia o datos incompletos. eventType={}, dedupKey={}", eventType, dedupKey);
        }
    }

    private String safeId(User user) {
        if (user == null || user.getId() == null) {
            return "unknown";
        }
        return String.valueOf(user.getId());
    }

    private EmailContent renderTemplate(
            String locale,
            String badge,
            String title,
            String intro,
            List<String> detailLines,
            String code,
            Instant codeExpiresAt,
            String ctaLabel,
            String ctaUrl,
            String footerMessage
    ) {
        String safeBadge = escape(safeText(badge, ""));
        String safeTitle = escape(safeText(title, ""));
        String safeIntro = escape(safeText(intro, ""));
        String safeFooter = escape(safeText(footerMessage, ""));
        String detailsHtml = buildDetailsHtml(detailLines);
        String codeHtml = buildCodeHtml(code, codeExpiresAt);
        String ctaHtml = buildCtaHtml(ctaLabel, ctaUrl);
        String htmlLang = locale.toLowerCase().equals("en") ? "en" : locale.toLowerCase().equals("pt") ? "pt" : "es";

        String html = """
                <!doctype html>
                <html lang="%s">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>%s</title>
                </head>
                <body style="margin:0;padding:0;background:#F5FAFB;font-family:Segoe UI,Roboto,Helvetica,Arial,sans-serif;color:#0F172A;">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#F5FAFB;padding:24px 0;">
                    <tr>
                      <td align="center">
                        <table role="presentation" width="640" cellspacing="0" cellpadding="0" style="max-width:640px;background:#FFFFFF;border-radius:16px;overflow:hidden;box-shadow:0 12px 32px rgba(15,23,42,0.12);">
                          <tr>
                            <td style="padding:28px 32px;background:linear-gradient(130deg,#14324A,#1F6E8C,#4AA7AE);color:#FFFFFF;">
                              <div style="font-size:12px;letter-spacing:1px;text-transform:uppercase;opacity:0.88;">%s</div>
                              <h1 style="margin:10px 0 0;font-size:26px;line-height:1.24;font-weight:700;">%s</h1>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:28px 32px 8px;">
                              <p style="margin:0;font-size:16px;line-height:1.6;color:#1F2937;">%s</p>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:0 32px 4px;">
                              %s
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:4px 32px 4px;">
                              %s
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:12px 32px 6px;">
                              %s
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:8px 32px 28px;">
                              <p style="margin:0;font-size:13px;line-height:1.6;color:#475569;">%s</p>
                            </td>
                          </tr>
                        </table>
                        <p style="margin:14px 0 0;font-size:12px;color:#64748B;">%s</p>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(
                htmlLang,
                safeTitle,
                safeBadge,
                safeTitle,
                safeIntro,
                detailsHtml,
                codeHtml,
                ctaHtml,
                safeFooter,
                escape(brandName + " | Notificacion automatica")
        );

        String text = buildTextVersion(locale, title, intro, detailLines, code, codeExpiresAt, ctaLabel, ctaUrl, footerMessage);
        return new EmailContent(html, text);
    }

    private String buildDetailsHtml(List<String> detailLines) {
        if (detailLines == null || detailLines.isEmpty()) {
            return "";
        }
        StringBuilder rows = new StringBuilder();
        for (String line : detailLines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            rows.append("<li style=\"margin:0 0 8px;color:#334155;font-size:14px;line-height:1.5;\">")
                    .append(escape(line))
                    .append("</li>");
        }
        if (rows.isEmpty()) {
            return "";
        }
        return "<div style=\"background:#EEF6F9;border:1px solid #D8E8EE;border-radius:12px;padding:16px 18px;\">"
                + "<ul style=\"margin:0;padding-left:18px;\">" + rows + "</ul>"
                + "</div>";
    }

    private String buildCodeHtml(String code, Instant codeExpiresAt) {
        if (code == null || code.isBlank()) {
            return "";
        }
        String expiresLabel = codeExpiresAt == null ? "" : "Valido hasta " + formatInstant(codeExpiresAt) + " (Lima)";
        return "<div style=\"border-radius:12px;background:#F8FCFD;border:1px solid #CCE4EC;padding:16px 18px;text-align:center;\">"
                + "<div style=\"font-size:30px;letter-spacing:8px;font-weight:700;color:#14324A;\">"
                + escape(code)
                + "</div>"
                + "<div style=\"margin-top:8px;font-size:12px;color:#475569;\">"
                + escape(expiresLabel)
                + "</div>"
                + "</div>";
    }

    private String buildCtaHtml(String ctaLabel, String ctaUrl) {
        if (normalize(ctaLabel) == null || normalize(ctaUrl) == null) {
            return "";
        }
        return "<a href=\"" + escapeAttribute(ctaUrl) + "\" "
                + "style=\"display:inline-block;padding:12px 18px;border-radius:10px;background:#F29F05;"
                + "color:#1F2937;text-decoration:none;font-weight:700;font-size:14px;\">"
                + escape(ctaLabel)
                + "</a>";
    }

    private String buildTextVersion(
            String locale,
            String title,
            String intro,
            List<String> detailLines,
            String code,
            Instant codeExpiresAt,
            String ctaLabel,
            String ctaUrl,
            String footerMessage
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append(safeText(title, "Notificacion TravelBox")).append("\n\n");
        builder.append(safeText(intro, "")).append("\n");
        if (detailLines != null && !detailLines.isEmpty()) {
            builder.append("\n").append(getLocalizedText(locale, "Details", "Detalles", "Details", "Detalhes") + ":\n");
            for (String line : detailLines) {
                if (line != null && !line.isBlank()) {
                    builder.append("- ").append(line.trim()).append("\n");
                }
            }
        }
        if (code != null && !code.isBlank()) {
            builder.append("\n" + getLocalizedText(locale, "Code", "Codigo", "Code", "Codigo") + ": ").append(code.trim()).append("\n");
            if (codeExpiresAt != null) {
                builder.append(getLocalizedText(locale, "Valid until", "Validez", "Valid until", "Valido ate") + ": ").append(formatInstant(codeExpiresAt)).append(" (" + getLocalizedText(locale, "Lima time", "hora Lima", "Lima time", "hora Lima") + ")\n");
            }
        }
        if (normalize(ctaLabel) != null && normalize(ctaUrl) != null) {
            builder.append("\n").append(ctaLabel.trim()).append(": ").append(ctaUrl.trim()).append("\n");
        }
        if (footerMessage != null && !footerMessage.isBlank()) {
            builder.append("\n").append(footerMessage.trim()).append("\n");
        }
        builder.append("\n").append(brandName);
        return builder.toString();
    }

    private String reservationRoute(Long reservationId) {
        return joinFrontUrl("/reservation/" + reservationId);
    }

    private String feedbackRoute() {
        return joinFrontUrl("/profile?feedback=true");
    }

    private String passwordResetRoute() {
        return joinFrontUrl("/password-reset");
    }

    private String profileRoute() {
        return joinFrontUrl("/profile");
    }

    private String loginRoute() {
        return joinFrontUrl("/login");
    }

    private String joinFrontUrl(String path) {
        if (frontendBaseUrl == null || path == null) {
            return null;
        }
        String base = frontendBaseUrl.endsWith("/") ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1) : frontendBaseUrl;
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return base + normalizedPath;
    }

    private String changedFieldsLine(List<String> changedFields) {
        if (changedFields == null || changedFields.isEmpty()) {
            return "sin detalle";
        }
        return String.join(", ", changedFields);
    }

    private String displayName(User user) {
        if (user == null) {
            return "cliente";
        }
        if (normalize(user.getFirstName()) != null) {
            return user.getFirstName().trim();
        }
        if (normalize(user.getFullName()) != null) {
            return user.getFullName().trim();
        }
        return "cliente";
    }

    private String safeText(String value, String fallback) {
        return normalize(value) == null ? fallback : value.trim();
    }

    private String formatInstant(Instant instant) {
        if (instant == null) {
            return "-";
        }
        return DATE_TIME_FORMATTER.format(instant);
    }

    private String formatMoney(BigDecimal amount) {
        if (amount == null) {
            return "S/ 0.00";
        }
        NumberFormat formatter = NumberFormat.getCurrencyInstance(new Locale("es", "PE"));
        formatter.setCurrency(java.util.Currency.getInstance("PEN"));
        return formatter.format(amount);
    }

    private String escape(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value);
    }

    private String escapeAttribute(String value) {
        return escape(value).replace("\"", "&quot;");
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private record EmailContent(String html, String text) {
    }
}