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
- PostgreSQL (runtime) + H2 solo para pruebas automatizadas
- Actuator + correlation id

## Modulos implementados

- `auth`: login, refresh, logout
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
- `GET /api/v1/warehouses/{id}/image` (foto subida o portada generada por sede)
- `GET /api/v1/warehouses/availability/search?cityId=1&query=...&startAt=...&endAt=...`
- `POST /api/v1/reservations`
- `POST /api/v1/reservations/checkout` (crea reserva + procesa pago en una sola transaccion)
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
- `GET /api/v1/payments/history`
- `GET /api/v1/payments/cash/pending`
- `POST /api/v1/payments/cash/{paymentIntentId}/approve`
- `POST /api/v1/payments/cash/{paymentIntentId}/reject`
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
- `POST /api/v1/incidents`
- `PATCH /api/v1/incidents/{id}/resolve`
- `POST /api/v1/ops/qr-handoff/scan`
- `GET /api/v1/ops/qr-handoff/reservations/{reservationId}`
- `POST /api/v1/ops/qr-handoff/reservations/{reservationId}/tag`
- `POST /api/v1/ops/qr-handoff/reservations/{reservationId}/store`
- `POST /api/v1/ops/qr-handoff/reservations/{reservationId}/store-with-photos` (multipart; exige una foto por bulto al registrar ingreso)
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
- `POST /api/v1/admin/warehouses/{id}/photo` (subir/actualizar foto real del almacen)
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

Tiempo real y audiencias por sede:
- `NotificationTopicBroker` publica eventos SSE tipo topico por usuario sobre `/api/v1/notifications/events`.
- Cada cambio operativo relevante publica notificacion y evento: reserva creada/cancelada, pago confirmado, pago en caja pendiente, QR/PIN, inventario, delivery, incidencias.
- `ADMIN` recibe visibilidad global.
- `OPERATOR`, `CITY_SUPERVISOR` y `COURIER` reciben solo eventos/notificaciones de las sedes asignadas.
- `SUPPORT` por sede recibe incidencias de sus sedes asignadas.
- Los clientes SSE actualizan por evento, sin polling periodico; si el stream cae, el cliente reconecta el canal.

Detalle operativo y fotos de equipaje:
- `GET /api/v1/reservations/{id}` ahora devuelve `operationalDetail` con `bagTagId`, `bagUnits`, visibilidad de `pickupPin` segun rol y conteo/galeria de fotos del ingreso a almacen.
- Cliente puede ver `bagTagId` y `pickupPin` cuando exista; no ve fotos del equipaje.
- `ADMIN` y `OPERATOR`/`CITY_SUPERVISOR` con acceso a la sede ven fotos del equipaje e `ID maleta`.
- `COURIER` con acceso a la sede ve `ID maleta` y fotos del equipaje, pero no el `pickupPin`.
- Las fotos de equipaje quedan bloqueadas despues del check-in: el endpoint generico de evidencias ya no permite subir/modificar `CHECKIN_BAG_PHOTO` fuera del ingreso a almacen.

Auth cliente y limites de perfil:
- Solo `CLIENT` usa Firebase para Google/Facebook; el backend valida `idToken` y luego emite el JWT propio de TravelBox.
- Los usuarios internos (`ADMIN`, `OPERATOR`, `CITY_SUPERVISOR`, `COURIER`, `SUPPORT`) quedan administrados por admin y no pueden editar su propia ficha desde `/profile`.
- El cliente puede cambiar `correo`, `telefono` y `documento` como maximo 3 veces por campo; cada cambio genera notificacion in-app con el saldo restante.
- `COURIER` exige `vehiclePlate` al crearse o editarse desde admin.

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
- Operador multi-sede demo: `operator.demo.multisede@travelbox.pe` / `Operator123!`
- Courier: `courier@travelbox.pe` / `Courier123!`
- Courier extra: `courier.north@travelbox.pe` / `Courier123!`
- Courier multi-sede demo: `courier.demo.multisede@travelbox.pe` / `Courier123!`
- Soporte: `support@travelbox.pe` / `Support123!`
- Soporte multi-sede demo: `support.demo.multisede@travelbox.pe` / `Support123!`
- Supervisor demo: `supervisor.demo@travelbox.pe` / `Supervisor123!`
- Cliente: `client@travelbox.pe` / `Client123!`

