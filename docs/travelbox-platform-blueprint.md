# TravelBox Peru - Blueprint funcional y tecnico

## Estado real del repositorio al 2026-03-14

- Backend Spring Boot validado con `.\mvnw.cmd test`: `37` pruebas OK.
- Frontend Flutter validado con `flutter analyze`, `flutter test` y `flutter build web --release`: OK.
- En backend ya existen `auth`, `profile`, `users`, `geo`, `warehouses`, `reservations`, `payments`, `delivery`, `incidents`, `notifications`, `reports`, `files`.
- En frontend ya existen login/registro, verificacion de correo, perfil editable/completable, discovery mapa/lista, reserva, checkout, tracking, incidencias y vistas admin/operativas.
- Izipay real ya tiene adaptador backend Java en `payments.infrastructure.out.gateway.IzipayGatewayClient`.
- Desde este turno el flujo real queda preparado para `REQUIRES_3DS_AUTH` en lugar de tratar ese caso como rechazo definitivo.
- Plataformas validadas en esta maquina: backend Java, Flutter web.
- Plataformas no validadas por toolchain local:
  - Android: falta Android SDK.
  - Windows desktop: falta Visual Studio con C++.
  - iOS: no se puede compilar desde Windows.

## 1. Resumen ejecutivo

TravelBox Peru debe operar como una plataforma multiproducto:

- App cliente Flutter para Android, iOS y Web.
- Backoffice Flutter Web para administracion y operacion.
- Backend Java 21 + Spring Boot como monolito modular con hexagonal interna.
- Integracion de pagos con Izipay usando backend Java, frontend tokenizando con componentes oficiales de Izipay cuando se activen llaves reales.
- Modo local/mock desde el dia 1 para registro, perfil, mapa, reservas, tracking, notificaciones y pagos.

La estrategia correcta para este repositorio es:

- Mantener un monolito modular porque el MVP todavia requiere mucha coordinacion transaccional entre reservas, pagos, tracking y operacion.
- Mantener un solo repo Flutter para la experiencia cliente y el backoffice del MVP, con guards de rol y despliegues separados por flavor/entrypoint.
- Mantener todas las integraciones externas detras de contratos de adaptador para no acoplar el dominio a Izipay, Firebase, correo ni mapas.
- Tratar admin y operativo como producto en espanol fijo.
- Tratar cliente como experiencia internacionalizable desde el MVP con `es` y `en`.

## 2. Alcance funcional completo

### Cliente/turista

Disponible para Android, iOS y Web:

- Registro por correo con password y confirmacion.
- Verificacion de correo.
- Pantalla de completar perfil cuando falte informacion obligatoria.
- Edicion de perfil, foto local y datos sensibles con revalidacion.
- Seleccion de nacionalidad.
- Preferencia de idioma.
- Home de descubrimiento con mapa y lista.
- Busqueda de almacenes cercanos.
- Zoom, desplazamiento y seleccion de marcador.
- Detalle de almacen con horario, precio y disponibilidad.
- Reserva por horas o dias.
- Pago con tarjeta e Izipay Checkout.
- Flujo Yape/Plin a traves de Izipay.
- Estado de reserva, QR/codigo y confirmacion.
- Solicitud de recojo o entrega.
- Tracking en tiempo real o mock.
- Historial de reservas, incidencias, pagos y notificaciones.
- Soporte e incidencias.

### Operador de almacen

Disponible para Flutter Web y luego desktop interno:

- Ver reservas operativas.
- Validar pagos en caja si aplica.
- Check-in y check-out.
- Cargar evidencias.
- Abrir incidencias.
- Ver notificaciones operativas.
- Consultar tracking cuando exista delivery.

### Operador logistico

Disponible para Flutter Web en MVP:

- Ver ordenes de recojo/entrega.
- Ver estado y ruta.
- Actualizar posicion y estado.
- Gestionar incidencias de delivery.
- Confirmar recojo, en ruta y entrega.

### Administrador

Disponible para Flutter Web, siempre en espanol:

- Dashboard general.
- Gestion de usuarios y perfiles.
- Gestion de almacenes.
- Gestion de ciudades y zonas.
- Gestion de reservas.
- Gestion de pagos e ingresos.
- Gestion de incidencias.
- Gestion de repartidores/unidades.
- Gestion de disponibilidad, promociones y configuracion.
- Reportes semanales, mensuales y semestrales.
- Vista de tracking logistico.

### Soporte

- Consulta transversal de usuarios, reservas, pagos, incidencias y notificaciones.
- Sin capacidad de administrar todo el catalogo salvo casos aprobados por rol.

## 3. Requerimientos funcionales

