package com.tuempresa.storage.payments.infrastructure.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuempresa.storage.warehouses.domain.Warehouse;
import com.tuempresa.storage.warehouses.infrastructure.out.persistence.WarehouseRepository;
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
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static com.tuempresa.storage.support.MockMvcReactiveSupport.perform;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentOperationsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Test
    void shouldExposePaymentStatusAndHistoryForClient() throws Exception {
        String clientToken = loginAndGetAccessToken("client@travelbox.pe", "Client123!");
        Warehouse warehouse = warehouseRepository.findByActiveTrueOrderByNameAsc().stream().findFirst().orElseThrow();

        long reservationId = createReservation(clientToken, warehouse.getId(), 5);
        JsonNode intent = createPaymentIntent(clientToken, reservationId);
        long paymentIntentId = intent.get("id").asLong();

        perform(mockMvc, post("/api/v1/payments/confirm")
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "paymentIntentId": %s,
                                  "paymentMethod": "card",
                                  "approved": true
                                }
                                """.formatted(paymentIntentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        perform(mockMvc, get("/api/v1/payments/status")
                        .header("Authorization", "Bearer " + clientToken)
                        .param("paymentIntentId", String.valueOf(paymentIntentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentIntentId").value(paymentIntentId))
                .andExpect(jsonPath("$.paymentStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.reservationStatus").value("CONFIRMED"));

        MvcResult historyResult = perform(mockMvc, get("/api/v1/payments/history")
                        .header("Authorization", "Bearer " + clientToken)
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode historyBody = objectMapper.readTree(historyResult.getResponse().getContentAsString());
        boolean found = false;
        for (JsonNode item : historyBody.path("items")) {
            if (item.path("paymentIntentId").asLong() == paymentIntentId) {
                found = true;
                break;
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    void shouldHandleCashPendingApproveAndRejectFlows() throws Exception {
        String clientToken = loginAndGetAccessToken("client@travelbox.pe", "Client123!");
        String operatorToken = loginAndGetAccessToken("operator@travelbox.pe", "Operator123!");
        Warehouse warehouse = warehouseForScopedOps();

        long reservationId = createReservation(clientToken, warehouse.getId(), 6);
        MvcResult pendingResult = perform(mockMvc, post("/api/v1/payments/checkout")
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
                .andExpect(jsonPath("$.paymentFlow").value("WAITING_OFFLINE_VALIDATION"))
                .andReturn();
        long pendingPaymentIntentId = objectMapper.readTree(pendingResult.getResponse().getContentAsString()).path("id").asLong();

        MvcResult pendingList = perform(mockMvc, get("/api/v1/payments/cash/pending")
                        .header("Authorization", "Bearer " + operatorToken)
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode pendingBody = objectMapper.readTree(pendingList.getResponse().getContentAsString());
        boolean appearsPending = false;
        for (JsonNode item : pendingBody.path("items")) {
            if (item.path("paymentIntentId").asLong() == pendingPaymentIntentId) {
                appearsPending = true;
                break;
            }
        }
        assertThat(appearsPending).isTrue();

        perform(mockMvc, post("/api/v1/payments/cash/{paymentIntentId}/approve", pendingPaymentIntentId)
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason":"Validado en caja"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.paymentFlow").value("OFFLINE_CONFIRMED_BY_OPERATOR"));

        perform(mockMvc, get("/api/v1/reservations/{id}", reservationId)
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        long reservationId2 = createReservation(clientToken, warehouse.getId(), 7);
        MvcResult pendingResult2 = perform(mockMvc, post("/api/v1/payments/checkout")
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reservationId": %s,
                                  "paymentMethod": "cash",
                                  "approved": true
                                }
                                """.formatted(reservationId2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        long pendingPaymentIntentId2 = objectMapper.readTree(pendingResult2.getResponse().getContentAsString()).path("id").asLong();

        perform(mockMvc, post("/api/v1/payments/cash/{paymentIntentId}/reject", pendingPaymentIntentId2)
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason":"Billete no valido"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.paymentFlow").value("OFFLINE_REJECTED_BY_OPERATOR"));
    }

    @Test
    void shouldReturnClientNotifications() throws Exception {
        String clientToken = loginAndGetAccessToken("client@travelbox.pe", "Client123!");
        Warehouse warehouse = warehouseRepository.findByActiveTrueOrderByNameAsc().stream().findFirst().orElseThrow();

        long reservationId = createReservation(clientToken, warehouse.getId(), 8);
        JsonNode intent = createPaymentIntent(clientToken, reservationId);
        long paymentIntentId = intent.path("id").asLong();

        perform(mockMvc, post("/api/v1/payments/confirm")
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "paymentIntentId": %s,
                                  "paymentMethod": "card",
                                  "approved": true
                                }
                                """.formatted(paymentIntentId)))
                .andExpect(status().isOk());

        MvcResult notificationsResult = perform(mockMvc, get("/api/v1/notifications/my")
                        .header("Authorization", "Bearer " + clientToken)
                        .param("page", "0")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode notificationsBody = objectMapper.readTree(notificationsResult.getResponse().getContentAsString());
        Set<String> types = new HashSet<>();
        for (JsonNode item : notificationsBody.path("items")) {
            types.add(item.path("type").asText());
        }
        assertThat(types).contains("RESERVATION_CREATED");
        assertThat(types).contains("PAYMENT_CONFIRMED");
    }

    @Test
    void shouldProcessWebhookAndBeIdempotent() throws Exception {
        String clientToken = loginAndGetAccessToken("client@travelbox.pe", "Client123!");
        Warehouse warehouse = warehouseRepository.findByActiveTrueOrderByNameAsc().stream().findFirst().orElseThrow();

        long reservationId = createReservation(clientToken, warehouse.getId(), 9);
        JsonNode intent = createPaymentIntent(clientToken, reservationId);
        long paymentIntentId = intent.path("id").asLong();
        String providerReference = intent.path("providerReference").asText();
        String eventId = "evt-test-" + UUID.randomUUID();

        String payload = """
                {
                  "id":"%s",
                  "type":"order.paid",
                  "data":{
                    "object":{
                      "id":"%s",
                      "status":"paid",
                      "paid":true
                    }
                  }
                }
                """.formatted(eventId, providerReference);

        perform(mockMvc, post("/api/v1/payments/webhooks/culqi")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(true))
                .andExpect(jsonPath("$.idempotent").value(false))
                .andExpect(jsonPath("$.paymentIntentId").value(paymentIntentId));

        perform(mockMvc, post("/api/v1/payments/webhooks/culqi")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(true))
                .andExpect(jsonPath("$.idempotent").value(true));

        perform(mockMvc, get("/api/v1/payments/status")
                        .header("Authorization", "Bearer " + clientToken)
                        .param("paymentIntentId", String.valueOf(paymentIntentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.reservationStatus").value("CONFIRMED"));
    }

    private JsonNode createPaymentIntent(String token, long reservationId) throws Exception {
        MvcResult intentResult = perform(mockMvc, post("/api/v1/payments/intents")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reservationId": %s
                                }
                                """.formatted(reservationId)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(intentResult.getResponse().getContentAsString());
    }

    private long createReservation(String token, Long warehouseId, long dayOffset) throws Exception {
        Instant startAt = Instant.now().plus(dayOffset, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant endAt = startAt.plus(3, ChronoUnit.HOURS);
        MvcResult reservationResult = perform(mockMvc, post("/api/v1/reservations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "warehouseId": %s,
                                  "startAt":"%s",
                                  "endAt":"%s",
                                  "estimatedItems":2
                                }
                                """.formatted(warehouseId, startAt, endAt)))
                .andExpect(status().isOk())
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

