package com.tuempresa.storage.incidents.infrastructure.in.web;

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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static com.tuempresa.storage.support.MockMvcReactiveSupport.perform;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Disabled("Requires database infrastructure")
class IncidentOperationsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Test
    void shouldAllowClientSupportTicketWithEvidenceAndAdminResolution() throws Exception {
        String clientToken = loginAndGetAccessToken("client@travelbox.pe", "Client123!");
        String adminToken = loginAndGetAccessToken("admin@travelbox.pe", "Admin123!");
        Warehouse warehouse = warehouseRepository.findByActiveTrueOrderByNameAsc().stream().findFirst().orElseThrow();

        long reservationId = createReservation(clientToken, warehouse.getId(), 10);

        MockMultipartFile evidence = new MockMultipartFile(
                "file",
                "damage.png",
                MediaType.IMAGE_PNG_VALUE,
                "fake-image-content".getBytes()
        );

        MvcResult uploadResult = perform(mockMvc, multipart("/api/v1/inventory/evidences/upload")
                        .file(evidence)
                        .header("Authorization", "Bearer " + clientToken)
                        .param("reservationId", String.valueOf(reservationId))
                        .param("type", "incident-photo")
                        .param("observation", "Golpe visible"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("EVIDENCE_REGISTERED"))
                .andExpect(jsonPath("$.imageUrl").exists())
                .andReturn();

        String evidenceUrl = objectMapper.readTree(uploadResult.getResponse().getContentAsString()).path("imageUrl").asText();
        assertThat(evidenceUrl).isNotBlank();

        MvcResult openResult = perform(mockMvc, post("/api/v1/incidents")
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reservationId": %s,
                                  "description": "Cliente reporta problema. Evidencia: %s"
                                }
                                """.formatted(reservationId, evidenceUrl)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationId").value(reservationId))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn();

        long incidentId = objectMapper.readTree(openResult.getResponse().getContentAsString()).path("id").asLong();

        perform(mockMvc, get("/api/v1/reservations/{id}", reservationId)
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));

        MvcResult listOpenResult = perform(mockMvc, get("/api/v1/incidents")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("status", "OPEN"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode openList = objectMapper.readTree(listOpenResult.getResponse().getContentAsString());
        boolean foundOpen = false;
        for (JsonNode item : openList) {
            if (item.path("id").asLong() == incidentId) {
                foundOpen = true;
                assertThat(item.path("description").asText()).contains("Evidencia:");
                break;
            }
        }
        assertThat(foundOpen).isTrue();

        perform(mockMvc, patch("/api/v1/incidents/{id}/resolve", incidentId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resolution":"Caso revisado por soporte"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        perform(mockMvc, get("/api/v1/reservations/{id}", reservationId)
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
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
}