### Auth y cuenta

- RF-01: registrar usuario por correo.
- RF-02: validar password, confirmacion, terminos y datos minimos.
- RF-03: emitir verificacion de correo.
- RF-04: impedir operacion completa sin correo verificado.
- RF-05: permitir login, refresh token y logout.
- RF-06: permitir completar perfil luego del alta.
- RF-07: exigir reautenticacion o password actual al cambiar email u otros datos sensibles.

### Perfil e identidad

- RF-08: guardar nombres, apellidos, correo, telefono, nacionalidad, idioma, direccion, ciudad, pais.
- RF-09: guardar documento principal y opcional secundario.
- RF-10: soportar foto local hoy y storage remoto en el futuro.
- RF-11: soportar estados `loading`, `success`, `error`.

### Descubrimiento, mapa y disponibilidad

- RF-12: listar almacenes por ciudad, zona, cercania y texto.
- RF-13: mostrar mapa con marcadores y lista sincronizada.
- RF-14: soportar zoom, paneo y re-centrado.
- RF-15: soportar filtros por precio, tamano, horario, disponibilidad y tipo de objeto.
- RF-16: soportar fallback sin permiso de ubicacion.

### Reserva

- RF-17: crear reserva con `warehouseId`, `startAt`, `endAt`, cantidad estimada y datos operativos.
- RF-18: calcular precio antes del pago.
- RF-19: bloquear reserva si correo no verificado o perfil incompleto.
- RF-20: emitir codigo/QR.
- RF-21: permitir cancelacion bajo reglas.

### Pagos

- RF-22: crear intento de pago.
- RF-23: confirmar pago.
- RF-24: consultar estado e historial.
- RF-25: reintentar pago pendiente o fallido.
- RF-26: soportar pago en caja para operacion local.
- RF-27: soportar tarjeta Izipay.
- RF-28: soportar Yape segun disponibilidad de llaves/componentes.
- RF-29: dejar Plin como wallet/QR opcional pendiente de definicion comercial final.
- RF-30: registrar webhook, conciliacion basica y auditoria.
- RF-31: soportar `REQUIRES_3DS_AUTH`.

### Delivery y tracking

- RF-32: crear orden de delivery ligada a reserva.
- RF-33: exponer tracking por reserva.
- RF-34: registrar eventos y ETA.
- RF-35: permitir vista cliente, operativo y admin.
- RF-40: bloquear creacion de `PICKUP` si el cliente no solicito recojo (`pickupRequested=false`).
- RF-41: bloquear creacion de `DELIVERY` si el cliente no solicito entrega (`dropoffRequested=false`).
- RF-42: en recojo final por oficina, cobrar tardanza si supera la tolerancia configurada.

### Admin y reportes

- RF-36: exponer dashboard resumido.
- RF-37: CRUD de almacenes y usuarios.
- RF-38: reportes de ingresos, reservas, cancelaciones, incidencias y demanda por ciudad/almacen.
- RF-39: exportacion futura a PDF, Excel y CSV.

## 4. Requerimientos no funcionales

- RNF-01: arquitectura modular, testeable y desacoplada de proveedores.
- RNF-02: backend stateless salvo persistencia y colas/eventos.
- RNF-03: trazabilidad por `correlation id`.
- RNF-04: auditoria de pagos, cambios de perfil sensible e incidencias.
- RNF-05: i18n cliente con fallback a espanol.
- RNF-06: backoffice siempre en espanol.
- RNF-07: UX usable con conectividad inestable.
- RNF-08: tracking eficiente sin saturar bateria ni red.
- RNF-09: seguridad por JWT + RBAC + validacion de email.
- RNF-10: soporte de mocks locales sin API keys.
- RNF-11: migracion futura a Firebase sin reescribir dominio.
- RNF-12: logs y metricas aptos para observabilidad.
- RNF-13: compatibilidad con H2 para test y PostgreSQL para ambientes serios.

## 5. Reglas de negocio

