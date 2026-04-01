package com.tuempresa.storage.payments.infrastructure.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuempresa.storage.payments.infrastructure.out.gateway.IzipayGatewayClient;
import com.tuempresa.storage.warehouses.domain.Warehouse;
import com.tuempresa.storage.warehouses.infrastructure.out.persistence.WarehouseRepository;
import org.junit.jupiter.api.Disabled;
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

import static com.tuempresa.storage.support.MockMvcReactiveSupport.perform;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.payments.provider=izipay")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Disabled("Requires database infrastructure")
class PaymentIzipayCheckoutIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @MockitoBean
    private IzipayGatewayClient izipayGatewayClient;

    @Test
    void shouldKeepPaymentPendingWhileIzipayCheckoutIsOpen() throws Exception {
        when(izipayGatewayClient.isConfigured()).thenReturn(true);
        when(izipayGatewayClient.requestSource()).thenReturn("ECOMMERCE");
        when(izipayGatewayClient.processType()).thenReturn("AT");
        when(izipayGatewayClient.createSession(any())).thenReturn(
                new IzipayGatewayClient.IzipaySessionResult(
                        "session-token-123",
                        "17370677285350",
                        "1737067728",
                        "4001061",
                        "PUBLIC_KEY",
                        "RSA",
                        "https://sandbox-checkout.izipay.pe/payments/v1/js/index.js",
                        objectMapper.createObjectNode()
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
                                  "approved": true
                                }
                                """.formatted(paymentIntentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.paymentFlow").value("OPEN_IZIPAY_CHECKOUT"))
                .andExpect(jsonPath("$.nextAction.type").value("OPEN_IZIPAY_CHECKOUT"))
                .andExpect(jsonPath("$.nextAction.provider").value("IZIPAY"))
                .andExpect(jsonPath("$.nextAction.authorization").value("session-token-123"))
                .andExpect(jsonPath("$.nextAction.checkoutConfig.action").value("pay"));

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
