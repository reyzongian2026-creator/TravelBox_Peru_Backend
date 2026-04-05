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
- Guia de consumo frontend Java:
  - `docs/java-frontend-consumption.md`
- Mapa central de variables/secrets (front + back):
  - `../VAULT_SECRETS_MAP.txt`

## Stack

- Java 21
- Spring Boot 3.5.11
- Spring Security + JWT access/refresh + RBAC
- Spring Data JPA + Flyway
- PostgreSQL (runtime) + H2 solo para pruebas automatizadas
- Actuator + correlation id

## Modulos implementados

- `auth`: login, refresh, logout, OAuth Google/Facebook y Microsoft Entra
- `profile`: perfil editable/completable + verificacion de correo
- `users`: roles `CLIENT`, `OPERATOR`, `COURIER`, `CITY_SUPERVISOR`, `ADMIN`, `SUPPORT`
- `geo`: ciudades, zonas, busqueda y sugerencias
- `warehouses`: busqueda, nearby GPS, detalle y CRUD admin
- `reservations`: creacion, consulta, cancelacion, QR y expiracion automatica
- `payments`: intents, confirmacion, status, history, caja, webhook Culqi
- `notifications`: bandeja in-app paginada + stream SSE en tiempo real por usuario
- `inventory`: check-in, checkout, evidencias
- `delivery`: ordenes de delivery
- `incidents`: apertura y resolucion
- `reports/admin`: dashboard
- `ops/qr-handoff`: escaneo QR, tagging de maleta, PIN presencial, aprobaciones de delivery

## Actualizacion 2026-03 (homologacion)

- Incidencias:
  - nuevo endpoint paginado `GET /api/v1/incidents/page`
  - filtros por `status`, `query` y `reservationId`
  - orden latest-first para carga mas rapida en frontend
- Pagos:
  - `GET /api/v1/payments/history` soporta filtro opcional `status`
  - repositorio y servicio actualizados para paginado y filtrado server-side
- Realtime:
  - el backend mantiene stream SSE por usuario para refresco operativo sin recarga manual
  - contratos homologados con frontend para vistas admin/operator/support/client

## Endpoints principales