- RN-01: un usuario no puede reservar si `emailVerified = false`.
- RN-02: un usuario no puede reservar si `profileCompleted = false`.
- RN-03: el admin ve todo en espanol sin depender de preferencia del usuario.
- RN-04: el idioma cliente se resuelve por `preferredLanguage`, luego nacionalidad, luego fallback `es`.
- RN-05: la nacionalidad no debe sobreescribir manualmente la preferencia de idioma si el usuario ya la eligio.
- RN-06: la reserva nace en `DRAFT` o `PENDING_PAYMENT` segun flujo UI; en el repo actual se materializa como `PENDING_PAYMENT`.
- RN-07: solo una ventana horaria valida por reserva; no se permiten rangos invertidos ni vacios.
- RN-08: no se confirma reserva sin pago confirmado, salvo flujo de caja pendiente para validacion operativa.
- RN-09: un pago `PENDING` puede terminar en `CONFIRMED`, `FAILED` o `REQUIRES_3DS_AUTH` sin cerrar la reserva.
- RN-10: si Izipay devuelve necesidad de autenticacion 3DS, el intento no se marca fallido; queda pendiente.
- RN-11: Yape debe seguir la documentacion oficial disponible del entorno habilitado.
- RN-12: Plin no debe prometerse como flujo cerrado del MVP si no existe componente oficial/productizado equivalente al caso de Yape.
- RN-13: una orden de delivery debe asociarse a una reserva existente y pagada.
- RN-14: solo roles operativos pueden hacer check-in/check-out/evidencias.
- RN-15: solo roles privilegiados pueden validar pagos en caja.
- RN-16: los cambios de email deben invalidar verificacion previa y disparar nueva verificacion.
- RN-17: los webhooks deben ser idempotentes.
- RN-18: toda transaccion debe dejar huella de estado proveedor, referencia externa y timestamps.
- RN-19: operador/courier solo pueden operar ordenes de delivery de su sede asignada.
- RN-20: no se permite `PICKUP` o `DELIVERY` si el cliente no activo ese servicio en la reserva.
- RN-21: el checkout final en oficina puede exigir cargo adicional por tardanza (`LATE_PICKUP_SURCHARGE_REQUIRED`).

## 6. Casos de uso

### CU-01 Registro con verificacion de correo

- Actor: cliente.
- Flujo principal:
  - llena formulario.
  - backend crea usuario desactivado funcionalmente.
  - sistema emite codigo/link de verificacion.
  - usuario confirma correo.
  - sesion queda habilitada para completar perfil y reservar.
- Flujos alternos:
  - correo ya registrado.
  - password debil.
  - terminos no aceptados.
  - codigo expirado.

### CU-02 Completar perfil

- Actor: cliente.
- Flujo principal:
  - usuario autenticado entra a `Completa tu perfil`.
  - actualiza datos faltantes.
  - backend recalcula `profileCompleted`.
- Alternos:
  - email cambia y exige nueva verificacion.
  - documento invalido para pais/tipo.

### CU-03 Buscar almacen

- Actor: cliente.
- Flujo principal:
  - obtiene posicion o elige ciudad manual.
  - ve lista + mapa.
  - filtra por precio/tamano/horario.
  - abre detalle.
- Alternos:
  - sin permisos de ubicacion.
  - no hay almacenes en rango.

### CU-04 Crear reserva y pagar

- Actor: cliente.
- Flujo principal:
  - selecciona horarios y objetos.
  - backend calcula monto.
  - crea intent.
  - confirma con tarjeta/Yape.
  - backend confirma pago y reserva.
- Alternos:
  - pago rechazado.
  - requiere 3DS.
  - usuario abandona checkout.

### CU-05 Pago con Yape

- Actor: cliente.
- Flujo principal:
  - frontend abre experiencia Yape soportada por Izipay.
  - recibe `sourceTokenId` u `orderId` segun integracion habilitada.
  - backend confirma o espera webhook.
- Alternos:
  - timeout del wallet.
  - order creada pero no pagada.

### CU-06 Solicitar recojo/entrega

- Actor: cliente.
- Flujo principal:
  - usuario pide delivery.
  - backend crea `deliveryOrder`.
  - tracking inicia con estado asignado.
- Alternos:
  - no hay unidad disponible.
  - direccion incompleta.

### CU-07 Operacion de almacen

- Actor: operador.
- Flujo principal:
  - consulta reserva.
  - valida codigo/QR.
  - registra check-in.
  - al final registra check-out.
- Alternos:
  - codigo invalido.
  - objeto observado.

### CU-08 Dashboard admin

- Actor: administrador.
- Flujo principal:
  - revisa KPIs.
  - navega usuarios, almacenes, reservas, pagos e incidencias.
  - exporta reportes en fases posteriores.

## 7. Casuistica completa y edge cases

### Auth y perfil

- registro con correo ya existente.
- correo no verificado intentando reservar.
- correo no verificado intentando modificar email otra vez.
- usuario con perfil parcial pero sesion valida.
- cambio de email con password actual incorrecta.
- foto local inexistente o inaccesible.
- telefono vacio para delivery.
- documento principal vacio en paises que lo exigen.

### Mapa y discovery

- permiso de ubicacion denegado.
- permiso revocado en runtime.
- GPS desactivado.
- ciudad sin puntos activos.
- demasiados marcadores en una zona turistica.
- scroll/zoom rapido con red lenta.
- datos de cercania cacheados pero usuario ya cambio de ciudad.

