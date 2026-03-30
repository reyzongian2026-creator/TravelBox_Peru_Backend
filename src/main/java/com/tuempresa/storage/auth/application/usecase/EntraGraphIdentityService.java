package com.tuempresa.storage.auth.application.usecase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuempresa.storage.shared.domain.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

@Service
public class EntraGraphIdentityService {

    private static final URI GRAPH_ME_URI = URI.create(
            "https://graph.microsoft.com/v1.0/me?$select=id,displayName,givenName,surname,mail,userPrincipalName"
    );

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper;

    public EntraGraphIdentityService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public EntraUserIdentity resolveUser(String accessToken) {
        String token = normalize(accessToken);
        if (token == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "ENTRA_TOKEN_REQUIRED",
                    "Debes enviar el access token de Microsoft Entra."
            );
        }

        HttpRequest request = HttpRequest.newBuilder(GRAPH_ME_URI)
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                throw new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        "ENTRA_TOKEN_INVALID",
                        "El token de Microsoft Entra es invalido o no tiene permisos."
                );
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        "ENTRA_GRAPH_ERROR",
                        "No se pudo consultar el perfil de Microsoft Entra."
                );
            }

            JsonNode body = objectMapper.readTree(response.body());
            String id = normalize(body.path("id").asText(null));
            String email = normalize(body.path("mail").asText(null));
            if (email == null) {
                email = normalize(body.path("userPrincipalName").asText(null));
            }
            if (email == null) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "ENTRA_EMAIL_REQUIRED",
                        "Microsoft Entra no devolvio un correo valido."
                );
            }

            return new EntraUserIdentity(
                    id,
                    email.toLowerCase(Locale.ROOT),
                    normalize(body.path("displayName").asText(null)),
                    normalize(body.path("givenName").asText(null)),
                    normalize(body.path("surname").asText(null))
            );
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "ENTRA_GRAPH_ERROR",
                    "No se pudo consultar el perfil de Microsoft Entra."
            );
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    public record EntraUserIdentity(
            String subject,
            String email,
            String displayName,
            String givenName,
            String surname
    ) {
    }
}
