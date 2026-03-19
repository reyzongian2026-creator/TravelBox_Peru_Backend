package com.tuempresa.storage.reports.infrastructure.in.web;

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

import static com.tuempresa.storage.support.MockMvcReactiveSupport.perform;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminDashboardIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Test
    void shouldReturnDashboardByPeriodWithRankings() throws Exception {
        String adminToken = loginAndGetAccessToken("admin@travelbox.pe", "Admin123!");
        String clientToken = loginAndGetAccessToken("client@travelbox.pe", "Client123!");
        createRecentConfirmedReservation(clientToken);

        MvcResult result = perform(mockMvc, get("/api/v1/admin/dashboard")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("period", "year"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("year"))
                .andExpect(jsonPath("$.periodLabel").isNotEmpty())
                .andExpect(jsonPath("$.summary.reservations").isNumber())
                .andExpect(jsonPath("$.topWarehouses").isArray())
                .andExpect(jsonPath("$.topCities").isArray())
                .andExpect(jsonPath("$.topCouriers").isArray())
                .andExpect(jsonPath("$.topOperators").isArray())
                .andExpect(jsonPath("$.trend").isArray())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.path("statusBreakdown").isArray()).isTrue();
        assertThat(body.path("totalWarehouses").asLong()).isGreaterThan(0);
        assertThat(body.path("confirmedPaymentsAmount").asDouble()).isGreaterThan(0d);
        assertThat(body.path("topCouriers").isArray()).isTrue();
    }

    @Test
    void compatibilityEndpointShouldAlsoAcceptPeriod() throws Exception {
        String adminToken = loginAndGetAccessToken("admin@travelbox.pe", "Admin123!");

        perform(mockMvc, get("/api/v1/dashboard")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("period", "year"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("year"))
                .andExpect(jsonPath("$.summary.confirmedRevenue").isNotEmpty());
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

    private void createRecentConfirmedReservation(String clientToken) throws Exception {
        Warehouse warehouse = warehouseRepository.findByActiveTrueOrderByNameAsc().stream().findFirst().orElseThrow();
        Instant startAt = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
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
        long reservationId = objectMapper.readTree(reservationResult.getResponse().getContentAsString()).path("id").asLong();

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
        long paymentIntentId = objectMapper.readTree(intentResult.getResponse().getContentAsString()).path("id").asLong();

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
    }
}