### Reserva

- `startAt >= endAt`.
- rango cruzando medianoche.
- rango en pasado.
- slots agotados entre cotizacion y confirmacion.
- doble click en pagar.
- checkout abandonado con intento pendiente.
- cancelacion cuando el equipaje ya esta en almacen.

### Pagos

- `paymentIntentId` inexistente.
- confirmacion repetida de un pago ya procesado.
- webhook duplicado.
- webhook sin firma o con firma invalida.
- cargo aprobado pero reserva ya cancelada por timeout.
- cargo requiere 3DS.
- reintento luego de `FAILED`.
- orden Yape/Wallet creada pero nunca pagada.
- conciliacion detecta pago confirmado en proveedor y pendiente local.
- conciliacion detecta pago fallido local pero webhook posterior de aprobado.
- refund/reversa solicitada por cancelacion operativa.

### Tracking y delivery

- sin conductor asignado.
- conductor sin actualizacion de posicion por perdida de senal.
- reconexion luego de 5-10 minutos.
- entrega marcada pero ultimo punto no coincide.
- ETA negativo por reloj desalineado.
- usuario cierra app durante tracking.

### Admin

- roles sin permiso intentando entrar a vistas admin.
- dashboard sin datos historicos suficientes.
- almacenes duplicados.
- cambios masivos de disponibilidad.
- incidencias abiertas sin responsable.

## 8. Arquitectura backend Java

### Estilo recomendado

- Monolito modular.
- Hexagonal interna por modulo.
- Persistencia relacional transaccional.
- Integraciones externas en adaptadores `out`.
- Eventos internos de dominio ligeros en la siguiente fase si se necesita desacople.

### Paquetes exactos base

```text
com.tuempresa.storage
  auth
  profile
  users
  geo
  warehouses
  reservations
  payments
  delivery
  incidents
  notifications
  reports
  shared
```

### Estructura por modulo

```text
<modulo>
  application
    dto
    usecase
    port.in        (recomendado siguiente paso)
    port.out       (recomendado siguiente paso)
  domain
  infrastructure
    in.web
    out.persistence
    out.gateway
    out.messaging  (futuro)
```

### Paquetes objetivo exactos para evolucion

- `com.tuempresa.storage.auth`
- `com.tuempresa.storage.profile`
- `com.tuempresa.storage.users`
- `com.tuempresa.storage.geo`
- `com.tuempresa.storage.warehouses`
- `com.tuempresa.storage.reservations`
- `com.tuempresa.storage.pricing`
- `com.tuempresa.storage.payments`
- `com.tuempresa.storage.payments.infrastructure.out.gateway`
- `com.tuempresa.storage.payments.infrastructure.out.gateway.culqi` (recomendado refactor siguiente)
- `com.tuempresa.storage.delivery`
- `com.tuempresa.storage.incidents`
- `com.tuempresa.storage.notifications`
- `com.tuempresa.storage.reports`
- `com.tuempresa.storage.files` (ya expuesto via `FileController`, conviene consolidar modulo dedicado)
- `com.tuempresa.storage.shared.infrastructure.security`
- `com.tuempresa.storage.shared.infrastructure.web`
- `com.tuempresa.storage.shared.infrastructure.persistence`

### Modulos minimos del pedido vs estado del repo

- `auth`: implementado.
- `profile`: implementado.
- `users`: implementado.
- `warehouses`: implementado.
- `cities`: cubierto hoy dentro de `geo`.
- `reservations`: implementado.
- `pricing`: hoy embebido en reservas/warehouses; conviene separarlo.
- `payments`: implementado.
- `culqi`: hoy dentro de `payments.infrastructure.out.gateway`.
- `delivery_tracking`: implementado como `delivery`.
- `incidents`: implementado.
- `notifications`: implementado.
- `admin_reports`: implementado como `reports`.
- `files`: implementado via `FileController` y `uploads`, recomendable separar modulo.
- `shared`: implementado.

### Entidades actuales principales

- `User`
- `RefreshToken`
- `City`
- `TouristZone`
- `Warehouse`
- `Reservation`
- `PaymentAttempt`
- `PaymentWebhookEvent`
- `DeliveryOrder`
- `DeliveryTrackingEvent`
- `Incident`
- `NotificationRecord`
- `CheckinRecord`
- `CheckoutRecord`
- `StoredItemEvidence`

### Casos de uso backend clave

