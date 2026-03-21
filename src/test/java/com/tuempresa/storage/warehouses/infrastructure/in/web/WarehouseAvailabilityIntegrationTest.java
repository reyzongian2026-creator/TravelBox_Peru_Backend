package com.tuempresa.storage.warehouses.infrastructure.in.web;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Disabled("Requires database infrastructure")
class WarehouseAvailabilityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Test
    void shouldReturnAvailabilityByRange() throws Exception {
        String clientToken = loginAndGetAccessToken("client@travelbox.pe", "Client123!");
        Warehouse warehouse = warehouseRepository.findByActiveTrueOrderByNameAsc().stream().findFirst().orElseThrow();

        Instant startAt = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant endAt = startAt.plus(3, ChronoUnit.HOURS);
        createReservation(clientToken, warehouse.getId(), startAt, endAt);

        perform(mockMvc, get("/api/v1/warehouses/{id}/availability", warehouse.getId())
                        .param("startAt", startAt.toString())
                        .param("endAt", endAt.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warehouseId").value(warehouse.getId()))
                .andExpect(jsonPath("$.reservedInRange").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.hasAvailability").value(true));
    }

    @Test
    void shouldReturnNearbyWarehousesUsingCoordinateAliases() throws Exception {
        perform(mockMvc, get("/api/v1/warehouses/nearby")
                        .param("latitude", "-12.120")
                        .param("longitude", "-77.030")
                        .param("radiusKm", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].lat").exists())
                .andExpect(jsonPath("$[0].lng").exists())
                .andExpect(jsonPath("$[0].imageUrl").exists())
                .andExpect(jsonPath("$[0].image").exists());
    }

    @Test
    void shouldAcceptAvailabilityDatesWithoutTimezone() throws Exception {
        Warehouse warehouse = warehouseRepository.findByActiveTrueOrderByNameAsc().stream().findFirst().orElseThrow();

        perform(mockMvc, get("/api/v1/warehouses/{id}/availability", warehouse.getId())
                        .param("startAt", "2026-03-12T03:00:00.000")
                        .param("endAt", "2026-03-12T09:00:00.000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warehouseId").value(warehouse.getId()));
    }

    private void createReservation(String token, Long warehouseId, Instant startAt, Instant endAt) throws Exception {
        perform(mockMvc, post("/api/v1/reservations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "warehouseId": %s,
                                  "startAt":"%s",
                                  "endAt":"%s",
                                  "estimatedItems":1
                                }
                                """.formatted(warehouseId, startAt, endAt)))
                .andExpect(status().isOk());
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