Credenciales por sede generadas automaticamente al arrancar:
- Operadores por sede: `operator.<suffix>@travelbox.pe` / `Operator123!`
- Couriers por sede: `courier.<suffix>@travelbox.pe` / `Courier123!`
- Soporte por sede: `support.<suffix>@travelbox.pe` / `Support123!`
- Usuarios adicionales por sede o multi-sede pueden crearse despues desde `/admin/users` asignando `warehouseIds` a `OPERATOR`, `CITY_SUPERVISOR`, `COURIER` o `SUPPORT`.
- Demo multi-sede:
  - `operator.demo.multisede@travelbox.pe` -> Miraflores + La Molina
  - `courier.demo.multisede@travelbox.pe` -> Miraflores + La Molina
  - `support.demo.multisede@travelbox.pe` -> Miraflores + La Molina
  - `supervisor.demo@travelbox.pe` -> Lima Centro + Miraflores + Barranco

Sufijos por sede:
- `miraflores` -> TravelBox Miraflores
- `barranco` -> TravelBox Barranco
- `lima.centro` -> TravelBox Lima Centro
- `la.molina` -> TravelBox La Molina
- `cusco.plaza` -> TravelBox Cusco Plaza
- `arequipa.yanahuara` -> TravelBox Arequipa Yanahuara
- `huacachina` -> TravelBox Huacachina
- `puno.terminal` -> TravelBox Puno Terminal
- `paracas.muelle` -> TravelBox Paracas Muelle
- `nazca.lines` -> TravelBox Nazca Lines
- `trujillo.centro` -> TravelBox Trujillo Centro
- `piura.plaza` -> TravelBox Piura Plaza
- `mancora.beach` -> TravelBox Mancora Beach

Ejemplo de una sede seed:
- Operador Puno: `operator.puno.terminal@travelbox.pe` / `Operator123!`
- Courier Puno: `courier.puno.terminal@travelbox.pe` / `Courier123!`
- Soporte Puno: `support.puno.terminal@travelbox.pe` / `Support123!`

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
- `POST /api/v1/reservations/checkout` evita reservas "fantasma": si Culqi rechaza el cobro o falta `sourceTokenId`, la reserva completa hace rollback.
- Plin/Wallet: backend crea orden y devuelve `nextAction.orderId` + `nextAction.publicKey`.
- Confirmacion asincrona: webhook `POST /api/v1/payments/webhooks/culqi`.

## Fotos de almacenes

- Si el admin sube foto por `POST /api/v1/admin/warehouses/{id}/photo`, el frontend recibe esa imagen en `imageUrl`, `photoUrl` y `coverImageUrl`.
- Si Firebase Admin esta configurado, la foto se publica en Firebase Storage y el frontend consume el link publico directo.
- Si no existe foto subida, el backend expone `GET /api/v1/warehouses/{id}/image` y genera una portada automatica distinta por sede/ciudad.
- Si Firebase Storage no esta disponible, TravelBox conserva la portada automatica por sede y no depende de archivos locales.

## Firebase cliente

Variables backend:
- `APP_FIREBASE_ENABLED=true`
- `APP_FIREBASE_PROJECT_ID=<project-id>`
- `APP_FIREBASE_SERVICE_ACCOUNT_FILE=<ruta-del-service-account.json>`
  o `APP_FIREBASE_SERVICE_ACCOUNT_JSON=<json-en-una-linea>`
- opcional: `APP_FIREBASE_STORAGE_BUCKET=<bucket.appspot.com>`
- opcional: `APP_FIREBASE_CLIENT_PROFILE_COLLECTION=clientProfiles`
- opcional: `APP_FIREBASE_USER_MIGRATION_ENABLED=true`
- opcional: `APP_FIREBASE_USER_MIGRATION_FAIL_FAST=false`

Uso:
- `POST /api/v1/auth/firebase/social` recibe el `idToken` emitido por Firebase Auth desde Google/Facebook.
- `POST /api/v1/auth/register` sincroniza el usuario en Firebase Auth y guarda/reutiliza `firebaseUid`.
- El backend espeja el perfil cliente en Firestore (`clientProfiles`) para no mover reservas, pagos y operaciones fuera de la base transaccional actual.
- En el arranque, si Firebase esta habilitado, se ejecuta una migracion incremental de usuarios locales hacia Firebase Auth.
- Sin `APP_FIREBASE_STORAGE_BUCKET`, el login social cliente y el espejo en Firestore siguen funcionando; las fotos quedan con imagen por defecto.
- `POST /api/v1/profile/me/photo` y `POST /api/v1/admin/warehouses/{id}/photo` solo quedan habilitados cuando existe bucket de Firebase Storage.

## Pruebas

```bash
./mvnw test
```

Resultado actual: `BUILD SUCCESS` con pruebas de auth, reservas, pagos (status/history/caja/webhook), notificaciones, inventory, disponibilidad GPS y contexto.
