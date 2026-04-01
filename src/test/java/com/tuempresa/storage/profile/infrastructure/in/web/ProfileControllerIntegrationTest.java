package com.tuempresa.storage.profile.infrastructure.in.web;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Disabled("Requires database infrastructure")
class ProfileControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldFetchAndUpdateProfileWithReauthenticationForSensitiveFields() throws Exception {
        String email = "profile+" + UUID.randomUUID().toString().substring(0, 8) + "@travelbox.pe";
        MvcResult registerResult = perform(mockMvc, post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName":"Perfil",
                                  "lastName":"Prueba",
                                  "email":"%s",
                                  "password":"Client123!",
                                  "confirmPassword":"Client123!",
                                  "nationality":"Peru",
                                  "preferredLanguage":"es",
                                  "phone":"+51991122334",
                                  "termsAccepted":true
                                }
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = objectMapper.readTree(registerResult.getResponse().getContentAsString()).get("accessToken").asText();

        perform(mockMvc, get("/api/v1/profile/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.emailVerified").value(false))
                .andExpect(jsonPath("$.profileCompleted").value(true));

        perform(mockMvc, patch("/api/v1/profile/me")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "documentType":"PASSPORT",
                                  "documentNumber":"AA998877"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentType").value("PASSPORT"))
                .andExpect(jsonPath("$.documentNumber").value("AA998877"));

        perform(mockMvc, patch("/api/v1/profile/me")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "documentType":"PASSPORT",
                                  "documentNumber":"BB112233"
                                }
                                """))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.code").value("PROFILE_REAUTH_REQUIRED"));

        perform(mockMvc, patch("/api/v1/profile/me")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "address":"Av. La Paz 123",
                                  "city":"Lima",
                                  "country":"Peru",
                                  "documentType":"PASSPORT",
                                  "documentNumber":"BB112233",
                                  "currentPassword":"Client123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address").value("Av. La Paz 123"))
                .andExpect(jsonPath("$.city").value("Lima"))
                .andExpect(jsonPath("$.documentType").value("PASSPORT"))
                .andExpect(jsonPath("$.documentNumber").value("BB112233"))
                .andExpect(jsonPath("$.documentChangeRemaining").value(2));
    }
}