- `POST /api/v1/auth/login`
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/entra/social`
- `GET /api/v1/auth/oauth/{provider}/start`
- `GET /api/v1/auth/oauth/{provider}/callback`
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
- `GET /api/v1/warehouses/{id}/image`
- `GET /api/v1/warehouses/availability/search?cityId=1&query=...&startAt=...&endAt=...`
- `POST /api/v1/reservations`
- `POST /api/v1/reservations/checkout`
- `POST /api/v1/reservations/assisted`
- `GET /api/v1/profile/me`
- `PATCH /api/v1/profile/me`
- `POST /api/v1/profile/me/photo`
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
- `GET /api/v1/payments/history` (admite `status=...`)
- `GET /api/v1/payments/cash/pending`
- `POST /api/v1/payments/cash/{paymentIntentId}/approve`
- `POST /api/v1/payments/cash/{paymentIntentId}/reject`
- `POST /api/v1/payments/{paymentIntentId}/refund`
- `POST /api/v1/payments/webhooks/culqi`
- `GET /api/v1/notifications/my`
- `GET /api/v1/notifications/stream`
- `GET /api/v1/notifications/events`
- `GET /api/v1/notifications/sse`
- `DELETE /api/v1/notifications/{id}`
- `DELETE /api/v1/notifications/my`
- `POST /api/v1/inventory/checkin`
- `POST /api/v1/inventory/checkout`
- `POST /api/v1/inventory/evidences`
- `POST /api/v1/inventory/evidences/upload`
- `GET /api/v1/files/{category}/{filename}`
- `POST /api/v1/delivery-orders`
- `GET /api/v1/incidents`
- `GET /api/v1/incidents/page`
- `POST /api/v1/incidents`
- `PATCH /api/v1/incidents/{id}/resolve`
- `POST /api/v1/ops/qr-handoff/scan`
- `GET /api/v1/ops/qr-handoff/reservations/{reservationId}`
- `POST /api/v1/ops/qr-handoff/reservations/{reservationId}/tag`
- `POST /api/v1/ops/qr-handoff/reservations/{reservationId}/store`
- `POST /api/v1/ops/qr-handoff/reservations/{reservationId}/store-with-photos`
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
- `GET /api/v1/admin/warehouses/registry`
- `POST /api/v1/admin/warehouses/{id}/photo`
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
- `PICKUP` aplica solo para reservas `CONFIRMED`.
- Se bloquean duplicados: no se permite otra orden activa del mismo tipo para la misma reserva.
- En profile `dev`, `GET /api/v1/geo/route` usa Google Routes API; si falla, usa fallback curvo local.

Tiempo real y audiencias por sede:
- `NotificationTopicBroker` publica eventos SSE tipo topico por usuario sobre `/api/v1/notifications/events`.
- Cada cambio operativo relevante publica notificacion y evento (reservas, pagos, QR/PIN, inventario, delivery, incidencias).
- `ADMIN` recibe visibilidad global.
- `OPERATOR`, `CITY_SUPERVISOR` y `COURIER` reciben solo eventos/notificaciones de sus sedes.
- `SUPPORT` por sede recibe incidencias de sus sedes.

Detalle operativo y fotos de equipaje:
- `GET /api/v1/reservations/{id}` devuelve `operationalDetail` (`bagTagId`, `bagUnits`, `pickupPin` segun rol, galeria de fotos check-in).
- Cliente ve `bagTagId` y `pickupPin` cuando exista; no ve fotos de equipaje.
- `ADMIN` y `OPERATOR`/`CITY_SUPERVISOR` con acceso a sede ven fotos de equipaje e ID maleta.
- `COURIER` con acceso a sede ve ID maleta y fotos, pero no `pickupPin`.

Auth cliente y limites de perfil:
- Clientes usan login local o social directo (`google` / `facebook`) contra el backend; usuarios internos usan correo corporativo y Microsoft Entra cuando aplique.
- Usuarios internos (`ADMIN`, `OPERATOR`, `CITY_SUPERVISOR`, `COURIER`, `SUPPORT`) los gestiona admin.
- Cliente puede cambiar `correo`, `telefono` y `documento` maximo 3 veces por campo.
- `COURIER` exige `vehiclePlate` al crearse o editarse desde admin.

Hardening de seguridad aplicado:
- Rate limit para endpoints sensibles de autenticacion.
- Security headers HTTP (CSP, HSTS, Referrer-Policy, Permissions-Policy, no-sniff).
- Validaciones mas estrictas para auth/profile.
- Respuestas de error sanitizadas.
- Flujo QR/delivery endurecido: identidad -> equipaje -> aprobacion -> PIN -> entrega; backend bloquea saltos de paso.

## Ejecucion local

Linux/macOS:

```bash
./mvnw spring-boot:run
```

Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

## Perfiles de despliegue

- `local`: usa `application-local.yml` (sin Key Vault runtime).
- `qa`: usa `application-qa.yml` (Key Vault opcional o fallback env).
- `prod`: usa `application-prod.yml` (Key Vault runtime).

Script recomendado (Windows) para arrancar por entorno:

```powershell
powershell -ExecutionPolicy Bypass -File ..\tools\run_backend_profile.ps1 -Profile local
powershell -ExecutionPolicy Bypass -File ..\tools\run_backend_profile.ps1 -Profile qa
powershell -ExecutionPolicy Bypass -File ..\tools\run_backend_profile.ps1 -Profile prod
```

Notas:
- En `prod`, el script exige `AZURE_CLIENT_ID`, `AZURE_CLIENT_SECRET` y `AZURE_TENANT_ID`.
- Para `qa`, puedes forzar Key Vault runtime con `-EnableKeyVaultRuntime`.

## Credenciales demo

- Admin: `admin@travelbox.pe` / `Admin123!`
- Operador: `operator@travelbox.pe` / `Operator123!`
- Operador extra: `operator.north@travelbox.pe` / `Operator123!`
- Operador multi-sede demo: `operator.demo.multisede@travelbox.pe` / `Operator123!`
- Courier: `courier@travelbox.pe` / `Courier123!`
- Courier extra: `courier.north@travelbox.pe` / `Courier123!`
- Courier multi-sede demo: `courier.demo.multisede@travelbox.pe` / `Courier123!`
- Soporte: `support@travelbox.pe` / `Support123!`
- Soporte multi-sede demo: `support.demo.multisede@travelbox.pe` / `Support123!`
- Supervisor demo: `supervisor.demo@travelbox.pe` / `Supervisor123!`
- Cliente: `client@travelbox.pe` / `Client123!`

## Configuracion frontend Java

- CORS:
  - `APP_CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:5173,http://localhost:8080`
- JWT:
  - `Authorization: Bearer <access_token>`
- Guia de consumo:
  - `docs/java-frontend-consumption.md`

## Pagos reales (Izipay)

