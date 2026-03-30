package com.tuempresa.storage.auth.infrastructure.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuempresa.storage.auth.infrastructure.out.persistence.RefreshTokenRepository;
import com.tuempresa.storage.users.infrastructure.out.persistence.UserRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Disabled("Requires database infrastructure")
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    void shouldLoginAndRefreshToken() throws Exception {
        MvcResult loginResult = perform(mockMvc, post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"admin@travelbox.pe",
                                  "password":"Admin123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String refreshToken = loginJson.get("refreshToken").asText();

        perform(mockMvc, post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken":"%s"
                                }
                                """.formatted(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    void shouldLogoutAllSessionsEvenWhenProvidedRefreshTokenIsAlreadyRevoked() throws Exception {
        MvcResult loginResult = perform(mockMvc, post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"admin@travelbox.pe",
                                  "password":"Admin123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String firstRefreshToken = loginJson.get("refreshToken").asText();

        MvcResult firstRefreshResult = perform(mockMvc, post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken":"%s"
                                }
                                """.formatted(firstRefreshToken)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode firstRefreshJson = objectMapper.readTree(firstRefreshResult.getResponse().getContentAsString());
        String activeRefreshToken = firstRefreshJson.get("refreshToken").asText();

        assertThat(refreshTokenRepository.findByTokenAndRevokedFalse(activeRefreshToken)).isPresent();

        perform(mockMvc, post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken":"%s"
                                }
                                """.formatted(firstRefreshToken)))
                .andExpect(status().isNoContent());

        assertThat(refreshTokenRepository.findByTokenAndRevokedFalse(activeRefreshToken)).isEmpty();

        perform(mockMvc, post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken":"%s"
                                }
                                """.formatted(activeRefreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRegisterVerifyEmailAndReturnSessionFlags() throws Exception {
        String email = "cliente+" + UUID.randomUUID().toString().substring(0, 8) + "@travelbox.pe";
        MvcResult registerResult = perform(mockMvc, post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName":"Cliente",
                                  "lastName":"Nuevo",
                                  "email":"%s",
                                  "password":"Client123!",
                                  "confirmPassword":"Client123!",
                                  "nationality":"Peru",
                                  "preferredLanguage":"es",
                                  "phone":"+51999999999",
                                  "termsAccepted":true
                                }
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.roles[0]").value("CLIENT"))
                .andExpect(jsonPath("$.emailVerified").value(false))
                .andExpect(jsonPath("$.verificationCodePreview").isNotEmpty())
                .andReturn();

        JsonNode registerJson = objectMapper.readTree(registerResult.getResponse().getContentAsString());
        String accessToken = registerJson.get("accessToken").asText();
        String verificationCode = registerJson.get("verificationCodePreview").asText();

        perform(mockMvc, post("/api/v1/auth/verify-email")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code":"%s"
                                }
                                """.formatted(verificationCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(true));
    }

    @Test
    void shouldVerifyEmailWithoutSessionWhenEmailAndCodeAreProvided() throws Exception {
        String email = "public.verify+" + UUID.randomUUID().toString().substring(0, 8) + "@travelbox.pe";
        MvcResult registerResult = perform(mockMvc, post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName":"Publico",
                                  "lastName":"Verificacion",
                                  "email":"%s",
                                  "password":"Client123!",
                                  "confirmPassword":"Client123!",
                                  "nationality":"Peru",
                                  "preferredLanguage":"es",
                                  "phone":"+51998811223",
                                  "termsAccepted":true
                                }
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode registerJson = objectMapper.readTree(registerResult.getResponse().getContentAsString());
        String verificationCode = registerJson.get("verificationCodePreview").asText();

        perform(mockMvc, post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"%s",
                                  "code":"%s"
                                }
                                """.formatted(email, verificationCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(true));
    }

}

