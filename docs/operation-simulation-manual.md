# Manual De Simulacion Operativa

Complemento de casuisticas por sede, roles y reserva asistida:

- `docs/operations-casuistics-sede-manual.md`

## Credenciales demo

- `admin@travelbox.pe / Admin123!`
- `operator@travelbox.pe / Operator123!`
- `operator.north@travelbox.pe / Operator123!`
- `courier@travelbox.pe / Courier123!`
- `courier.north@travelbox.pe / Courier123!`
- `support@travelbox.pe / Support123!`
- `client@travelbox.pe / Client123!`

## Objetivo

Este manual sirve para simular una operacion real desde la reserva del cliente hasta la entrega final con operador y courier propios.

## Flujo recomendado de simulacion real

### 1. Cliente crea la reserva

1. Inicia sesion con `client@travelbox.pe`.
2. Entra a `Descubrir`.
3. Selecciona un almacen en mapa o lista.
4. Define fecha y hora de ingreso y salida.
5. Indica cantidad de bultos.
6. Completa el checkout y confirma el pago.
7. Verifica que la reserva aparezca en `Mis reservas`.

Resultado esperado:
- La reserva queda visible como la mas actual.
- El historial queda debajo en paginas.
- La reserva nace en `CONFIRMED` o `PENDING_PAYMENT` segun flujo de pago.

### 2. Operador recibe la reserva

1. Inicia sesion con `operator@travelbox.pe`.
2. Entra a `Panel operativo`.
3. Ve a `Reservas operativas`.
4. Busca la reserva por codigo.
5. Usa `Registrar ingreso` cuando el equipaje llega al almacen.

Resultado esperado:
- La reserva cambia a `STORED`.
- El cliente ve el nuevo estado en su detalle.

### 3. Operador solicita el delivery

1. En la misma reserva, pulsa `Solicitar delivery`.
2. Ingresa direccion y zona de entrega.
3. Confirma la creacion de la orden.

Resultado esperado:
- Se crea una orden de delivery.
- La reserva cambia a `OUT_FOR_DELIVERY`.
- En admin dashboard el operador empieza a sumar `servicios creados`.

### 4. Courier toma el servicio

1. Inicia sesion con `courier@travelbox.pe`.
2. Entra a `Panel courier`.
3. Abre `Servicios`.
4. En la pestana `Disponibles`, toma la orden.
5. Registra tipo de vehiculo y placa.

Resultado esperado:
- La orden desaparece de `Disponibles`.
- La orden aparece en `Mis servicios`.
- El tracking pasa a modo manual por courier.

### 5. Courier actualiza el tracking

1. En `Mis servicios`, abre el servicio.
2. Usa `Actualizar tracking`.
3. Carga coordenadas manuales o GPS del navegador.
4. Ajusta ETA y mensaje.
5. Mantiene estado `ASSIGNED` o `IN_TRANSIT` segun avance.

Resultado esperado:
- Cliente, operador y admin ven la ubicacion actualizada.
- El dashboard refleja actividad del courier.

### 6. Courier entrega el equipaje

1. Desde el mismo servicio, cambia estado a `DELIVERED`.
2. Guarda la actualizacion final.

Resultado esperado:
- La orden de delivery queda completada.
- La reserva cambia a `COMPLETED`.
- El courier suma una entrega completada.

## Casos de uso principales

### Caso 1. Reserva con entrega exitosa

- Cliente reserva y paga.
- Operador registra ingreso.
- Operador crea delivery.
- Courier toma el servicio.
- Courier actualiza tracking.
- Courier marca entrega final.

### Caso 1.1. Cancelacion con pago digital confirmado

Regla operativa vigente:
- Si el pago ya esta `CONFIRMED` y el metodo es digital (`card`, `yape`, `plin`, `wallet`), no se puede cancelar directo.
- Primero se ejecuta reembolso y luego el backend cancela la reserva.

Flujo API:
1. Consultar pago por reserva: `GET /api/v1/payments/status?reservationId={id}`.
2. Reembolsar: `POST /api/v1/payments/{paymentIntentId}/refund`.
3. El backend deja pago en `REFUNDED` y reserva en `CANCELLED`.

Comision por reembolso:
- `APP_PAYMENT_REFUND_COMMISSION_GRACE_MINUTES` (default 60)
- `APP_PAYMENT_REFUND_COMMISSION_PERCENT_AFTER_GRACE` (default 4.50)
- `APP_PAYMENT_REFUND_MINIMUM_FEE` (default 0.00)