- `AuthService`: login, register, refresh, logout, verify email, resend verification.
- `ProfileService`: leer/editar perfil, recomputar banderas de perfil.
- `ReservationService`: crear, listar, cancelar, consultar QR, expirar.
- `PaymentService`: intent, confirm, status, history, cash approval/reject, webhook.
- `DeliveryService`: crear orden y consultar tracking.
- `NotificationService`: notificaciones in-app.

### Persistencia

- H2 para test/dev rapido.
- PostgreSQL para QA/Prod.
- Flyway obligatorio.
- Auditoria via `AuditableEntity`.

### Seguridad

- JWT access + refresh.
- RBAC por `Role`.
- Guards por modulo y endpoint.
- Revalidacion para cambios sensibles.

### Observabilidad

- Actuator.
- Correlation ID.
- Logs estructurados.
- Metricas por modulo:
  - registros.
  - reservas creadas/canceladas.
  - pagos pendientes/confirmados/fallidos.
  - webhooks procesados/ignorados/fallidos.
  - deliveries activos.

## 9. Arquitectura Flutter

### Decision

Mantener un solo repo Flutter para el MVP, con dos experiencias:

- Cliente: Android, iOS y Web.
- Backoffice: Web responsivo.

Separar en dos artefactos de despliegue:

- `travelbox-client`
- `travelbox-backoffice`

Sin duplicar codigo de red, modelos, sesion ni componentes base.

### Estructura actual adecuada

```text
lib/
  core/
  features/
  shared/
```

### Convencion recomendada por feature

```text
features/<feature>/
  data/
  domain/
  presentation/
```

### Estado y sesion

- Riverpod para estado global y por feature.
- `SessionController` como fuente unica de auth, usuario y locale.
- Persistencia local en `SharedPreferences`.

### Navegacion

- `go_router`.
- Guards por:
  - no autenticado.
  - requiere verificacion de correo.
  - requiere completar perfil.
  - rol admin/operativo.

### Formularios

- `Form` + `GlobalKey`.
- Validacion sincrona local.
- Errores remotos mapeados a mensajes de UI.

### i18n/l10n

- Cliente: `es` y `en` desde MVP.
- Admin/backoffice: strings bloqueadas en `es`.
- Fallback:
  - `preferredLanguage`
  - nacionalidad
  - locale del dispositivo
  - `es`

### Mapas

- `flutter_map` + OSM para MVP local.
- Gateway de mapas para cambiar luego a Mapbox/Google si negocio lo requiere.

### Tracking

- UI con polling hoy.
- evolucion a websocket con polling fallback.

### Offline basico

- cache de sesion.
- cache liviano de ciudades/almacenes.
- cola local solo para intents no criticos en fases posteriores.

### Pantallas minimas

Ya existentes o directamente alineadas en el repo:

- splash/onboarding
- login/registro
- verify email
- complete profile
- discovery
- warehouse detail
- reservation form
- checkout
- reservation success/detail/history
- tracking
- incidents
- profile/edit profile
- notifications
- admin dashboard
- admin users
- admin warehouses
- admin reservations
- admin payments
- admin incidents
- operator dashboard

## 10. Integracion Izipay

### Referencia oficial usada

Base de referencia oficial:

- SDK Java backend.
- tarjetas de prueba.
- Yape.
- billeteras moviles / QR.
- webhooks.
- Izipay 3DS.
- API docs.

### Principio de integracion

- El frontend no usa `secret key`.
- El frontend usa componentes oficiales de Izipay para obtener `sourceTokenId` o abrir checkout/order segun el flujo.
- El backend Java usa `secret key` para `/charges`, `/orders`, validacion de webhook y conciliacion.

### Flujo tarjeta

- frontend solicita token fuente con Izipay.
- backend `POST /payments/intents`.
- backend `POST /payments/confirm` con `sourceTokenId`.
- si Izipay aprueba:
  - `PaymentAttempt = CONFIRMED`
  - `Reservation = CONFIRMED`
- si Izipay rechaza:
  - `PaymentAttempt = FAILED`
- si Izipay requiere 3DS:
  - `PaymentAttempt = PENDING`
  - `paymentFlow = REQUIRES_3DS_AUTH`
  - `nextAction.type = AUTHENTICATE_3DS`

### Flujo Yape

Segun documentacion oficial consultada:

- Izipay publica Yape como integracion soportada en pagos online.
- Para el MVP, Yape debe modelarse como uno de dos caminos:
  - token directo tipo `sourceTokenId` si el componente habilitado lo entrega.
  - order/checkout si el ambiente comercial lo define asi.
- El backend debe seguir usando el mismo contrato `POST /payments/confirm`.
- En el repo actual, `PaymentMethod.YAPE` ya esta contemplado en la capa de pagos.

### Flujo Plin

Lo correcto para MVP:

