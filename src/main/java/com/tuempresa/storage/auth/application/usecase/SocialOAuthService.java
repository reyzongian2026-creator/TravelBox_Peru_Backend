package com.tuempresa.storage.auth.application.usecase;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuempresa.storage.auth.application.dto.AuthTokenResponse;
import com.tuempresa.storage.shared.domain.exception.ApiException;
import com.tuempresa.storage.shared.infrastructure.web.PublicUrlService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class SocialOAuthService {

    private static final String GOOGLE_PROVIDER = "GOOGLE";
    private static final String FACEBOOK_PROVIDER = "FACEBOOK";
    private static final URI GOOGLE_TOKEN_URI = URI.create("https://oauth2.googleapis.com/token");
    private static final URI GOOGLE_USERINFO_URI = URI.create("https://openidconnect.googleapis.com/v1/userinfo");
    private static final URI FACEBOOK_PROFILE_BASE_URI = URI.create("https://graph.facebook.com/me");
    private static final String DEFAULT_FRONTEND_CALLBACK = "https://www.inkavoy.pe/auth/callback";
    private static final Set<String> ALLOWED_CALLBACK_HOSTS = Set.of(
            "www.inkavoy.pe",
            "inkavoy.pe",
            "localhost",
            "127.0.0.1"
    );

    private final AuthService authService;
    private final ObjectMapper objectMapper;
    private final PublicUrlService publicUrlService;
    private final SecretKey stateSigningKey;
    private final HttpClient httpClient;
    private final String googleClientId;
    private final String googleClientSecret;
    private final String facebookAppId;
    private final String facebookAppSecret;
    private final String configuredFrontendBaseUrl;

    public SocialOAuthService(
            AuthService authService,
            ObjectMapper objectMapper,
            PublicUrlService publicUrlService,
            @Value("${app.security.jwt.secret}") String jwtSecret,
            @Value("${tbx-auth-google-web-client-id:}") String googleClientId,
            @Value("${tbx-auth-google-web-client-secret:}") String googleClientSecret,
            @Value("${tbx-auth-facebook-app-id:}") String facebookAppId,
            @Value("${tbx-auth-facebook-app-secret:}") String facebookAppSecret,
            @Value("${app.frontend-base-url:}") String configuredFrontendBaseUrl
    ) {
        this.authService = authService;
        this.objectMapper = objectMapper;
        this.publicUrlService = publicUrlService;
        this.stateSigningKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.googleClientId = normalize(googleClientId);
        this.googleClientSecret = normalize(googleClientSecret);
        this.facebookAppId = normalize(facebookAppId);
        this.facebookAppSecret = normalize(facebookAppSecret);
        this.configuredFrontendBaseUrl = normalize(configuredFrontendBaseUrl);
    }

    public URI buildAuthorizationRedirect(String provider, @Nullable String redirectUri) {
        String normalizedProvider = normalizeProvider(provider);
        String frontendRedirectUri = resolveFrontendRedirect(redirectUri);
        String callbackUri = publicUrlService.absolute("/api/v1/auth/oauth/" + normalizedProvider.toLowerCase(Locale.ROOT) + "/callback");
        String state = issueState(normalizedProvider, frontendRedirectUri);

        return switch (normalizedProvider) {
            case GOOGLE_PROVIDER -> buildGoogleAuthorizationUri(callbackUri, state);
            case FACEBOOK_PROVIDER -> buildFacebookAuthorizationUri(callbackUri, state);
            default -> throw providerUnsupported();
        };
    }

    public URI handleCallback(
            String provider,
            @Nullable String code,
            @Nullable String state,
            @Nullable String error,
            @Nullable String errorDescription
    ) {
        OAuthState callbackState = verifyState(provider, state);
        if (error != null && !error.isBlank()) {
            return buildErrorRedirect(
                    callbackState.redirectUri(),
                    callbackState.provider(),
                    normalize(errorDescription) == null ? error : errorDescription
            );
        }
        String authCode = normalize(code);
        if (authCode == null) {
            return buildErrorRedirect(
                    callbackState.redirectUri(),
                    callbackState.provider(),
                    "No se recibio el codigo de autorizacion."
            );
        }

        try {
            AuthService.SocialIdentity identity = switch (callbackState.provider()) {
                case GOOGLE_PROVIDER -> exchangeGoogleCode(authCode, callbackUri(callbackState.provider()));
                case FACEBOOK_PROVIDER -> exchangeFacebookCode(authCode, callbackUri(callbackState.provider()));
                default -> throw providerUnsupported();
            };
            AuthTokenResponse response = authService.socialOAuthLogin(
                    identity,
                    callbackState.provider(),
                    true
            );
            return buildSuccessRedirect(callbackState.redirectUri(), response, callbackState.provider());
        } catch (ApiException ex) {
            return buildErrorRedirect(callbackState.redirectUri(), callbackState.provider(), ex.getMessage());
        }
    }

    private URI buildGoogleAuthorizationUri(String callbackUri, String state) {
        ensureConfigured(GOOGLE_PROVIDER, googleClientId, googleClientSecret);
        return UriComponentsBuilder.fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
                .queryParam("client_id", googleClientId)
                .queryParam("redirect_uri", callbackUri)
                .queryParam("response_type", "code")
                .queryParam("scope", "openid email profile")
                .queryParam("state", state)
                .queryParam("access_type", "offline")
                .queryParam("include_granted_scopes", "true")
                .queryParam("prompt", "select_account")
                .encode()
                .build()
                .toUri();
    }

    private URI buildFacebookAuthorizationUri(String callbackUri, String state) {
        ensureConfigured(FACEBOOK_PROVIDER, facebookAppId, facebookAppSecret);
        return UriComponentsBuilder.fromUriString("https://www.facebook.com/dialog/oauth")
                .queryParam("client_id", facebookAppId)
                .queryParam("redirect_uri", callbackUri)
                .queryParam("response_type", "code")
                .queryParam("scope", "email,public_profile")
                .queryParam("state", state)
                .encode()
                .build()
                .toUri();
    }

    private AuthService.SocialIdentity exchangeGoogleCode(String code, String callbackUri) {
        String requestBody = "code=" + encode(code)
                + "&client_id=" + encode(googleClientId)
                + "&client_secret=" + encode(googleClientSecret)
                + "&redirect_uri=" + encode(callbackUri)
                + "&grant_type=authorization_code";

        JsonNode tokenBody = sendFormRequest(GOOGLE_TOKEN_URI, requestBody, GOOGLE_PROVIDER);
        String accessToken = normalize(tokenBody.path("access_token").asText(null));
        if (accessToken == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "GOOGLE_TOKEN_INVALID", "Google no devolvio access token.");
        }

        JsonNode profileBody = sendAuthorizedGet(GOOGLE_USERINFO_URI, accessToken, GOOGLE_PROVIDER);
        return new AuthService.SocialIdentity(
                normalize(profileBody.path("sub").asText(null)),
                normalize(profileBody.path("email").asText(null)),
                normalize(profileBody.path("name").asText(null)),
                normalize(profileBody.path("given_name").asText(null)),
                normalize(profileBody.path("family_name").asText(null))
        );
    }

    private AuthService.SocialIdentity exchangeFacebookCode(String code, String callbackUri) {
        URI tokenUri = UriComponentsBuilder.fromUriString("https://graph.facebook.com/v19.0/oauth/access_token")
                .queryParam("client_id", facebookAppId)
                .queryParam("redirect_uri", callbackUri)
                .queryParam("client_secret", facebookAppSecret)
                .queryParam("code", code)
                .encode()
                .build()
                .toUri();

        JsonNode tokenBody = sendGetRequest(tokenUri, FACEBOOK_PROVIDER);
        String accessToken = normalize(tokenBody.path("access_token").asText(null));
        if (accessToken == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "FACEBOOK_TOKEN_INVALID", "Facebook no devolvio access token.");
        }

        URI profileUri = UriComponentsBuilder.fromUri(FACEBOOK_PROFILE_BASE_URI)
                .queryParam("fields", "id,name,first_name,last_name,email")
                .queryParam("access_token", accessToken)
                .encode()
                .build()
                .toUri();

        JsonNode profileBody = sendGetRequest(profileUri, FACEBOOK_PROVIDER);
        return new AuthService.SocialIdentity(
                normalize(profileBody.path("id").asText(null)),
                normalize(profileBody.path("email").asText(null)),
                normalize(profileBody.path("name").asText(null)),
                normalize(profileBody.path("first_name").asText(null)),
                normalize(profileBody.path("last_name").asText(null))
        );
    }

    private JsonNode sendFormRequest(URI uri, String requestBody, String provider) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();
        return sendJsonRequest(request, provider);
    }

    private JsonNode sendAuthorizedGet(URI uri, String accessToken, String provider) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .GET()
                .build();
        return sendJsonRequest(request, provider);
    }

    private JsonNode sendGetRequest(URI uri, String provider) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .GET()
                .build();
        return sendJsonRequest(request, provider);
    }

    private JsonNode sendJsonRequest(HttpRequest request, String provider) {
        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        provider + "_OAUTH_ERROR",
                        providerLabel(provider) + " no acepto la autenticacion solicitada."
                );
            }
            return objectMapper.readTree(response.body());
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    provider + "_OAUTH_ERROR",
                    "No se pudo completar la autenticacion con " + providerLabel(provider) + "."
            );
        }
    }

    private String issueState(String provider, String redirectUri) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject("social-oauth")
                .claim("provider", provider)
                .claim("redirectUri", redirectUri)
                .issuedAt(java.util.Date.from(now))
                .expiration(java.util.Date.from(now.plus(10, ChronoUnit.MINUTES)))
                .signWith(stateSigningKey)
                .compact();
    }

    private OAuthState verifyState(String provider, @Nullable String stateToken) {
        String normalizedProvider = normalizeProvider(provider);
        String token = normalize(stateToken);
        if (token == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SOCIAL_STATE_REQUIRED", "Falta el state del login social.");
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(stateSigningKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String stateProvider = normalize(claims.get("provider", String.class));
            String redirectUri = resolveFrontendRedirect(claims.get("redirectUri", String.class));
            if (!normalizedProvider.equalsIgnoreCase(stateProvider)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "SOCIAL_STATE_INVALID", "El provider del callback no coincide.");
            }
            return new OAuthState(stateProvider.toUpperCase(Locale.ROOT), redirectUri);
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SOCIAL_STATE_INVALID", "No se pudo validar el callback social.");
        }
    }

    private URI buildSuccessRedirect(String redirectUri, AuthTokenResponse response, String provider) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("accessToken", response.accessToken());
        payload.put("refreshToken", response.refreshToken());
        payload.put("verificationCodePreview", response.verificationCodePreview());
        payload.put("user", response.user());
        payload.put("provider", provider);

        return UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("payload", encodePayload(payload))
                .encode()
                .build()
                .toUri();
    }

    private URI buildErrorRedirect(String redirectUri, String provider, String message) {
        return UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("error", normalize(message) == null ? "No se pudo iniciar sesion." : message)
                .queryParam("provider", provider)
                .encode()
                .build()
                .toUri();
    }

    private String encodePayload(Map<String, Object> payload) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(payload);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json);
        } catch (JsonProcessingException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "SOCIAL_PAYLOAD_ERROR", "No se pudo preparar la sesion social.");
        }
    }

    private String resolveFrontendRedirect(@Nullable String requestedRedirectUri) {
        String fallback = buildDefaultFrontendCallback();
        String requested = normalize(requestedRedirectUri);
        if (requested == null) {
            return fallback;
        }
        try {
            URI uri = URI.create(requested);
            String scheme = normalize(uri.getScheme());
            String host = normalize(uri.getHost());
            if (scheme == null || host == null) {
                return fallback;
            }
            String loweredHost = host.toLowerCase(Locale.ROOT);
            if (!ALLOWED_CALLBACK_HOSTS.contains(loweredHost) && !loweredHost.endsWith(".inkavoy.pe")) {
                return fallback;
            }
            return uri.toString();
        } catch (Exception ex) {
            return fallback;
        }
    }

    private String buildDefaultFrontendCallback() {
        if (configuredFrontendBaseUrl != null) {
            return stripTrailingSlash(configuredFrontendBaseUrl) + "/auth/callback";
        }
        return DEFAULT_FRONTEND_CALLBACK;
    }

    private String callbackUri(String provider) {
        return publicUrlService.absolute("/api/v1/auth/oauth/" + provider.toLowerCase(Locale.ROOT) + "/callback");
    }

    private String normalizeProvider(String provider) {
        String normalized = normalize(provider);
        if (normalized == null) {
            throw providerUnsupported();
        }
        return switch (normalized.toUpperCase(Locale.ROOT)) {
            case GOOGLE_PROVIDER -> GOOGLE_PROVIDER;
            case FACEBOOK_PROVIDER -> FACEBOOK_PROVIDER;
            default -> throw providerUnsupported();
        };
    }

    private ApiException providerUnsupported() {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "SOCIAL_PROVIDER_UNSUPPORTED",
                "Solo Google y Facebook estan permitidos en este flujo."
        );
    }

    private void ensureConfigured(String provider, String clientId, String clientSecret) {
        if (clientId == null || clientSecret == null) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    provider + "_NOT_CONFIGURED",
                    providerLabel(provider) + " aun no esta configurado en Azure."
            );
        }
    }

    private String providerLabel(String provider) {
        return switch (provider.toUpperCase(Locale.ROOT)) {
            case GOOGLE_PROVIDER -> "Google";
            case FACEBOOK_PROVIDER -> "Facebook";
            default -> "el proveedor social";
        };
    }

    private String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record OAuthState(String provider, String redirectUri) {
    }
}
