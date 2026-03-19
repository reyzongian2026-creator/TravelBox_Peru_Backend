# Consumo desde Frontend Java

## 1) Login y obtención de token

```java
HttpClient client = HttpClient.newHttpClient();
ObjectMapper mapper = new ObjectMapper();

String loginJson = """
{
  "email":"client@travelbox.pe",
  "password":"Client123!"
}
""";

HttpRequest loginRequest = HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:8080/api/v1/auth/login"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(loginJson))
        .build();

HttpResponse<String> loginResponse = client.send(loginRequest, HttpResponse.BodyHandlers.ofString());
JsonNode loginBody = mapper.readTree(loginResponse.body());
String accessToken = loginBody.get("accessToken").asText();
```

## 2) Buscar almacenes

```java
HttpRequest searchRequest = HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:8080/api/v1/warehouses/search?cityId=1"))
        .header("Authorization", "Bearer " + accessToken)
        .GET()
        .build();

HttpResponse<String> searchResponse = client.send(searchRequest, HttpResponse.BodyHandlers.ofString());
System.out.println(searchResponse.body());
```

## 3) Crear reserva

```java
String reservationPayload = """
{
  "warehouseId": 1,
  "startAt": "2026-03-20T10:00:00Z",
  "endAt": "2026-03-20T14:00:00Z",
  "estimatedItems": 2
}
""";

HttpRequest reservationRequest = HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:8080/api/v1/reservations"))
        .header("Content-Type", "application/json")
        .header("Authorization", "Bearer " + accessToken)
        .POST(HttpRequest.BodyPublishers.ofString(reservationPayload))
        .build();

HttpResponse<String> reservationResponse = client.send(reservationRequest, HttpResponse.BodyHandlers.ofString());
JsonNode reservationBody = mapper.readTree(reservationResponse.body());
long reservationId = reservationBody.get("id").asLong();
```

## 4) Crear y confirmar pago

```java
String intentPayload = "{\"reservationId\":" + reservationId + "}";

HttpRequest intentRequest = HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:8080/api/v1/payments/intents"))
        .header("Content-Type", "application/json")
        .header("Authorization", "Bearer " + accessToken)
        .POST(HttpRequest.BodyPublishers.ofString(intentPayload))
        .build();

HttpResponse<String> intentResponse = client.send(intentRequest, HttpResponse.BodyHandlers.ofString());
JsonNode intentBody = mapper.readTree(intentResponse.body());
long paymentIntentId = intentBody.get("id").asLong();

String confirmPayload = """
{
  "paymentIntentId": %d,
  "approved": true,
  "providerReference": "JAVA-FRONT-OK"
}
""".formatted(paymentIntentId);

HttpRequest confirmRequest = HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:8080/api/v1/payments/confirm"))
        .header("Content-Type", "application/json")
        .header("Authorization", "Bearer " + accessToken)
        .POST(HttpRequest.BodyPublishers.ofString(confirmPayload))
        .build();

HttpResponse<String> confirmResponse = client.send(confirmRequest, HttpResponse.BodyHandlers.ofString());
System.out.println(confirmResponse.body());
```

Tambien soporta flujo directo desde checkout con `reservationId` (sin `paymentIntentId`):

```json
POST /api/v1/payments/checkout
{
  "reservationId": 123,
  "paymentMethod": "wallet",
  "approved": true
}
```

## 5) Flujo funcional cliente (reserva y luego paga)

1. Cliente crea reserva: `POST /api/v1/reservations` -> estado `PENDING_PAYMENT`.
2. Cliente confirma pago:
   - Opcion A: `POST /api/v1/payments/intents` y luego `POST /api/v1/payments/confirm`.
   - Opcion B (checkout rapido): `POST /api/v1/payments/checkout` con `reservationId`.
3. Backend pasa reserva a estado `CONFIRMED`.

## 6) Campos de compatibilidad para frontend

- `ReservationResponse` ahora expone aliases: `qrUrl`, `qrDataUrl`, `ticketQrUrl`, `ticketQrDataUrl`.
- `WarehouseResponse` expone `lat`, `lng` y `imageUrl/photoUrl/coverImageUrl`.
- `InventoryActionResponse` expone `imageUrl` y `photoUrl`.
- El endpoint `GET /api/v1/reservations/{id}/qr` es publico para renderizar imagen QR en navegador/webview.
- Busqueda GPS compatible:
  - `GET /api/v1/geo/warehouses/nearby?lat=&lng=`
  - `GET /api/v1/warehouses/nearby?lat=&lng=`

