package com.tuempresa.storage.warehouses.infrastructure.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

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
@Disabled("Requires database infrastructure")
class AdminWarehouseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldCreateWarehouseActiveByDefaultAndAppearInPublicSearch() throws Exception {
        String adminToken = loginAndGetAccessToken("admin@travelbox.pe", "Admin123!");
        long cityId = firstCityId();
        String uniqueName = "TravelBox Test " + UUID.randomUUID().toString().substring(0, 6);

        MvcResult createResult = perform(mockMvc, post("/api/v1/admin/warehouses")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cityId": %s,
                                  "name": "%s",
                                  "address": "Av. Prueba 123",
                                  "latitude": -12.1201,
                                  "longitude": -77.0301,
                                  "capacity": 30,
                                  "openHour": "08:00",
                                  "closeHour": "22:00",
                                  "rules": "solo prueba"
                                }
                                """.formatted(cityId, uniqueName)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andReturn();

        JsonNode createBody = objectMapper.readTree(createResult.getResponse().getContentAsString());
        long warehouseId = createBody.path("id").asLong();

        MvcResult publicSearch = perform(mockMvc, get("/api/v1/warehouses/search")
                        .param("query", uniqueName))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode searchBody = objectMapper.readTree(publicSearch.getResponse().getContentAsString());
        boolean found = false;
        for (JsonNode item : searchBody) {
            if (item.path("id").asLong() == warehouseId) {
                found = true;
                break;
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    void shouldShowCreatedWarehouseInAdminRegistry() throws Exception {
        String adminToken = loginAndGetAccessToken("admin@travelbox.pe", "Admin123!");
        long cityId = firstCityId();
        String uniqueName = "TravelBox Registry " + UUID.randomUUID().toString().substring(0, 6);

        MvcResult createResult = perform(mockMvc, post("/api/v1/admin/warehouses")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cityId": %s,
                                  "name": "%s",
                                  "address": "Jr. Registro 456",
                                  "latitude": -12.1101,
                                  "longitude": -77.0201,
                                  "capacity": 15,
                                  "openHour": "07:00",
                                  "closeHour": "21:00",
                                  "rules": "registro",
                                  "active": false
                                }
                                """.formatted(cityId, uniqueName)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andReturn();
        long warehouseId = objectMapper.readTree(createResult.getResponse().getContentAsString()).path("id").asLong();

        MvcResult registryResult = perform(mockMvc, get("/api/v1/admin/warehouses/registry")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("page", "0")
                        .param("size", "20")
                        .param("query", uniqueName))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode registryBody = objectMapper.readTree(registryResult.getResponse().getContentAsString());
        boolean found = false;
        for (JsonNode item : registryBody.path("items")) {
            if (item.path("id").asLong() == warehouseId) {
                found = true;
                assertThat(item.path("active").asBoolean()).isFalse();
                break;
            }
        }
        assertThat(found).isTrue();
    }

    private long firstCityId() throws Exception {
        MvcResult result = perform(mockMvc, get("/api/v1/geo/cities"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode cities = objectMapper.readTree(result.getResponse().getContentAsString());
        return cities.get(0).path("id").asLong();
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


