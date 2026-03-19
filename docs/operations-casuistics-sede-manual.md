# Manual Operativo Por Casuisticas y Sede

## 1. Objetivo

Este manual define el flujo operativo completo para que:

- el cliente siempre pueda recuperar su equipaje de forma segura,
- cada operador y courier solo trabaje con reservas de su sede,
- soporte atienda incidencias con contacto directo al cliente,
- admin tenga visibilidad total sin filtros por sede.

## 2. Reglas de acceso por rol

- `ADMIN`: acceso total (todas las sedes, reservas, delivery, incidencias, usuarios).
- `OPERATOR` y `CITY_SUPERVISOR`: acceso solo a reservas/incidencias/operaciones de sus `warehouseIds` asignados.
- `COURIER`: solo ve y toma servicios de su(s) sede(s) asignada(s).
- `SUPPORT`: acceso solo a incidencias, con acceso a contacto directo del cliente (`WhatsApp`/`Llamar`).
- `CLIENT`: solo sus propias reservas/incidencias.

## 3. Configuracion inicial (obligatoria)

1. Ingresar como `ADMIN`.
2. Crear/validar sedes en `Admin > Almacenes`.
3. Crear usuarios operativos en `Admin > Usuarios operativos`.
4. Para cada `OPERATOR`, `CITY_SUPERVISOR` y `COURIER`, asignar una o mas sedes (`warehouseIds`).
5. Confirmar que soporte no tenga roles operativos (solo `SUPPORT`).

Validacion esperada:

- operador de sede Molina no puede operar ni ver reservas de otra sede,
- courier de sede Molina no puede tomar servicios de otra sede.

## 4. Notificaciones y eventos acumulados

- El backend expone stream incremental en:
  - `GET /api/v1/notifications/stream?afterId={id}&limit={n}`
- La web escucha en polling y acumula eventos en bandeja.
- Cada notificacion incluye `route` para redireccion:
  - reserva: `/reservation/{id}`
  - operador: `/operator/reservations`
  - courier: `/courier/services`
  - incidencias: `/operator/incidents`

## 5. Casuisticas operativas paso a paso

### Caso A. Cliente llega a oficina con reserva ya creada

1. Operador busca la reserva en `Reservas operativas`.
2. Verifica identidad y codigo/QR.
3. Registra ingreso (`checkin`) y evidencia de maleta.
4. Reserva pasa a `STORED`.
5. Cliente visualiza el nuevo estado en su app.

### Caso B. Cliente reserva y solicita recojo de maleta (pickup)

1. Cliente crea reserva con `pickupRequested=true` (o se genera orden tipo `PICKUP`).
2. Operador de sede recibe evento de nueva reserva.
3. Courier de la misma sede toma servicio disponible.
4. Courier recoge maleta y operador confirma ingreso al almacen.
5. Reserva queda almacenada con trazabilidad completa.

### Caso C. Cliente deja maleta y luego solicita entrega a destino (delivery)

1. Cliente reserva y deja maleta en sede (estado `STORED`).
2. Operador crea orden de delivery (`type=DELIVERY`).
3. Solo couriers de esa sede reciben la orden.
4. Courier toma servicio, actualiza tracking y ETA.
5. Se confirma entrega y reserva pasa a `COMPLETED`.

### Caso D. Cliente sin app (reserva asistida en oficina por operador)

Se habilito endpoint de reserva asistida:

- `POST /api/v1/reservations/assisted`
- Roles permitidos: `ADMIN`, `OPERATOR`, `CITY_SUPERVISOR`.
- Si el operador no tiene acceso a la sede indicada, retorna `403`.
- Si el cliente no existe, se crea automaticamente como `CLIENT`.

Ejemplo de payload:

```json
{
  "warehouseId": 1,
  "startAt": "2026-03-14T14:00:00Z",
  "endAt": "2026-03-15T02:00:00Z",
  "estimatedItems": 2,
  "bagSize": "M",
  "pickupRequested": false,
  "dropoffRequested": true,
  "deliveryRequested": true,
  "extraInsurance": true,
  "customerFullName": "Cliente Mostrador",
  "customerEmail": "cliente.mostrador@travelbox.pe",
  "customerPhone": "+51999111222",
  "customerNationality": "Peru",
  "customerPreferredLanguage": "es"
}
```

### Caso E. Incidencia y soporte

1. Cliente abre incidencia.
2. `SUPPORT` atiende solo modulo incidencias.
3. Desde incidencia usa contacto directo:
   - enlace WhatsApp (`wa.me`)
   - enlace llamada (`tel:`)
4. Se registra resolucion y seguimiento.

## 6. Controles de seguridad operacional

- Check-in/check-out con evidencia.
- Flujo QR/PIN para entregas presenciales y delivery.
- Historial de estados de reserva y tracking.
- Notificacion por evento en cada cambio clave.
- Scope estricto por sede para operacion/logistica.

## 7. Pruebas de no-fallo recomendadas

1. Crear reserva en sede A y validar que operador/courier de sede B no la vean.
2. Abrir incidencia y validar que soporte la ve, pero no caja ni inventario.
3. Crear reserva asistida con operador de sede A hacia sede B y validar rechazo `403`.
4. Completar ciclo completo (reserva -> almacen -> delivery -> completado) y validar notificaciones acumuladas.
