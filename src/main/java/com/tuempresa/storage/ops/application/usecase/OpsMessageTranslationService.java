package com.tuempresa.storage.ops.application.usecase;

import com.fasterxml.jackson.databind.JsonNode;
import com.tuempresa.storage.shared.domain.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class OpsMessageTranslationService {

    private static final Logger log = LoggerFactory.getLogger(OpsMessageTranslationService.class);

    private static final Map<String, Map<String, String>> LOCAL_DICTIONARY = buildLocalDictionary();

    private final RestClient googleTranslationClient;
    private final String provider;
    private final boolean allowFallback;
    private final String googleApiKey;
    private final String googleModel;

    public OpsMessageTranslationService(
            RestClient.Builder restClientBuilder,
            @Value("${app.translation.provider:local}") String provider,
            @Value("${app.translation.allow-fallback:true}") boolean allowFallback,
            @Value("${app.translation.google.base-url:https://translation.googleapis.com}") String googleBaseUrl,
            @Value("${app.translation.google.api-key:}") String googleApiKey,
            @Value("${app.translation.google.model:nmt}") String googleModel
    ) {
        this.googleTranslationClient = restClientBuilder.baseUrl(googleBaseUrl).build();
        this.provider = provider == null ? "local" : provider.trim().toLowerCase(Locale.ROOT);
        this.allowFallback = allowFallback;
        this.googleApiKey = googleApiKey == null ? "" : googleApiKey.trim();
        this.googleModel = StringUtils.hasText(googleModel) ? googleModel.trim() : "nmt";
    }

    public String translateFromSpanish(String messageInSpanish, String targetLanguage) {
        String source = messageInSpanish == null ? "" : messageInSpanish.trim();
        if (source.isEmpty()) {
            return "";
        }
        String language = normalizeLanguage(targetLanguage);
        if ("es".equals(language)) {
            return source;
        }

        String translated = null;
        if (isGoogleProvider(provider)) {
            translated = translateUsingGoogle(source, language);
            if (!StringUtils.hasText(translated) && !allowFallback) {
                throw new ApiException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "TRANSLATION_PROVIDER_UNAVAILABLE",
                        "No se pudo traducir el mensaje y el fallback local esta deshabilitado."
                );
            }
        }

        if (StringUtils.hasText(translated)) {
            return translated.trim();
        }
        return translateUsingLocalFallback(source, language);
    }

    private String translateUsingGoogle(String messageInSpanish, String targetLanguage) {
        if (!StringUtils.hasText(googleApiKey)) {
            log.warn("Traduccion Google omitida: app.translation.google.api-key vacio.");
            return null;
        }
        try {
            JsonNode response = googleTranslationClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/language/translate/v2")
                            .queryParam("key", googleApiKey)
                            .build())
                    .body(Map.of(
                            "q", messageInSpanish,
                            "source", "es",
                            "target", targetLanguage,
                            "format", "text",
                            "model", googleModel
                    ))
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode translations = response == null ? null : response.path("data").path("translations");
            if (translations == null || !translations.isArray() || translations.isEmpty()) {
                return null;
            }
            String rawTranslated = translations.get(0).path("translatedText").asText("");
            if (!StringUtils.hasText(rawTranslated)) {
                return null;
            }
            return decodeCommonHtmlEntities(rawTranslated).trim();
        } catch (RestClientException | IllegalStateException ex) {
            log.warn("Traduccion Google fallo para targetLanguage={}: {}", targetLanguage, ex.getMessage());
            return null;
        }
    }

    private String translateUsingLocalFallback(String messageInSpanish, String targetLanguage) {
        String normalizedMessage = normalizeSentence(messageInSpanish);
        Map<String, String> dictionaryByLanguage = LOCAL_DICTIONARY.get(targetLanguage);
        if (dictionaryByLanguage != null && dictionaryByLanguage.containsKey(normalizedMessage)) {
            return dictionaryByLanguage.get(normalizedMessage);
        }
        return "[" + targetLanguage.toUpperCase(Locale.ROOT) + "] " + messageInSpanish.trim();
    }

    private String normalizeLanguage(String rawLanguage) {
        if (!StringUtils.hasText(rawLanguage)) {
            return "es";
        }
        String normalized = rawLanguage.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() <= 2) {
            return normalized;
        }
        return normalized.substring(0, 2);
    }

    private String normalizeSentence(String value) {
        return value == null
                ? ""
                : value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .replace("á", "a")
                .replace("é", "e")
                .replace("í", "i")
                .replace("ó", "o")
                .replace("ú", "u")
                .replace("ñ", "n");
    }

    private String decodeCommonHtmlEntities(String value) {
        return value
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
    }

    private boolean isGoogleProvider(String configuredProvider) {
        return switch (configuredProvider) {
            case "google", "google-translate", "google_translate", "gcp" -> true;
            default -> false;
        };
    }

    private static Map<String, Map<String, String>> buildLocalDictionary() {
        Map<String, Map<String, String>> byLanguage = new LinkedHashMap<>();

        Map<String, String> en = new LinkedHashMap<>();
        en.put(
                "hola, por favor presenta tu qr para validar tu reserva y maleta.",
                "Hello, please show your QR to validate your reservation and luggage."
        );
        en.put(
                "tu entrega fue validada. comparte este pin con el operador o courier para completar.",
                "Your handoff was validated. Share this PIN with the operator or courier to complete."
        );
        en.put(
                "el operador necesita aprobar la entrega. te avisaremos cuando este listo.",
                "The operator needs to approve the handoff. We will notify you when it is ready."
        );
        en.put(
                "tu reserva esta lista para recojo. presenta tu qr y pin de seguridad.",
                "Your reservation is ready for pickup. Show your QR and security PIN."
        );
        byLanguage.put("en", Map.copyOf(en));

        Map<String, String> pt = new LinkedHashMap<>();
        pt.put(
                "hola, por favor presenta tu qr para validar tu reserva y maleta.",
                "Ola, por favor apresente seu QR para validar sua reserva e bagagem."
        );
        pt.put(
                "tu entrega fue validada. comparte este pin con el operador o courier para completar.",
                "Sua entrega foi validada. Compartilhe este PIN com o operador ou courier para concluir."
        );
        byLanguage.put("pt", Map.copyOf(pt));

        return Map.copyOf(byLanguage);
    }
}
