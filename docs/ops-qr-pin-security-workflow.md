# Flujo QR/PIN y Hardening de Seguridad

## Objetivo
Este modulo operacional cubre el flujo presencial y delivery para:
- validar cliente por QR de reserva,
- generar/pegar QR de maleta (bag tag),
- custodiar equipaje en almacen,
- cerrar entrega con PIN,
- exigir aprobacion de operador/admin para delivery.

## Endpoints operativos
- `POST /api/v1/ops/qr-handoff/scan`
- `GET /api/v1/ops/qr-handoff/reservations/{reservationId}`
- `POST /api/v1/ops/qr-handoff/reservations/{reservationId}/tag`
- `POST /api/v1/ops/qr-handoff/reservations/{reservationId}/store`
- `POST /api/v1/ops/qr-handoff/reservations/{reservationId}/ready-for-pickup`
- `POST /api/v1/ops/qr-handoff/reservations/{reservationId}/pickup/confirm`
- `PATCH /api/v1/ops/qr-handoff/reservations/{reservationId}/delivery/identity`
- `PATCH /api/v1/ops/qr-handoff/reservations/{reservationId}/delivery/luggage`
- `POST /api/v1/ops/qr-handoff/reservations/{reservationId}/delivery/request-approval`
- `GET /api/v1/ops/qr-handoff/approvals`
- `POST /api/v1/ops/qr-handoff/approvals/{approvalId}/approve`
- `POST /api/v1/ops/qr-handoff/approvals/{approvalId}/reject`
- `POST /api/v1/ops/qr-handoff/reservations/{reservationId}/delivery/complete`

## Roles
- `OPERATOR`, `ADMIN`, `SUPPORT`, `CITY_SUPERVISOR`: control completo de handoff.
- `COURIER`: validaciones de identidad/maleta y cierre delivery, solo cuando la reserva pertenece a un delivery asignado al courier.

## Persistencia
Se agregan tablas:
- `qr_handoff_cases` (estado QR/PIN por reserva).
- `qr_handoff_approvals` (solicitudes/aprobaciones de entrega).

Migracion: `V9__ops_qr_handoff.sql`.

## Seguridad OWASP aplicada
- **Rate limiting** en auth (`/auth/login`, `/auth/register`, `/auth/refresh`, `/auth/verify-email`, `/auth/resend-verification`).
- **Headers de seguridad**: CSP, HSTS, Referrer-Policy, Permissions-Policy, X-Content-Type-Options.
- **Errores sanitizados**: sin fuga de detalles internos en respuestas de error genericas.
- **Validaciones reforzadas**:
  - password fuerte en registro,
  - telefono en formato internacional E.164,
  - idiomas permitidos (`es/en/de/fr/it/pt`),
  - restricciones mas fuertes en payload de perfil.
- **PIN seguro**:
  - almacenamiento hash (`BCrypt`),
  - expiracion configurable,
  - bloqueo temporal por intentos fallidos.

## Configuracion relevante
Variables:
- `APP_AUTH_RATE_LIMIT_ENABLED`
- `APP_AUTH_RATE_LIMIT_MAX_REQUESTS`
- `APP_AUTH_RATE_LIMIT_WINDOW_SECONDS`
- `APP_AUTH_RATE_LIMIT_BLOCK_SECONDS`
- `APP_OPS_QR_PIN_EXPIRY_MINUTES`
- `APP_OPS_QR_MAX_PIN_ATTEMPTS`
- `APP_OPS_QR_PIN_LOCK_SECONDS`
- `APP_OPS_QR_BAG_TAG_PREFIX`
