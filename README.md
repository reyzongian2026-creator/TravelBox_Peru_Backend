# TravelBox Peru Backend (MVP)

Backend para plataforma de almacenamiento turistico, construido en Java 21 + Spring Boot, con arquitectura modular por dominios.

## Documentacion principal

- Blueprint funcional y tecnico completo:
  - `docs/travelbox-platform-blueprint.md`
- Manual de simulacion operativa paso a paso:
  - `docs/operation-simulation-manual.md`
- Manual de casuisticas por sede y roles:
  - `docs/operations-casuistics-sede-manual.md`
- Flujo QR/PIN operativo y seguridad:
  - `docs/ops-qr-pin-security-workflow.md`

## Stack

- Java 21
- Spring Boot 3.5.11
- Spring Security + JWT access/refresh + RBAC
- Spring Data JPA + Flyway
- H2 (dev/test), PostgreSQL (qa/prod)
- Actuator + correlation id

## Modulos implementados

- `auth`: login, refresh, logout
- `profile`: perfil editable/completable + verificacion de correo
- `users`: roles `CLIENT`, `OPERATOR`, `COURIER`, `CITY_SUPERVISOR`, `ADMIN`, `SUPPORT`
- `geo`: ciudades, zonas, busqueda y sugerencias
- `warehouses`: busqueda, nearby GPS, detalle y CRUD admin
- `reservations`: creacion, consulta, cancelacion, QR y expiracion automatica
- `payments`: intents, confirmacion, status, history, caja, webhook Culqi
- `notifications`: bandeja in-app paginada
- `inventory`: check-in, checkout, evidencias
- `delivery`: ordenes de delivery
- `incidents`: apertura y resolucion
- `reports/admin`: dashboard
- `ops/qr-handoff`: escaneo QR, tagging de maleta, PIN presencial, aprobaciones de delivery

## Endpoints principales

- `POST /api/v1/auth/login`
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`
- `GET /api/v1/geo/cities`
- `GET /api/v1/geo/zones?cityId=1`
- `GET /api/v1/geo/search?query=...`
- `GET /api/v1/geo/route?originLat=...&originLng=...&destinationLat=...&destinationLng=...`
- `GET /api/v1/geo/warehouses/nearby?lat=...&lng=...`
- `GET /api/v1/warehouses/search?cityId=1&query=miraflores`
- `GET /api/v1/warehouses/nearby?latitude=...&longitude=...`
- `GET /api/v1/warehouses/{id}/availability?startAt=...&endAt=...`
- `GET /api/v1/warehouses/availability/search?cityId=1&query=...&startAt=...&endAt=...`
- `POST /api/v1/reservations`
- `POST /api/v1/reservations/assisted`
- `GET /api/v1/reservations`
- `GET /api/v1/reservations/page`
- `PATCH /api/v1/reservations/{id}/cancel`
- `GET /api/v1/reservations/{id}/qr`
- `POST /api/v1/payments/intents`
- `POST /api/v1/payments/intent` (alias)
- `POST /api/v1/payments/confirm`
- `POST /api/v1/payments/checkout` (alias frontend)
- `POST /api/v1/payments/process` (alias)
- `GET /api/v1/payments/status`
- `GET /api/v1/payments/history`
- `GET /api/v1/payments/cash/pending`
- `POST /api/v1/payments/cash/{paymentIntentId}/approve`
- `POST /api/v1/payments/cash/{paymentIntentId}/reject`
- `POST /api/v1/payments/webhooks/culqi`
- `GET /api/v1/notifications/my`
- `GET /api/v1/notifications/stream`
- `POST /api/v1/inventory/checkin`
- `POST /api/v1/inventory/checkout`
- `POST /api/v1/inventory/evidences`
- `POST /api/v1/inventory/evidences/upload`
- `GET /api/v1/files/{category}/{filename}`
- `POST /api/v1/delivery-orders`
- `POST /api/v1/incidents`
- `PATCH /api/v1/incidents/{id}/resolve`
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
- `GET /api/v1/admin/dashboard`
- `GET|POST|PUT|DELETE /api/v1/admin/warehouses`
- `GET|POST|PUT|DELETE /api/v1/admin/almacenes` (alias)
- `GET /api/v1/admin/warehouses/registry` (tablero de registros)
- `GET|POST|PUT|DELETE /api/v1/admin/users`
- `PATCH /api/v1/admin/users/{id}/roles`
- `PATCH /api/v1/admin/users/{id}/active`
- `PATCH /api/v1/admin/users/{id}/password`

Permisos operativos homologados con frontend:
- Dashboard/CRUD de almacenes: solo `ADMIN`
- Inventory (`checkin/checkout/evidences`): `OPERATOR`, `CITY_SUPERVISOR`, `ADMIN`
- Incidents (open): `CLIENT`, `OPERATOR`, `CITY_SUPERVISOR`, `ADMIN`, `SUPPORT`

Notas logisticas:
- `POST /api/v1/delivery-orders` soporta `type=DELIVERY` y `type=PICKUP`.
- `DELIVERY` aplica para reservas `STORED` o `READY_FOR_PICKUP`.
- `PICKUP` aplica para reservas `CONFIRMED` o `CHECKIN_PENDING`.
- En profile `dev`, `GET /api/v1/geo/route` intenta OSRM; si falla, usa fallback curvo local.

Hardening de seguridad aplicado:
- Rate limit para endpoints sensibles de autenticacion.
- Security headers HTTP (CSP, HSTS, Referrer-Policy, Permissions-Policy, no-sniff).
- Validaciones mas estrictas para auth/profile (idioma, telefono internacional, password fuerte).
- Respuestas de error sanitizadas (sin filtrar clases internas).

## Ejecucion local

Linux/macOS:

```bash
./mvnw spring-boot:run
```

Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

## Credenciales demo

- Admin: `admin@travelbox.pe` / `Admin123!`
- Operador: `operator@travelbox.pe` / `Operator123!`
- Operador extra: `operator.north@travelbox.pe` / `Operator123!`
- Courier: `courier@travelbox.pe` / `Courier123!`
- Courier extra: `courier.north@travelbox.pe` / `Courier123!`
- Soporte: `support@travelbox.pe` / `Support123!`
- Cliente: `client@travelbox.pe` / `Client123!`

## Configuracion frontend Java

- CORS:
  - `APP_CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:5173,http://localhost:8080`
- JWT:
  - `Authorization: Bearer <access_token>`
- Guia de consumo:
  - `docs/java-frontend-consumption.md`

## Pagos reales (Culqi)

- Activar: `APP_PAYMENT_PROVIDER=culqi`
- Configurar:
  - `APP_CULQI_SECRET_KEY`
  - `APP_CULQI_PUBLIC_KEY`
  - opcional `APP_CULQI_WEBHOOK_SECRET`
- Tarjeta/Yape: enviar `sourceTokenId` generado por Culqi Checkout.
- Plin/Wallet: backend crea orden y devuelve `nextAction.orderId` + `nextAction.publicKey`.
- Confirmacion asincrona: webhook `POST /api/v1/payments/webhooks/culqi`.

## Pruebas

```bash
./mvnw test
```

Resultado actual: `BUILD SUCCESS` con pruebas de auth, reservas, pagos (status/history/caja/webhook), notificaciones, inventory, disponibilidad GPS y contexto.
