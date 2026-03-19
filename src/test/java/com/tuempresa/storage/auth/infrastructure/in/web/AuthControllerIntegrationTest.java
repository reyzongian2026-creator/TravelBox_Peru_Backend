package com.tuempresa.storage.auth.infrastructure.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuempresa.storage.firebase.application.FirebaseAdminService;
import com.tuempresa.storage.firebase.application.FirebaseClientIdentity;
import com.tuempresa.storage.users.domain.AuthProvider;
import com.tuempresa.storage.users.infrastructure.out.persistence.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static com.tuempresa.storage.support.MockMvcReactiveSupport.perform;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private FirebaseAdminService firebaseAdminService;

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

    @Test
    void shouldRegisterAndPersistFirebaseUidWhenFirebaseAdapterReturnsUid() throws Exception {
        String firebaseUid = "firebase-register-" + UUID.randomUUID().toString().substring(0, 8);
        String email = "firebase.register+" + UUID.randomUUID().toString().substring(0, 8) + "@travelbox.pe";

        when(firebaseAdminService.syncUserAccount(any(), eq("Client123!"))).thenReturn(firebaseUid);

        perform(mockMvc, post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName":"Firebase",
                                  "lastName":"Register",
                                  "email":"%s",
                                  "password":"Client123!",
                                  "confirmPassword":"Client123!",
                                  "nationality":"Peru",
                                  "preferredLanguage":"es",
                                  "phone":"+51998877665",
                                  "termsAccepted":true
                                }
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("CLIENT"))
                .andExpect(jsonPath("$.user.authProvider").value("LOCAL"));

        var persisted = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(persisted.getFirebaseUid()).isEqualTo(firebaseUid);
    }

    @Test
    void shouldLoginUsingFirebaseSocialEndpointWhenTokenIsValid() throws Exception {
        String firebaseUid = "firebase-social-" + UUID.randomUUID().toString().substring(0, 8);
        String email = "firebase.social+" + UUID.randomUUID().toString().substring(0, 8) + "@travelbox.pe";

        when(firebaseAdminService.verifyClientIdToken("token-google-ok"))
                .thenReturn(new FirebaseClientIdentity(
                        firebaseUid,
                        email,
                        "Cliente Social",
                        "https://cdn.travelbox.pe/avatar.png",
                        AuthProvider.FIREBASE_GOOGLE
                ));

        perform(mockMvc, post("/api/v1/auth/firebase/social")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "idToken":"token-google-ok",
                                  "provider":"GOOGLE",
                                  "termsAccepted":true,
                                  "displayName":"Cliente Social"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.roles[0]").value("CLIENT"))
                .andExpect(jsonPath("$.user.authProvider").value("FIREBASE_GOOGLE"))
                .andExpect(jsonPath("$.emailVerified").value(true));

        var persisted = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(persisted.getFirebaseUid()).isEqualTo(firebaseUid);
        assertThat(persisted.getAuthProvider()).isEqualTo(AuthProvider.FIREBASE_GOOGLE);
    }
}

