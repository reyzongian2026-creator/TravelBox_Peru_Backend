package com.tuempresa.storage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.main.web-application-type=reactive"
)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class ReactiveWebFluxSmokeTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldAuthenticateAndAccessProtectedProfileInReactiveMode() throws Exception {
        String clientToken = loginAndGetAccessToken("client@travelbox.pe", "Client123!");

        webTestClient.get()
                .uri("/api/v1/profile/me")
                .header("Authorization", "Bearer " + clientToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.email").isEqualTo("client@travelbox.pe");

        webTestClient.get()
                .uri("/api/v1/profile/me")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    private String loginAndGetAccessToken(String email, String password) throws Exception {
        byte[] responseBody = webTestClient.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "email":"%s",
                          "password":"%s"
                        }
                        """.formatted(email, password))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.accessToken").isNotEmpty()
                .returnResult()
                .getResponseBody();

        assertThat(responseBody).isNotNull();
        JsonNode json = objectMapper.readTree(responseBody);
        return json.path("accessToken").asText();
    }
}