## 7) Pago real (Culqi): Tarjeta, Yape y Plin

### Activacion backend

- `APP_PAYMENT_PROVIDER=culqi`
- `APP_CULQI_SECRET_KEY=...`
- `APP_CULQI_PUBLIC_KEY=...`
- Opcional para webhook seguro: `APP_CULQI_WEBHOOK_SECRET=...`

### Tarjeta o Yape (cargo directo)

El frontend debe tokenizar en Culqi Checkout y enviar el token:

```json
POST /api/v1/payments/checkout
{
  "reservationId": 123,
  "paymentMethod": "card",
  "sourceTokenId": "tkn_test_xxx",
  "customerEmail": "cliente@correo.com",
  "approved": true
}
```

Para Yape usar `paymentMethod: "yape"` y `sourceTokenId` de Yape.

### Plin (flujo orden + checkout + webhook)

1. Llamar checkout con `paymentMethod: "plin"` y datos cliente.
2. Backend responde `paymentFlow: "ORDER_CHECKOUT"` y `nextAction.orderId` + `nextAction.publicKey`.
3. Front abre Culqi Checkout con ese `orderId` (el usuario paga con Plin).
4. Culqi notifica a `POST /api/v1/payments/webhooks/culqi`.
5. Recién allí la reserva pasa a `CONFIRMED`.

## 8) Endpoints nuevos para integrar frontend y operaciones

- Estado de pago por intento o reserva:
  - `GET /api/v1/payments/status?paymentIntentId=123`
  - `GET /api/v1/payments/status?reservationId=123`
- Historial paginado de pagos:
  - `GET /api/v1/payments/history?page=0&size=20`
- Cola operativa de pagos en efectivo pendientes:
  - `GET /api/v1/payments/cash/pending?page=0&size=20`
- Aprobar/Rechazar pago en caja (rol operador/admin):
  - `POST /api/v1/payments/cash/{paymentIntentId}/approve`
  - `POST /api/v1/payments/cash/{paymentIntentId}/reject`
- Reembolsar pago digital confirmado:
  - `POST /api/v1/payments/{paymentIntentId}/refund`
- Notificaciones del cliente:
  - `GET /api/v1/notifications/my?page=0&size=20`
- Reservas paginadas:
  - `GET /api/v1/reservations/page?page=0&size=20`

## 9) Como controlar pago tarjeta vs efectivo

1. Cliente reserva (`PENDING_PAYMENT`).
2. Cliente paga:
   - Tarjeta/Yape/Wallet mock:
     - `POST /api/v1/payments/checkout` con `approved=true`.
     - El backend confirma inmediato y la reserva pasa a `CONFIRMED`.
   - En caja/efectivo:
     - `POST /api/v1/payments/checkout` con `paymentMethod: "counter"` o `"cash"`.
     - Respuesta queda `PENDING` con `paymentFlow: WAITING_OFFLINE_VALIDATION`.
3. Operador revisa `GET /api/v1/payments/cash/pending`.
4. Operador decide:
   - aprobar: `POST /api/v1/payments/cash/{id}/approve` -> reserva `CONFIRMED`.
   - rechazar: `POST /api/v1/payments/cash/{id}/reject` -> pago `FAILED`.

## 10) Cancelacion con regla de reembolso

Antes de cancelar una reserva, el frontend debe considerar:

1. Consultar `GET /api/v1/payments/status?reservationId={id}`.
2. Si `paymentStatus=CONFIRMED` y `paymentMethod` es digital (`card`, `yape`, `plin`, `wallet`):
   - Ejecutar `POST /api/v1/payments/{paymentIntentId}/refund`.
   - El backend cancela la reserva despues del reembolso.
3. Si no requiere reembolso:
   - Ejecutar `PATCH /api/v1/reservations/{id}/cancel`.

Si se intenta cancelar directo cuando aplica reembolso, backend responde `409 RESERVATION_REFUND_REQUIRED`.

## Notas

- Si el frontend Java es de escritorio (Swing/JavaFX), CORS no aplica.
- Si usas Java en navegador (por ejemplo Vaadin web), configura `APP_CORS_ALLOWED_ORIGINS`.
- Incluye `X-Correlation-Id` opcional para trazabilidad.
