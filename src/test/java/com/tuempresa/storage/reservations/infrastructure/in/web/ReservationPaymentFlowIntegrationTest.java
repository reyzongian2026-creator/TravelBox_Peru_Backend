package com.tuempresa.storage.reservations.infrastructure.in.web;

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
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Disabled("Requires database infrastructure")
class ReservationPaymentFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Test
    void shouldCreateReservationAndConfirmPayment() throws Exception {
        String clientToken = loginAndGetAccessToken("client@travelbox.pe", "Client123!");
        Warehouse warehouse = warehouseForScopedOps();

        Instant startAt = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant endAt = startAt.plus(4, ChronoUnit.HOURS);

        MvcResult reservationResult = perform(mockMvc, post("/api/v1/reservations")
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "warehouseId": %s,
                                  "startAt":"%s",
                                  "endAt":"%s",
                                  "estimatedItems":2
                                }
                                """.formatted(warehouse.getId(), startAt, endAt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
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
                .andExpect(jsonPath("$.status").value("PENDING"))
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
                                  "providerReference": "TEST-CONFIRMED-1"
                                }
                                """.formatted(paymentIntentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        MvcResult detailResult = perform(mockMvc, get("/api/v1/reservations/{id}", reservationId)
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andReturn();

        JsonNode detailJson = objectMapper.readTree(detailResult.getResponse().getContentAsString());
        assertThat(detailJson.get("id").asLong()).isEqualTo(reservationId);
    }

    @Test
    void shouldConfirmPaymentFromCheckoutUsingReservationIdAndRepeatedMethod() throws Exception {
        String clientToken = loginAndGetAccessToken("client@travelbox.pe", "Client123!");
        Warehouse warehouse = warehouseForScopedOps();

        long firstReservationId = createReservation(clientToken, warehouse.getId(), 1);
        long secondReservationId = createReservation(clientToken, warehouse.getId(), 2);

        perform(mockMvc, post("/api/v1/payments/checkout")
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reservationId": %s,
                                  "paymentMethod": "wallet",
                                  "approved": true
                                }
                                """.formatted(firstReservationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.providerReference").value(org.hamcrest.Matchers.containsString("MOCK-WALLET")));

        perform(mockMvc, post("/api/v1/payments/checkout")
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reservationId": %s,
                                  "paymentMethod": "wallet",
                                  "approved": true
                                }
                                """.formatted(secondReservationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.providerReference").value(org.hamcrest.Matchers.containsString("MOCK-WALLET")));

        perform(mockMvc, get("/api/v1/reservations/{id}", firstReservationId)
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        perform(mockMvc, get("/api/v1/reservations/{id}", secondReservationId)
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void shouldRequireOperatorValidationForCounterPayment() throws Exception {
        String clientToken = loginAndGetAccessToken("client@travelbox.pe", "Client123!");
        String operatorToken = loginAndGetAccessToken("operator@travelbox.pe", "Operator123!");
        Warehouse warehouse = warehouseForScopedOps();

        long reservationId = createReservation(clientToken, warehouse.getId(), 3);

        perform(mockMvc, post("/api/v1/payments/checkout")
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reservationId": %s,
                                  "paymentMethod": "counter",
                                  "approved": true
                                }
                                """.formatted(reservationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.paymentFlow").value("WAITING_OFFLINE_VALIDATION"));

        perform(mockMvc, get("/api/v1/reservations/{id}", reservationId)
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));

        MvcResult pendingListResult = perform(mockMvc, get("/api/v1/payments/cash/pending")
                        .header("Authorization", "Bearer " + operatorToken)
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode pendingItems = objectMapper.readTree(pendingListResult.getResponse().getContentAsString()).path("items");
        long pendingPaymentIntentId = -1;
        for (JsonNode item : pendingItems) {
            if (item.path("reservationId").asLong() == reservationId) {
                pendingPaymentIntentId = item.path("paymentIntentId").asLong();
                break;
            }
        }
        assertThat(pendingPaymentIntentId).isPositive();

        perform(mockMvc, post("/api/v1/payments/cash/{paymentIntentId}/approve", pendingPaymentIntentId)
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason":"Validado por operador"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.paymentFlow").value("OFFLINE_CONFIRMED_BY_OPERATOR"));

        perform(mockMvc, get("/api/v1/reservations/{id}", reservationId)
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void shouldListReservationsWithPaginationEndpoint() throws Exception {
        String clientToken = loginAndGetAccessToken("client@travelbox.pe", "Client123!");
        Warehouse warehouse = warehouseForScopedOps();
        createReservation(clientToken, warehouse.getId(), 4);

        perform(mockMvc, get("/api/v1/reservations/page")
                        .header("Authorization", "Bearer " + clientToken)
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void shouldFilterReservationsByCodeQueryForPrivilegedUsers() throws Exception {
        String clientToken = loginAndGetAccessToken("client@travelbox.pe", "Client123!");
        String operatorToken = loginAndGetAccessToken("operator@travelbox.pe", "Operator123!");
        Warehouse warehouse = warehouseForScopedOps();

        Instant startAt = Instant.now().plus(8, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant endAt = startAt.plus(2, ChronoUnit.HOURS);

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
                                """.formatted(warehouse.getId(), startAt, endAt)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode reservationJson = objectMapper.readTree(reservationResult.getResponse().getContentAsString());
        String reservationCode = reservationJson.get("qrCode").asText();

        perform(mockMvc, get("/api/v1/reservations/page")
                        .header("Authorization", "Bearer " + operatorToken)
                        .param("page", "0")
                        .param("size", "20")
                        .param("query", reservationCode.substring(0, 12)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].qrCode").value(reservationCode))
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    private long createReservation(String clientToken, Long warehouseId, long dayOffset) throws Exception {
        Instant startAt = Instant.now().plus(dayOffset, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
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
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andReturn();
        JsonNode reservationJson = objectMapper.readTree(reservationResult.getResponse().getContentAsString());
        return reservationJson.get("id").asLong();
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

