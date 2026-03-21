package com.tuempresa.storage.inventory.infrastructure.in.web;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Disabled("Requires database infrastructure")
class InventoryFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Test
    void shouldRunCheckinAndCheckoutFlow() throws Exception {
        String clientToken = loginAndGetAccessToken("client@travelbox.pe", "Client123!");
        String operatorToken = loginAndGetAccessToken("operator@travelbox.pe", "Operator123!");
        Warehouse warehouse = warehouseForScopedOps();

        long reservationId = createAndConfirmReservation(clientToken, warehouse.getId());

        perform(mockMvc, post("/api/v1/inventory/checkin")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reservationId": %s,
                                  "notes":"Ingreso validado por operador."
                                }
                                """.formatted(reservationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationStatus").value("STORED"))
                .andExpect(jsonPath("$.action").value("CHECKIN_COMPLETED"));

        perform(mockMvc, post("/api/v1/inventory/checkout")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reservationId": %s,
                                  "notes":"Reserva lista para recojo."
                                }
                                """.formatted(reservationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationStatus").value("READY_FOR_PICKUP"))
                .andExpect(jsonPath("$.action").value("READY_FOR_PICKUP"));

        perform(mockMvc, post("/api/v1/inventory/checkout")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reservationId": %s,
                                  "notes":"Entrega final validada."
                                }
                                """.formatted(reservationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.action").value("CHECKOUT_COMPLETED"));
    }

    private long createAndConfirmReservation(String clientToken, Long warehouseId) throws Exception {
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
                                  "estimatedItems":1
                                }
                                """.formatted(warehouseId, startAt, endAt)))
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
                                  "approved": true
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

