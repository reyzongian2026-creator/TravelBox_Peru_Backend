package com.tuempresa.storage.users.infrastructure.in.web;

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

import java.util.UUID;

import static com.tuempresa.storage.support.MockMvcReactiveSupport.perform;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Disabled("Requires database infrastructure")
class AdminUserControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldListAndUpdateUsersAsAdmin() throws Exception {
        String adminToken = adminAccessToken();

        String email = "ops+" + UUID.randomUUID().toString().substring(0, 8) + "@travelbox.pe";
        String createPayload = """
                {
                  "fullName":"Operador QA",
                  "email":"%s",
                  "phone":"+51998877665",
                  "password":"Operator123!",
                  "roles":["OPERATOR"],
                  "nationality":"Peru",
                  "preferredLanguage":"es",
                  "active":true
                }
                """.formatted(email);
        String createBody = perform(mockMvc, post("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.roles[0]").value("OPERATOR"))
                .andExpect(jsonPath("$.deliveryCreatedCount").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode createJson = objectMapper.readTree(createBody);
        long userId = createJson.get("id").asLong();

        perform(mockMvc, get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .queryParam("query", email))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(userId))
                .andExpect(jsonPath("$[0].email").value(email));

        perform(mockMvc, patch("/api/v1/admin/users/{id}/roles", userId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roles": ["OPERATOR","SUPPORT"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("OPERATOR"))
                .andExpect(jsonPath("$.roles[1]").value("SUPPORT"));

        perform(mockMvc, put("/api/v1/admin/users/{id}", userId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName":"Operador QA Norte",
                                  "email":"%s",
                                  "phone":"+51998877666",
                                  "roles":["OPERATOR","SUPPORT"],
                                  "nationality":"Peru",
                                  "preferredLanguage":"en",
                                  "active":true
                                }
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Operador QA Norte"))
                .andExpect(jsonPath("$.phone").value("+51998877666"))
                .andExpect(jsonPath("$.preferredLanguage").value("en"));

        perform(mockMvc, post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"%s",
                                  "password":"Operator123!"
                                }
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").isArray())
                .andExpect(jsonPath("$.roles.length()").value(2))
                .andExpect(jsonPath("$.roles[?(@ == 'OPERATOR')]").exists())
                .andExpect(jsonPath("$.roles[?(@ == 'SUPPORT')]").exists());

        perform(mockMvc, patch("/api/v1/admin/users/{id}/password", userId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "password":"Operator456!"
                                }
                                """))
                .andExpect(status().isOk());

        perform(mockMvc, post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"%s",
                                  "password":"Operator456!"
                                }
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));

        perform(mockMvc, patch("/api/v1/admin/users/{id}/active", userId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "active": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        perform(mockMvc, post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"%s",
                                  "password":"Operator456!"
                                }
                                """.formatted(email)))
                .andExpect(status().isUnauthorized());

        String deleteEmail = "courier+" + UUID.randomUUID().toString().substring(0, 8) + "@travelbox.pe";
        String deleteBody = perform(mockMvc, post("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName":"Courier QA",
                                  "email":"%s",
                                  "phone":"+51998877667",
                                  "password":"Courier123!",
                                  "roles":["COURIER"],
                                  "nationality":"Peru",
                                  "preferredLanguage":"es",
                                  "vehiclePlate":"TBX-321",
                                  "active":true
                                }
                                """.formatted(deleteEmail)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long deleteUserId = objectMapper.readTree(deleteBody).get("id").asLong();

        perform(mockMvc, post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"%s",
                                  "password":"Courier123!"
                                }
                                """.formatted(deleteEmail)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());

        perform(mockMvc, delete("/api/v1/admin/users/{id}", deleteUserId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        perform(mockMvc, get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .queryParam("query", deleteEmail))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

    }

    private String adminAccessToken() throws Exception {
        String loginBody = perform(mockMvc, post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"admin@travelbox.pe",
                                  "password":"Admin123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode loginJson = objectMapper.readTree(loginBody);
        return loginJson.get("accessToken").asText();
    }
}