- modelarlo como wallet/QR opcional.
- no prometer boton dedicado si el flujo oficial del comercio no esta igual de cerrado que Yape para este proyecto.
- tratarlo como `WALLET/PLIN` dentro del adaptador de pagos y activarlo solo cuando negocio confirme alcance.

### 3DS

Segun documentacion oficial de Izipay 3DS:

- un cargo puede devolver respuesta de autenticacion en vez de aprobacion final.
- el backend no debe marcar eso como rechazo.
- el frontend debe continuar el challenge y reintentar/continuar el flujo con el payload que corresponda.

Contrato recomendado de backend:

- `paymentFlow = REQUIRES_3DS_AUTH`
- `nextAction.type = AUTHENTICATE_3DS`
- `nextAction.provider = CULQI`
- `nextAction.providerPayload = {...}`

### Webhooks

- endpoint: `POST /api/v1/payments/webhooks/culqi`
- validar firma/secreto.
- idempotencia por `provider + eventId`.
- mapear `approved`, `paid`, `captured`, `failed`, `expired`, `rejected`.
- usar `order.status.changed` para ordenes/wallets.

### Conciliacion basica

Proceso programado futuro:

- buscar intentos `PENDING` mayores a N minutos.
- consultar proveedor.
- cerrar como `CONFIRMED`, `FAILED` o `EXPIRED`.
- registrar resultado en tabla auditable.

### Reversa/cancelacion

Para el MVP:

- dejar contrato backend listo:
  - `POST /payments/{paymentIntentId}/reverse`
  - `POST /payments/{paymentIntentId}/refund` (ya implementado)
- disparador:
  - cancelacion de reserva elegible.
  - error operativo.
  - duplicidad.
- estado sugerido:
  - `REFUND_PENDING`
  - `REFUNDED`
  - `REFUND_FAILED`

Detalle Izipay implementado:
- El backend normaliza `reason` de refund a valores aceptados por Izipay:
  - `solicitud_comprador`
  - `duplicidad`
  - `fraudulenta`
- Si no llega motivo compatible, usa `solicitud_comprador` como default.

## 11. Estrategia mock

### Principio

Todo proveedor externo debe tener:

- modo `mock`
- modo `test`
- modo `prod`

Con el mismo contrato de aplicacion.

### Pagos mock

- `app.payments.provider=mock`
- aprobar/rechazar sin llaves reales.
- simular:
  - aprobado
  - rechazado
  - pendiente caja
  - requiere 3DS
  - webhook duplicado

### Correo mock

- generar `verificationCodePreview`.
- registrar eventos en logs o tabla local.

### Tracking mock

- generar coordenadas interpoladas.
- estados `REQUESTED`, `ASSIGNED`, `IN_TRANSIT`, `DELIVERED`.
- ETA ficticio pero coherente.

### Foto mock/local

- guardar ruta local primero.
- aislar en `files` para cambiar a Storage luego.

### Datos mock

- ciudades y almacenes semilla.
- dashboard con KPIs semilla.
- notificaciones semilla.

## 12. Geolocalizacion

### Estrategia MVP

- OSM + `flutter_map`.
- catalogo inicial Peru:
  - Lima
  - Cusco
  - Arequipa
  - Ica
  - Puno
  - Paracas
  - Nazca
  - Trujillo
  - Piura
  - Mancora

### Local/mock

- dataset seed por ciudad, zona y almacenes.
- cercania con formula Haversine en backend o cliente.

### Produccion

- mantener proveedor intercambiable.
- cache de tiles y respuesta paginada.
- clustering cuando haya alta densidad.

### Permisos y fallback

- si no hay permiso:
  - pedir ciudad manual.
  - mostrar destinos sugeridos.
- si no hay GPS:
  - usar ultima ubicacion o ciudad elegida.

### Performance

- no recalcular lista completa en cada frame.
- debounce de busqueda.
- clustering.
- fetch por bounding box en fase siguiente.

## 13. Tracking en tiempo real

### Decision tecnica

- Recomendacion final: WebSocket para tiempo real.
- Fallback: polling HTTP.
- SSE es aceptable para vista admin liviana pero menos versatil para bidireccionalidad.

### Frecuencia recomendada

- vehiculo en movimiento: cada 3-5 segundos.
- detenido/asignado: cada 10-15 segundos.
- background: degradar a 20-30 segundos o eventos resumidos.

### Anti-lag

- interpolacion entre puntos.
- desacople entre recepcion de coordenadas y render.
- throttling de redraw.
- historial corto en memoria.

### Reconexion

- backoff exponencial.
- re-suscripcion automatica.
- si falla websocket, caer a polling.

