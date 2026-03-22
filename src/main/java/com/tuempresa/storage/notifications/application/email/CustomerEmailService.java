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
        List<String> details = new ArrayList<>();
        details.add("Reserva: #" + reservation.getId());
        details.add("Sede: " + safeText(reservation.getWarehouse().getName(), "-"));
        details.add("Inicio: " + formatInstant(reservation.getStartAt()));
        details.add("Fin: " + formatInstant(reservation.getEndAt()));
        details.add("Total estimado: " + formatMoney(reservation.getTotalPrice()));

        EmailContent content = renderTemplate(
                "Reserva registrada",
                "Tu reserva fue registrada correctamente",
                "Hola " + displayName(user) + ", ya registramos tu reserva en " + brandName + ".",
                details,
                null,
                null,
                "Ver mi reserva",
                reservationRoute(reservation.getId()),
                "Completa el pago para activar el servicio y evitar vencimientos automaticos."
        );
        send(
                user.getEmail(),
                "TravelBox | Reserva registrada #" + reservation.getId(),
                content,
                "RESERVATION_CREATED",
                "reservation-created:" + reservation.getId()
        );
    }

    public void sendPaymentConfirmed(User user, Reservation reservation, String paymentMethod) {
        if (user == null || reservation == null) {
            return;
        }
        List<String> details = new ArrayList<>();
        details.add("Reserva: #" + reservation.getId());
        details.add("Monto pagado: " + formatMoney(reservation.getTotalPrice()));
        details.add("Metodo: " + safeText(paymentMethod, "online"));
        details.add("Estado: Pago confirmado");

        EmailContent content = renderTemplate(
                "Pago aprobado",
                "Recibimos tu pago",
                "Hola " + displayName(user) + ", confirmamos el pago de tu reserva.",
                details,
                null,
                null,
                "Ver detalle",
                reservationRoute(reservation.getId()),
                "Gracias por confiar en " + brandName + "."
        );
        send(
                user.getEmail(),
                "TravelBox | Pago confirmado #" + reservation.getId(),
                content,
                "PAYMENT_CONFIRMED",
                "payment-confirmed:" + reservation.getId()
        );
    }

    public void sendPickupThankYou(User user, Reservation reservation) {
        if (user == null || reservation == null) {
            return;
        }
        List<String> details = new ArrayList<>();
        details.add("Reserva: #" + reservation.getId());
        details.add("Sede: " + safeText(reservation.getWarehouse().getName(), "-"));
        details.add("Estado: Servicio completado");

        EmailContent content = renderTemplate(
                "Gracias por tu recojo",
                "Tu servicio fue completado",
                "Hola " + displayName(user) + ", tu equipaje ya fue entregado correctamente.",
                details,
                null,
                null,
                "Calificar experiencia",
                feedbackRoute(),
                "Gracias por elegir " + brandName + ". Te esperamos pronto."
        );
        send(
                user.getEmail(),
                "TravelBox | Gracias por tu confianza",
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
        List<String> details = new ArrayList<>();
        details.add("Correo: " + safeText(user.getEmail(), "-"));
        details.add("Validez: " + formatInstant(expiresAt) + " (hora Lima)");
        if (verificationReason != null && !verificationReason.isBlank()) {
            details.add("Motivo: " + verificationReason.trim());
        }

        EmailContent content = renderTemplate(
                "Verificacion requerida",
                "Confirma tu correo",
                "Hola " + displayName(user) + ", usa este codigo para validar tu correo.",
                details,
                verificationCode,
                expiresAt,
                "Ir al inicio de sesion",
                loginRoute(),
                "Si no solicitaste esta accion, ignora este mensaje."
        );
        send(
                user.getEmail(),
                "TravelBox | Codigo de verificacion",
                content,
                "EMAIL_VERIFICATION_CODE",
                "email-verification:" + safeId(user) + ":" + verificationCode.trim()
        );
    }

    public void sendPasswordResetCode(User user, String resetCode, Instant expiresAt) {
        if (user == null || resetCode == null || resetCode.isBlank()) {
            return;
        }
        List<String> details = new ArrayList<>();
        details.add("Correo: " + safeText(user.getEmail(), "-"));
        details.add("Validez: " + formatInstant(expiresAt) + " (hora Lima)");

        EmailContent content = renderTemplate(
                "Recuperacion de contrasena",
                "Restablece tu contrasena",
                "Hola " + displayName(user) + ", usa este codigo para recuperar el acceso.",
                details,
                resetCode,
                expiresAt,
                "Abrir recuperacion",
                passwordResetRoute(),
                "Si no solicitaste el cambio, protege tu cuenta y no compartas este codigo."
        );
        send(
                user.getEmail(),
                "TravelBox | Codigo para restablecer contrasena",
                content,
                "PASSWORD_RESET_CODE",
                "password-reset:" + safeId(user) + ":" + resetCode.trim()
        );
    }

    public void sendPasswordChangedConfirmation(User user) {
        if (user == null) {
            return;
        }
        List<String> details = new ArrayList<>();
        details.add("Correo: " + safeText(user.getEmail(), "-"));
        details.add("Fecha: " + formatInstant(Instant.now()) + " (hora Lima)");

        EmailContent content = renderTemplate(
                "Contrasena actualizada",
                "Tu contrasena fue cambiada",
                "Hola " + displayName(user) + ", registramos un cambio de contrasena exitoso.",
                details,
                null,
                null,
                "Ingresar a mi cuenta",
                loginRoute(),
                "Si no fuiste tu, contacta soporte inmediatamente."
        );
        String passwordFingerprint = user.getPasswordHash() == null
                ? String.valueOf(Instant.now().toEpochMilli())
                : Integer.toHexString(user.getPasswordHash().hashCode());
        send(
                user.getEmail(),
                "TravelBox | Contrasena actualizada",
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
        List<String> details = new ArrayList<>();
        details.add("Correo de cuenta: " + safeText(user.getEmail(), "-"));
        details.add("Cambios detectados: " + changedFieldsLine(changedFields));
        details.add("Validez del codigo: " + formatInstant(expiresAt) + " (hora Lima)");

        EmailContent content = renderTemplate(
                "Confirmacion de perfil",
                "Valida los cambios de tu perfil",
                "Hola " + displayName(user) + ", confirma los cambios de tu perfil con este codigo.",
                details,
                verificationCode,
                expiresAt,
                "Abrir perfil",
                profileRoute(),
                "Hasta confirmar el codigo, la cuenta quedara en estado pendiente de verificacion."
        );
        send(
                user.getEmail(),
                "TravelBox | Confirma la actualizacion de tu perfil",
                content,
                "PROFILE_UPDATE_VERIFICATION",
                "profile-update-verification:" + safeId(user) + ":" + verificationCode.trim()
        );
    }

    public void sendProfileUpdatedNotice(User user, List<String> changedFields) {
        if (user == null || changedFields == null || changedFields.isEmpty()) {
            return;
        }
        List<String> details = new ArrayList<>();
        details.add("Correo de cuenta: " + safeText(user.getEmail(), "-"));
        details.add("Campos actualizados: " + changedFieldsLine(changedFields));
        details.add("Fecha: " + formatInstant(Instant.now()) + " (hora Lima)");

        EmailContent content = renderTemplate(
                "Perfil actualizado",
                "Tus datos fueron actualizados",
                "Hola " + displayName(user) + ", registramos cambios en tu perfil.",
                details,
                null,
                null,
                "Revisar perfil",
                profileRoute(),
                "Si no reconoces esta actividad, cambia tu contrasena y contacta soporte."
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
                "TravelBox | Actualizacion de perfil registrada",
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
        List<String> details = new ArrayList<>();
        details.add("Nuevo correo: " + safeText(pendingEmail, "-"));
        details.add("Correo actual: " + safeText(user.getEmail(), "-"));
        details.add("Validez: " + formatInstant(expiresAt) + " (hora Lima)");

        EmailContent content = renderTemplate(
                "Cambio de correo",
                "Verifica tu nuevo correo",
                "Hola " + displayName(user) + ", solicitaste cambiar tu correo. Usa este codigo para confirmar el cambio.",
                details,
                verificationCode,
                expiresAt,
                "Confirmar cambio",
                profileRoute(),
                "Si no solicitaste este cambio, ignora este mensaje y considera cambiar tu contrasena."
        );
        send(
                pendingEmail,
                "TravelBox | Codigo para cambiar tu correo",
                content,
                "EMAIL_CHANGE_VERIFICATION",
                "email-change:" + safeId(user) + ":" + verificationCode.trim()
        );
    }

    public void sendEmailChangeConfirmation(User user) {
        if (user == null) {
            return;
        }
        List<String> details = new ArrayList<>();
        details.add("Nuevo correo: " + safeText(user.getEmail(), "-"));
        details.add("Fecha: " + formatInstant(Instant.now()) + " (hora Lima)");

        EmailContent content = renderTemplate(
                "Correo actualizado",
                "Tu correo fue cambiado",
                "Hola " + displayName(user) + ", confirmamos el cambio de tu correo electronico.",
                details,
                null,
                null,
                "Ir a mi cuenta",
                profileRoute(),
                "Si no realizaste este cambio, contacta soporte inmediatamente."
        );
        send(
                user.getEmail(),
                "TravelBox | Correo electronico actualizado",
                content,
                "EMAIL_CHANGE_CONFIRMED",
                "email-change-confirmed:" + safeId(user)
        );
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

        String html = """
                <!doctype html>
                <html lang="es">
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

        String text = buildTextVersion(title, intro, detailLines, code, codeExpiresAt, ctaLabel, ctaUrl, footerMessage);
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
            builder.append("\nDetalles:\n");
            for (String line : detailLines) {
                if (line != null && !line.isBlank()) {
                    builder.append("- ").append(line.trim()).append("\n");
                }
            }
        }
        if (code != null && !code.isBlank()) {
            builder.append("\nCodigo: ").append(code.trim()).append("\n");
            if (codeExpiresAt != null) {
                builder.append("Validez: ").append(formatInstant(codeExpiresAt)).append(" (hora Lima)\n");
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
