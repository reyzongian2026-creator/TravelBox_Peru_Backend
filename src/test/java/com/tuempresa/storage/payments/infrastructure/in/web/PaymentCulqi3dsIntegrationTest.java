package com.tuempresa.storage.payments.infrastructure.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuempresa.storage.payments.infrastructure.out.gateway.CulqiGatewayClient;
import com.tuempresa.storage.warehouses.domain.Warehouse;
import com.tuempresa.storage.warehouses.infrastructure.out.persistence.WarehouseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static com.tuempresa.storage.support.MockMvcReactiveSupport.perform;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.payments.provider=culqi")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentCulqi3dsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @MockitoBean
    private CulqiGatewayClient culqiGatewayClient;

    @Test
    void shouldKeepPaymentPendingWhenCulqiRequires3dsAuthentication() throws Exception {
        when(culqiGatewayClient.createCharge(any())).thenReturn(
                new CulqiGatewayClient.CulqiChargeResult(
                        "chr_test_3ds_001",
                        "REQUIRES_AUTHENTICATION",
                        "Se requiere autenticacion 3DS.",
                        false,
                        true,
                        Map.of(
                                "authenticationTransactionId", "auth3ds-001",
                                "authenticationUrl", "https://3ds.test/challenge"
                        )
                )
        );

        String clientToken = loginAndGetAccessToken("client@travelbox.pe", "Client123!");
        Warehouse warehouse = warehouseRepository.findByActiveTrueOrderByNameAsc().stream().findFirst().orElseThrow();

        long reservationId = createReservation(clientToken, warehouse.getId(), 10);
        JsonNode intent = createPaymentIntent(clientToken, reservationId);
        long paymentIntentId = intent.get("id").asLong();

        perform(mockMvc, post("/api/v1/payments/confirm")
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "paymentIntentId": %s,
                                  "paymentMethod": "card",
                                  "approved": true,
                                  "sourceTokenId": "tok_test_requires_3ds"
                                }
                                """.formatted(paymentIntentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.paymentFlow").value("REQUIRES_3DS_AUTH"))
                .andExpect(jsonPath("$.nextAction.type").value("AUTHENTICATE_3DS"))
                .andExpect(jsonPath("$.nextAction.provider").value("CULQI"))
                .andExpect(jsonPath("$.nextAction.providerPayload.authenticationTransactionId").value("auth3ds-001"));

        perform(mockMvc, get("/api/v1/payments/status")
                        .header("Authorization", "Bearer " + clientToken)
                        .param("paymentIntentId", String.valueOf(paymentIntentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$.reservationStatus").value("PENDING_PAYMENT"));
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
        Instant endAt = startAt.plus(4, ChronoUnit.HOURS);
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
}