### Mock local

- ruta definida por polyline fija.
- ticks de progreso.
- perdida de senal simulada.
- reconexion simulada.

### Eventos minimos

- REQUESTED
- ASSIGNED
- ARRIVING_PICKUP
- PICKED_UP
- IN_TRANSIT
- ARRIVING_DELIVERY
- DELIVERED
- INCIDENT

## 14. Modelo de datos

### Usuario

- `id`
- `email`
- `passwordHash`
- `firstName`
- `lastName`
- `phone`
- `nationality`
- `preferredLanguage`
- `emailVerified`
- `profileCompleted`
- `birthDate`
- `gender`
- `address`
- `city`
- `country`
- `documentType`
- `documentNumber`
- `secondaryDocumentType`
- `secondaryDocumentNumber`
- `emergencyContactName`
- `emergencyContactPhone`
- `profilePhotoPath`
- `role`

### Warehouse

- `id`
- `cityId`
- `zoneId`
- `name`
- `address`
- `latitude`
- `longitude`
- `openingHours`
- `priceFrom`
- `availableSlots`
- `active`

### Reservation

- `id`
- `code`
- `userId`
- `warehouseId`
- `startAt`
- `endAt`
- `estimatedItems`
- `totalPrice`
- `latePickupSurcharge`
- `status`
- `qrCode`

### PaymentAttempt

- `id`
- `reservationId`
- `amount`
- `status`
- `providerReference`
- `gatewayStatus`
- `gatewayMessage`

### PaymentWebhookEvent

- `id`
- `provider`
- `eventId`
- `eventType`
- `providerReference`
- `rawPayload`
- `processingStatus`
- `paymentAttemptId`
- `reservationId`

### DeliveryOrder

- `id`
- `reservationId`
- `status`
- `driverName`
- `driverPhone`
- `vehicleType`
- `vehiclePlate`
- `originLat`
- `originLng`
- `destinationLat`
- `destinationLng`
- `etaMinutes`

### DeliveryTrackingEvent

- `id`
- `deliveryOrderId`
- `sequence`
- `status`
- `latitude`
- `longitude`
- `etaMinutes`
- `message`

### Incident

- `id`
- `reservationId`
- `type`
- `status`
- `description`
- `createdBy`
- `resolvedBy`

## 15. APIs

