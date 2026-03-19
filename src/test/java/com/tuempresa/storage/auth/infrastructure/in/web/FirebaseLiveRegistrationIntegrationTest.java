package com.tuempresa.storage.auth.infrastructure.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserRecord;
import com.tuempresa.storage.users.infrastructure.out.persistence.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.firebase.user-migration.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "firebase.live.tests", matches = "true")
class FirebaseLiveRegistrationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldRegisterClientAndCreateRemoteFirebaseUser() throws Exception {
        String email = "live.firebase+" + UUID.randomUUID().toString().substring(0, 8) + "@travelbox.pe";
        String firebaseUid = null;
        try {
            MvcResult registerResult = perform(mockMvc, post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "firstName":"Live",
                                      "lastName":"Firebase",
                                      "email":"%s",
                                      "password":"Client123!",
                                      "confirmPassword":"Client123!",
                                      "nationality":"Peru",
                                      "preferredLanguage":"es",
                                      "phone":"+51995544332",
                                      "termsAccepted":true
                                    }
                                    """.formatted(email)))
                    .andExpect(status().isOk())
                    .andReturn();

            assertThat(
                    objectMapper.readTree(registerResult.getResponse().getContentAsString())
                            .get("accessToken")
                            .asText()
            ).isNotBlank();

            var persisted = userRepository.findByEmailIgnoreCase(email).orElseThrow();
            assertThat(persisted.getFirebaseUid()).isNotBlank();
            firebaseUid = persisted.getFirebaseUid();

            UserRecord remoteUser = FirebaseAuth.getInstance(firebaseApp()).getUser(firebaseUid);
            assertThat(remoteUser.getEmail()).isEqualTo(email);
            assertThat(remoteUser.isDisabled()).isFalse();
        } finally {
            if (firebaseUid != null) {
                try {
                    FirebaseAuth.getInstance(firebaseApp()).deleteUser(firebaseUid);
                } catch (Exception ignored) {
                    // Best-effort cleanup to avoid polluting Firebase Auth in live tests.
                }
            }
        }
    }

    private FirebaseApp firebaseApp() {
        return FirebaseApp.getApps().stream()
                .filter(app -> "travelbox-backend".equals(app.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Firebase App travelbox-backend no esta inicializada."));
    }
}
