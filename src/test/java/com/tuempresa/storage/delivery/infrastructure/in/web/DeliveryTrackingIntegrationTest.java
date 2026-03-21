package com.tuempresa.storage.delivery.infrastructure.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuempresa.storage.warehouses.domain.Warehouse;
import com.tuempresa.storage.warehouses.infrastructure.out.persistence.WarehouseRepository;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static com.tuempresa.storage.support.MockMvcReactiveSupport.perform;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Disabled("Requires database infrastructure")
class DeliveryTrackingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Test
    void shouldCreateDeliveryAndExposeProgressiveTrackingHistory() throws Exception {
        String clientToken = loginAndGetAccessToken("client@travelbox.pe", "Client123!");
        String operatorToken = loginAndGetAccessToken("operator@travelbox.pe", "Operator123!");
        Warehouse warehouse = warehouseForScopedOps();

        long reservationId = createAndConfirmReservation(clientToken, warehouse.getId(), false, true);
        perform(mockMvc, post("/api/v1/inventory/checkin")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reservationId": %s,
                                  "notes":"Ingreso validado."
                                }
                                """.formatted(reservationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationStatus").value("STORED"));

        perform(mockMvc, post("/api/v1/delivery-orders")
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reservationId": %s,
                                  "type":"DELIVERY",
                                  "address":"Hotel Lima Centro",
                                  "zone":"LIMA"
                                }
                                """.formatted(reservationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.driverName").isNotEmpty());

        perform(mockMvc, get("/api/v1/delivery-orders/reservation/{reservationId}/tracking", reservationId)
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.events.length()").value(2));

        perform(mockMvc, get("/api/v1/delivery-orders/reservation/{reservationId}/tracking", reservationId)
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.events.length()").value(3));

        perform(mockMvc, get("/api/v1/delivery-orders/reservation/{reservationId}/tracking", reservationId)
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.etaMinutes").value(0))
                .andExpect(jsonPath("$.events.length()").value(4));

        perform(mockMvc, get("/api/v1/delivery-orders")
                        .header("Authorization", "Bearer " + operatorToken)
                        .param("activeOnly", "false")
                        .param("query", String.valueOf(reservationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reservationId").value(reservationId))
                .andExpect(jsonPath("$[0].customerEmail").value("client@travelbox.pe"))
                .andExpect(jsonPath("$[0].warehouseName").isNotEmpty());
    }

    @Test
    void courierShouldClaimServiceAndReportManualProgress() throws Exception {
        String clientToken = loginAndGetAccessToken("client@travelbox.pe", "Client123!");
        String operatorToken = loginAndGetAccessToken("operator@travelbox.pe", "Operator123!");
        String courierToken = loginAndGetAccessToken("courier@travelbox.pe", "Courier123!");
        Warehouse warehouse = warehouseForScopedOps();

        long reservationId = createAndConfirmReservation(clientToken, warehouse.getId(), false, true);
        perform(mockMvc, post("/api/v1/inventory/checkin")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reservationId": %s,
                                  "notes":"Ingreso validado."
                                }
                                """.formatted(reservationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationStatus").value("STORED"));

        MvcResult deliveryResult = perform(mockMvc, post("/api/v1/delivery-orders")
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reservationId": %s,
                                  "type":"DELIVERY",
                                  "address":"Hotel Miraflores",
                                  "zone":"LIMA"
                                }
                                """.formatted(reservationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andReturn();

        long deliveryOrderId = objectMapper.readTree(deliveryResult.getResponse().getContentAsString()).get("id").asLong();

        perform(mockMvc, get("/api/v1/delivery-orders")
                        .header("Authorization", "Bearer " + courierToken)
                        .param("scope", "available")
                        .param("activeOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].deliveryOrderId").value(deliveryOrderId));

        perform(mockMvc, post("/api/v1/delivery-orders/{id}/claim", deliveryOrderId)
                        .header("Authorization", "Bearer " + courierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "vehicleType":"MOTO",
                                  "vehiclePlate":"TBX-777"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.driverName").value(org.hamcrest.Matchers.containsString("Courier")));

        perform(mockMvc, patch("/api/v1/delivery-orders/{id}/progress", deliveryOrderId)
                        .header("Authorization", "Bearer " + courierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status":"IN_TRANSIT",
                                  "latitude":-12.111111,
                                  "longitude":-77.012345,
                                  "etaMinutes":7,
                                  "message":"Courier en ruta al hotel."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.trackingMode").value("courier-manual"));

        perform(mockMvc, patch("/api/v1/delivery-orders/{id}/progress", deliveryOrderId)
                        .header("Authorization", "Bearer " + courierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status":"DELIVERED",
                                  "latitude":-12.109999,
                                  "longitude":-77.011111,
                                  "etaMinutes":0,
                                  "message":"Equipaje entregado al cliente."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.events.length()").value(4));

        perform(mockMvc, get("/api/v1/reservations/{id}", reservationId)
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void shouldAllowPickupFlowFromConfirmedReservationUntilStored() throws Exception {
        String clientToken = loginAndGetAccessToken("client@travelbox.pe", "Client123!");
        String courierToken = loginAndGetAccessToken("courier@travelbox.pe", "Courier123!");
        Warehouse warehouse = warehouseForScopedOps();

        long reservationId = createAndConfirmReservation(clientToken, warehouse.getId(), true, false);

        MvcResult deliveryResult = perform(mockMvc, post("/api/v1/delivery-orders")
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reservationId": %s,
                                  "type":"PICKUP",
                                  "address":"Hotel Cusco Centro",
                                  "zone":"CUSCO"
                                }
                                """.formatted(reservationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("PICKUP"))
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andReturn();

        long deliveryOrderId = objectMapper.readTree(deliveryResult.getResponse().getContentAsString()).get("id").asLong();

        perform(mockMvc, get("/api/v1/reservations/{id}", reservationId)
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CHECKIN_PENDING"));

        perform(mockMvc, post("/api/v1/delivery-orders/{id}/claim", deliveryOrderId)
                        .header("Authorization", "Bearer " + courierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "vehicleType":"AUTO",
                                  "vehiclePlate":"TBX-909"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"));

        perform(mockMvc, patch("/api/v1/delivery-orders/{id}/progress", deliveryOrderId)
                        .header("Authorization", "Bearer " + courierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status":"IN_TRANSIT",
                                  "latitude":-12.091111,
                                  "longitude":-77.019876,
                                  "etaMinutes":10,
                                  "message":"Courier camino al hotel para recojo."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_TRANSIT"));

        perform(mockMvc, patch("/api/v1/delivery-orders/{id}/progress", deliveryOrderId)
                        .header("Authorization", "Bearer " + courierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status":"DELIVERED",
                                  "latitude":-12.099999,
                                  "longitude":-77.011111,
                                  "etaMinutes":0,
                                  "message":"Equipaje recogido y recibido en almacen."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));

        perform(mockMvc, get("/api/v1/reservations/{id}", reservationId)
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("STORED"));
    }

    @Test
    void shouldBlockDeliveryActionsWhenClientDidNotRequestThoseServices() throws Exception {
        String clientToken = loginAndGetAccessToken("client@travelbox.pe", "Client123!");
        Warehouse warehouse = warehouseForScopedOps();

        long reservationId = createAndConfirmReservation(clientToken, warehouse.getId(), false, false);

        perform(mockMvc, post("/api/v1/delivery-orders")
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reservationId": %s,
                                  "type":"PICKUP",
                                  "address":"Hotel Cusco Centro",
                                  "zone":"CUSCO"
                                }
                                """.formatted(reservationId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PICKUP_NOT_REQUESTED_BY_CLIENT"));

        perform(mockMvc, post("/api/v1/delivery-orders")
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reservationId": %s,
                                  "type":"DELIVERY",
                                  "address":"Hotel Miraflores",
                                  "zone":"LIMA"
                                }
                                """.formatted(reservationId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DELIVERY_NOT_REQUESTED_BY_CLIENT"));
    }

    private long createAndConfirmReservation(
            String clientToken,
            Long warehouseId,
            boolean pickupRequested,
            boolean dropoffRequested
    ) throws Exception {
        Instant startAt = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant endAt = startAt.plus(3, ChronoUnit.HOURS);
        MvcResult reservationResult = perform(mockMvc, post("/api/v1/reservations")
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "warehouseId": %s,
                                  "startAt":"%s",
                                  "endAt":"%s",
                                  "estimatedItems":1,
                                  "pickupRequested": %s,
                                  "dropoffRequested": %s
                                }
                                """.formatted(warehouseId, startAt, endAt, pickupRequested, dropoffRequested)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode reservationJson = objectMapper.readTree(reservationResult.getResponse().getContentAsString());
        long reservationId = reservationJson.get("id").asLong();

        MvcResult intentResult = perform(mockMvc, post("/api/v1/payments/intents")
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reservationId": %s
                                }
                                """.formatted(reservationId)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode intentJson = objectMapper.readTree(intentResult.getResponse().getContentAsString());
        long paymentIntentId = intentJson.get("id").asLong();

        perform(mockMvc, post("/api/v1/payments/confirm")
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "paymentIntentId": %s,
                                  "approved": true,
                                  "paymentMethod":"wallet"
                                }
                                """.formatted(paymentIntentId)))
                .andExpect(status().isOk());
        return reservationId;
    }

    private String loginAndGetAccessToken(String email, String password) throws Exception {
        MvcResult loginResult = perform(mockMvc, post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"%s",
                                  "password":"%s"
                                }
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        return loginJson.get("accessToken").asText();
    }

    private Warehouse warehouseForScopedOps() {
        return warehouseRepository.findByActiveTrueOrderByNameAsc().stream()
                .filter(warehouse -> warehouse.getName().equals("TravelBox Miraflores")
                        || warehouse.getName().equals("TravelBox Barranco")
                        || warehouse.getName().equals("TravelBox Lima Centro"))
                .findFirst()
                .orElseThrow();
    }
}