### Auth/Profile

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`
- `POST /api/v1/auth/verify-email`
- `POST /api/v1/auth/resend-verification`
- `GET /api/v1/profile/me`
- `PATCH /api/v1/profile/me`

### Geo/Warehouses

- `GET /api/v1/geo/cities`
- `GET /api/v1/geo/zones`
- `GET /api/v1/warehouses/search`
- `GET /api/v1/warehouses/nearby`
- `GET /api/v1/warehouses/{id}`
- `GET /api/v1/warehouses/{id}/availability`

### Reservations

- `POST /api/v1/reservations`
- `GET /api/v1/reservations`
- `GET /api/v1/reservations/page`
- `GET /api/v1/reservations/{id}`
- `PATCH /api/v1/reservations/{id}/cancel`
- `GET /api/v1/reservations/{id}/qr`

### Payments

- `POST /api/v1/payments/intents`
- `POST /api/v1/payments/confirm`
- `POST /api/v1/payments/checkout`
- `GET /api/v1/payments/status`
- `GET /api/v1/payments/history`
- `GET /api/v1/payments/cash/pending`
- `POST /api/v1/payments/cash/{paymentIntentId}/approve`
- `POST /api/v1/payments/cash/{paymentIntentId}/reject`
- `POST /api/v1/payments/{paymentIntentId}/refund`
- `POST /api/v1/payments/webhooks/culqi`

### Delivery/Incidents/Notifications

- `POST /api/v1/delivery-orders`
- `GET /api/v1/delivery-orders/reservation/{reservationId}/tracking`
- `POST /api/v1/incidents`
- `PATCH /api/v1/incidents/{id}/resolve`
- `GET /api/v1/notifications/my`

### Admin

- `GET /api/v1/admin/dashboard`
- `GET /api/v1/admin/users`
- `PATCH /api/v1/admin/users/{id}/roles`
- `PATCH /api/v1/admin/users/{id}/active`
- `GET|POST|PUT|DELETE /api/v1/admin/warehouses`

### APIs a agregar en siguiente fase

- `GET /api/v1/admin/reports/revenue`
- `GET /api/v1/admin/reports/cities`
- `GET /api/v1/admin/reports/warehouses`
- `POST /api/v1/payments/{paymentIntentId}/reverse`
- `POST /api/v1/files/profile-photo`

## 16. Seguridad

- JWT access token corto + refresh token persistido.
- Password hash robusto.
- RBAC por rol.
- verificacion de correo obligatoria para operacion.
- reautenticacion para cambios sensibles.
- CORS por configuracion.
- secretos por variables de entorno.
- no exponer `secret key` en frontend.
- validar firma/secreto de webhook.
- rate limiting recomendado para auth, verify-email y pagos.
- auditoria de cambios sensibles.

## 17. QA

### Estrategia

- unit tests en dominio, mapeos, validadores y controllers simples.
- integration tests backend con H2 + MockMvc.
- widget tests Flutter.
- E2E fase siguiente con Flutter integration_test y escenarios backend local.

### Casos minimos de prueba

- registro con correo no verificado.
- verificacion correcta e incorrecta.
- edicion de perfil sensible.
- reserva con perfil incompleto bloqueada.
- pago exitoso.
- pago rechazado.
- pago en caja pendiente/aprobado/rechazado.
- pago requiere 3DS.
- webhook idempotente.
- tracking sin senal.
- tracking con reconexion.
- reserva cancelada.
- incidencia abierta y resuelta.

### Validacion ejecutada hoy

- backend: `.\mvnw.cmd test` OK.
- frontend: `flutter analyze` OK.
- frontend: `flutter test` OK.
- frontend: `flutter build web --release` OK.

### Limitaciones de validacion hoy

- Android no compilado por falta de Android SDK en esta maquina.
- Windows desktop no compilado por falta de Visual Studio.
- iOS no compilable desde Windows.

## 18. Backlog priorizado

### P0

- separar `pricing` como modulo.
- formalizar `PaymentGateway` como puerto explicito de aplicacion.
- agregar refund/reversal.
- cerrar integracion real de correo.
- endurecer validaciones por documento/pais.
- websocket para tracking.

### P1

- exportacion PDF/Excel/CSV.
- clustering avanzado de mapa.
- subida real de foto.
- conciliacion programada.
- push notifications.

### P2

- dashboards mas profundos.
- mode offline mas rico.
- entrypoint separado de backoffice.
- analitica de demanda y prediccion de ocupacion.

## 19. Roadmap MVP

### Sprint 0

- base backend y frontend.
- semillas y mocks.
- auth y sesion.

### Sprint 1

- registro, verificacion de correo, perfil.
- discovery y almacenes.

### Sprint 2

- reserva, pricing MVP, checkout mock.

### Sprint 3

- Izipay tarjeta/Yape test.
- webhooks.
- historial y notificaciones.

### Sprint 4

- delivery tracking mock/real mixto.
- admin dashboard y operacion.

### Sprint 5

- endurecimiento QA.
- hardening de seguridad.
- go-live piloto Lima.

## 20. Riesgos

- integracion Izipay puede cambiar detalles de frontend/tokenizacion por producto habilitado.
- Plin puede generar expectativas mayores que el soporte comercial real disponible.
- sin Android SDK/Visual Studio no hay validacion local completa de todas las plataformas.
- mapas OSM pueden quedarse cortos si el volumen crece mucho.
- tracking por polling escala peor que websocket.
- cambios sensibles de perfil sin UX clara generan friccion.
- datos mock muy optimistas pueden ocultar cuellos de botella.

## 21. Mejoras futuras

- Firebase Auth/Storage/Firestore/FCM como adaptadores opcionales.
- search por bounding box y heatmaps.
- promociones dinamicas.
- motor de pricing por demanda.
- split del backoffice en app web dedicada si crece mas rapido que el producto cliente.
- antifraude y reglas de riesgo en pagos.
- conciliacion financiera avanzada.
- observabilidad con dashboards y alertas.

## Referencias oficiales Izipay usadas

- SDK Java backend: https://docs.culqi.com/es/documentacion/librerias/backend/sdk_java
- Tarjetas de prueba: https://docs.culqi.com/es/documentacion/pagos-online/tarjetas-de-prueba
- Yape: https://docs.culqi.com/es/documentacion/pagos-online/yape/
- Billeteras moviles / QR: https://docs.culqi.com/es/documentacion/pagos-online/otros-metodos-pago/billeteras-moviles/
- Webhooks: https://docs.culqi.com/es/documentacion/pagos-online/webhooks/
- Izipay 3DS: https://docs.culqi.com/es/documentacion/culqi-3ds/culqi-3ds/
- Flujo de cargos 3DS: https://docs.culqi.com/es/documentacion/culqi-3ds/flujo-de-cargos-3ds/
- API docs: https://apidocs.culqi.com/
lqi-3ds/flujo-de-cargos-3ds/
- API docs: https://apidocs.culqi.com/
