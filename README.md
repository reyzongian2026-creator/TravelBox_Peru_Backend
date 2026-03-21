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

- `auth`: login, refresh, logout, firebase social
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
- `POST /api/v1/auth/firebase/social`
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
- Solo `CLIENT` usa Firebase para Google/Facebook; backend valida `idToken` y emite JWT TravelBox.
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

Cloud Run (desde raiz del workspace):

```powershell
powershell -ExecutionPolicy Bypass -File tools\deploy_cloudrun_backend.ps1 -Environment qa -ProjectId <project-id> -ServiceName <servicio-qa>
powershell -ExecutionPolicy Bypass -File tools\deploy_cloudrun_backend.ps1 -Environment prod -ProjectId <project-id> -ServiceName <servicio-prod>
```

Archivos de entorno para Cloud Run:
- `..\cloudrun-backend-env.qa.yaml`
- `..\cloudrun-backend-env.prod.yaml` (alias legado: `..\cloudrun-backend-env.yaml`)

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

## Pagos reales (Culqi)

- Activar: `APP_PAYMENT_PROVIDER=culqi`
- Para mantener Culqi habilitado pero operar solo efectivo: `APP_PAYMENTS_FORCE_CASH_ONLY=true`
- Configurar:
  - `APP_CULQI_SECRET_KEY`
  - `APP_CULQI_PUBLIC_KEY`
  - opcional `APP_CULQI_WEBHOOK_SECRET`
- Tarjeta/Yape: enviar `sourceTokenId` generado por Culqi Checkout.
- `POST /api/v1/reservations/checkout` evita reservas fantasma (rollback completo si falla pago).
- Plin/Wallet: backend crea orden y devuelve `nextAction.orderId` + `nextAction.publicKey`.
- Confirmacion asincrona: webhook `POST /api/v1/payments/webhooks/culqi`.

## Correo SMTP (Brevo / Gmail)

- Proveedor: `APP_AUTH_EMAIL_PROVIDER=smtp` y `APP_EMAIL_PROVIDER=smtp`
- Brevo (recomendado para evitar limites diarios de Gmail):
  - `APP_SMTP_HOST=smtp-relay.brevo.com`
  - `APP_SMTP_PORT=587`
  - `APP_SMTP_USERNAME=<usuario_smtp_brevo>`
  - `APP_SMTP_PASSWORD=<smtp_key_brevo>`
  - `APP_EMAIL_FROM_ADDRESS=<remitente_verificado_en_brevo>`
- Gmail (alternativa):
  - `APP_SMTP_HOST=smtp.gmail.com`
  - `APP_SMTP_PORT=587`
  - `APP_SMTP_USERNAME=<tu_correo_personal@gmail.com>`
  - `APP_SMTP_PASSWORD=<app_password_16_chars>`
  - `APP_EMAIL_FROM_ADDRESS=<tu_correo_personal@gmail.com>`
- En `profile=prod` el backend ahora falla en arranque si falta `spring.mail.username`, `spring.mail.password` o `app.email.from-address`.

## Reembolsos y cancelaciones

- `POST /api/v1/payments/{paymentIntentId}/refund` ejecuta reembolso y deja el pago en `REFUNDED`.
- Si la reserva tiene pago digital confirmado (`card`, `yape`, `plin`, `wallet`), cancelar directo devuelve `409 RESERVATION_REFUND_REQUIRED`.
- Comision configurable:
  - `APP_PAYMENT_REFUND_COMMISSION_GRACE_MINUTES`
  - `APP_PAYMENT_REFUND_COMMISSION_PERCENT_AFTER_GRACE`
  - `APP_PAYMENT_REFUND_MINIMUM_FEE`

## Fotos de almacenes

- Si admin sube foto por `POST /api/v1/admin/warehouses/{id}/photo`, frontend recibe `imageUrl`, `photoUrl`, `coverImageUrl`.
- Si Firebase Admin esta configurado, la foto se publica en Firebase Storage.
- Si no existe foto, backend expone `GET /api/v1/warehouses/{id}/image` con portada automatica por sede/ciudad.

## Firebase cliente

Variables backend:
- `APP_FIREBASE_ENABLED=true`
- `APP_FIREBASE_PROJECT_ID=<project-id>`
- `APP_FIREBASE_SERVICE_ACCOUNT_JSON=<json-en-una-linea>` (recomendado cloud)
- opcional: `APP_FIREBASE_SERVICE_ACCOUNT_FILE=<ruta-local-service-account.json>` (solo local/dev)
- opcional: `APP_FIREBASE_STORAGE_BUCKET=<bucket.appspot.com>`
- opcional: `APP_FIREBASE_CLIENT_PROFILE_COLLECTION=clientProfiles`
- opcional: `APP_FIREBASE_USER_MIGRATION_ENABLED=true`
- opcional: `APP_FIREBASE_USER_MIGRATION_FAIL_FAST=false`

Uso:
- `POST /api/v1/auth/firebase/social` recibe `idToken` emitido por Firebase Auth.
- `POST /api/v1/auth/register` sincroniza usuario en Firebase Auth y guarda/reutiliza `firebaseUid`.
- Backend espeja perfil cliente en Firestore (`clientProfiles`).
- Sin `APP_FIREBASE_STORAGE_BUCKET`, login social y espejo Firestore siguen operativos.
- Para web OAuth (Google/Facebook), agregar dominio frontend en `Firebase Console -> Authentication -> Settings -> Authorized domains`.
- Si aparece error `proveedor social aun no esta habilitado`, activar Google/Facebook en `Firebase Console -> Authentication -> Sign-in method` con sus credenciales OAuth.

## Ruteo real (Google Routes API)

Variables backend:
- `APP_ROUTING_PROVIDER=google`
- `APP_ROUTING_GOOGLE_API_KEY=<api-key-del-mismo-proyecto-firebase-gcp>`
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
- En cloud usar `APP_FIREBASE_SERVICE_ACCOUNT_JSON` (JSON completo desde vault).
- `APP_FIREBASE_SERVICE_ACCOUNT_FILE` solo aplica para local/dev con archivo fisico.
- Rotar credenciales sensibles si estuvieron expuestas (DB/JWT/Firebase/Culqi).

## Pruebas

```bash
./mvnw test
```

Resultado actual: `BUILD SUCCESS` con pruebas de auth, reservas, pagos (status/history/caja/webhook), notificaciones, inventory, disponibilidad GPS y contexto.