- Activar: `APP_PAYMENT_PROVIDER=izipay`
- Para mantener Izipay habilitado pero operar solo efectivo: `APP_PAYMENTS_FORCE_CASH_ONLY=true`
- Configurar:
  - `APP_IZIPAY_MERCHANT_CODE`
  - `APP_IZIPAY_PUBLIC_KEY`
  - `APP_IZIPAY_HASH_KEY`
  - `APP_IZIPAY_API_BASE_URL` (ej: https://qa-api-pw.izipay.pe)
- Flujo: El backend genera una sesión y el frontend abre el SDK de Izipay.
- `POST /api/v1/reservations/checkout` evita reservas fantasma (rollback completo si falla pago).
- Confirmacion asincrona: webhook `POST /api/v1/payments/webhooks/izipay`.

## Correo transaccional (Exchange Online / Microsoft Graph)

- Proveedor recomendado y por defecto en prod: `APP_AUTH_EMAIL_PROVIDER=graph` y `APP_EMAIL_PROVIDER=graph`
- Variables requeridas:
  - `APP_EMAIL_FROM_ADDRESS=admin@inkavoy.pe`
  - `APP_EMAIL_FROM_NAME=Inkavoy`
  - `APP_EMAIL_GRAPH_TENANT_ID=<tenant-id>`
  - `APP_EMAIL_GRAPH_CLIENT_ID=<app-registration-client-id>`
  - `APP_EMAIL_GRAPH_CLIENT_SECRET=<app-registration-client-secret>`
- El backend usa Microsoft Graph para:
  - verificacion de correo
  - recuperacion de contraseña
  - notificaciones operativas por email
- En prod no se debe usar `mock` ni proveedores legacy.

## Reembolsos y cancelaciones

- `POST /api/v1/payments/{paymentIntentId}/refund` ejecuta reembolso y deja el pago en `REFUNDED`.
- Si la reserva tiene pago digital confirmado (`card`, `yape`, `plin`, `wallet`), cancelar directo devuelve `409 RESERVATION_REFUND_REQUIRED`.
- Comision configurable:
  - `APP_PAYMENT_REFUND_COMMISSION_GRACE_MINUTES`
  - `APP_PAYMENT_REFUND_COMMISSION_PERCENT_AFTER_GRACE`
  - `APP_PAYMENT_REFUND_MINIMUM_FEE`

## Fotos de almacenes

- Si admin sube foto por `POST /api/v1/admin/warehouses/{id}/photo`, frontend recibe `imageUrl`, `photoUrl`, `coverImageUrl`.
- Si no existe foto, backend expone `GET /api/v1/warehouses/{id}/image` con portada automatica por sede/ciudad.

## Auth social directo

- `GET /api/v1/auth/oauth/google/start` inicia OAuth web con Google.
- `GET /api/v1/auth/oauth/facebook/start` inicia OAuth web con Facebook.
- Los callbacks válidos en producción son:
  - `https://api.inkavoy.pe/api/v1/auth/oauth/google/callback`
  - `https://api.inkavoy.pe/api/v1/auth/oauth/facebook/callback`
- El frontend de producción es `https://www.inkavoy.pe`.
- Las cuentas se vinculan por email exacto.
- Si Facebook no devuelve un email utilizable, el backend rechaza el login.

## Ruteo real (Google Routes API)

Variables backend:
- `APP_ROUTING_PROVIDER=google`
- `APP_ROUTING_GOOGLE_API_KEY=<api-key-del-proyecto-google-maps>`
- opcional: `APP_ROUTING_GOOGLE_BASE_URL=https://routes.googleapis.com`
- opcional: `APP_ROUTING_GOOGLE_FIELD_MASK=routes.duration,routes.distanceMeters,routes.polyline.encodedPolyline`

Uso:
- El backend llama `POST /directions/v2:computeRoutes` de Google Maps Routes API.
- Si falta API key o falla proveedor externo, responde ruta `mock-curved` como contingencia.

Configuracion recomendada por entorno:
- `local/dev` (temporal): se puede dejar la API key sin restriccion de aplicacion para pruebas rapidas.
- `local/dev` (minimo): restringir la key al menos por API y permitir solo `Routes API`.
- `cloud/prod` (obligatorio): usar una key separada para backend, restringida por IP/NAT del servidor y por API solo `Routes API`.
- Si una key fue compartida o expuesta, rotarla y reemplazarla en variables de entorno.

## Secretos para cloud

- Fuente central de mapeo: `../VAULT_SECRETS_MAP.txt`
- En cloud usar Azure Key Vault para secretos operativos del backend y frontend.
- Rotar credenciales sensibles si estuvieron expuestas (DB/JWT/Google/Facebook/Graph/Culqi).

## Pruebas

```bash
./mvnw test
```

Resultado actual: `BUILD SUCCESS` con pruebas de auth, reservas, pagos (status/history/caja/webhook), notificaciones, inventory, disponibilidad GPS y contexto.
nibilidad GPS y contexto.