Si el pago no es digital (caja/efectivo), se mantiene cancelacion operativa normal.

### Caso 1.2. Reembolso con Culqi (tarjeta y yape) y motivo estandarizado

Cuando el pago pertenece a Culqi y se solicita devolucion:

- El backend usa el endpoint de refund de Culqi.
- El motivo se normaliza a valores permitidos por Culqi:
  - `solicitud_comprador`
  - `duplicidad`
  - `fraudulenta`
- Si no llega un motivo valido, se envia `solicitud_comprador`.

Nota operativa:
- La comision por ventana de tiempo se sigue aplicando con los parametros de app:
  - `APP_PAYMENT_REFUND_COMMISSION_GRACE_MINUTES`
  - `APP_PAYMENT_REFUND_COMMISSION_PERCENT_AFTER_GRACE`
  - `APP_PAYMENT_REFUND_MINIMUM_FEE`

### Caso 2. Cliente llama con codigo de reserva

- Operador busca la reserva por codigo en `Reservas operativas`.
- Revisa estado actual.
- Aplica la accion permitida para ese estado.
- Si hay delivery, abre tracking y comunica ETA.

Controles anti-salto de proceso:
- No se permite crear otra orden activa del mismo tipo (`PICKUP` o `DELIVERY`) para la misma reserva.
- `PICKUP` solo inicia desde `CONFIRMED` y solo si el cliente marco `pickupRequested=true`.
- `DELIVERY` solo inicia desde `STORED` o `READY_FOR_PICKUP` y solo si el cliente marco `dropoffRequested=true`.
- Operador/courier solo pueden operar ordenes de su sede asignada (scope por warehouse).
- Si la reserva no tiene ese servicio solicitado por cliente, el backend responde conflicto (`409`) y bloquea la accion.

### Caso 2.1. Recojo en oficina fuera de tiempo (cargo adicional)

Aplica solo para salida en oficina (sin delivery de salida):

- En checkout final desde `READY_FOR_PICKUP`, el backend calcula tardanza contra `endAt`.
- Se usa tolerancia configurable: `APP_INVENTORY_OFFICE_PICKUP_LATE_GRACE_MINUTES` (default `30`).
- Si se excede la tolerancia, se exige registrar cargo adicional antes de completar checkout.
- Mientras el cargo no este cubierto, la API responde `LATE_PICKUP_SURCHARGE_REQUIRED`.
- Al cubrirse el cargo, se incrementan `storageAmount`, `totalPrice` y `latePickupSurcharge` en la reserva.

### Caso 3. Admin crea mas operadores y couriers

1. Inicia sesion con `admin@travelbox.pe`.
2. Ve a `Usuarios operativos`.
3. Usa `Nuevo operador`, `Nuevo courier` o `Nuevo usuario`.
4. Completa ficha, roles y credenciales temporales.
5. Si necesitas rotar clave, usa `Credenciales`.

Resultado esperado:
- El usuario aparece en la lista.
- Puedes activarlo, editarlo o eliminarlo.
- Si ya tuvo operaciones, la eliminacion puede ser bloqueada por integridad.

### Caso 4. Incidencia en soporte

1. Cliente abre una incidencia desde la reserva.
2. Soporte entra con `support@travelbox.pe`.
3. Revisa ticket, tracking y reserva.
4. Atiende o resuelve el caso.

## Que debe hacer cada rol

### Cliente

- Crear reserva.
- Pagar.
- Ver QR.
- Seguir estado.
- Ver tracking del delivery.
- Reportar incidencias.

### Operador

- Buscar reserva por codigo.
- Registrar ingreso al almacen.
- Crear orden de delivery.
- Coordinar salida.

### Courier

- Tomar servicio disponible.
- Registrar vehiculo.
- Actualizar ubicacion, ETA y estado.
- Marcar entrega final.

### Admin

- Crear mas operadores y couriers.
- Actualizar credenciales.
- Ver ranking de almacenes, operadores y couriers.
- Auditar usuarios operativos.

## Recomendaciones para una demo realista

- Usa un cliente para crear una reserva nueva.
- Usa un operador para pasarla a `STORED`.
- Crea el delivery con una direccion distinta al almacen.
- Usa un courier diferente al operador.
- Actualiza tracking al menos dos veces antes de cerrar la entrega.
- Valida dashboard admin al final para ver ranking y conteos.
