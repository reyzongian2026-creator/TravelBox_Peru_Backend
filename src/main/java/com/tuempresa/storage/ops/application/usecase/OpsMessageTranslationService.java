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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class OpsMessageTranslationService {

    private static final int MAX_CACHE_ENTRIES = 2048;
    private static final Logger log = LoggerFactory.getLogger(OpsMessageTranslationService.class);

    private static final Map<String, Map<String, String>> LOCAL_DICTIONARY = buildLocalDictionary();

    private final RestClient googleTranslationClient;
    private final String provider;
    private final boolean allowFallback;
    private final String googleApiKey;
    private final String googleModel;
    private final long cacheSeconds;
    private final ConcurrentMap<String, CacheEntry> translationCache = new ConcurrentHashMap<>();

    public OpsMessageTranslationService(
            RestClient.Builder restClientBuilder,
            @Value("${app.translation.provider:local}") String provider,
            @Value("${app.translation.allow-fallback:true}") boolean allowFallback,
            @Value("${app.translation.google.base-url:https://translation.googleapis.com}") String googleBaseUrl,
            @Value("${app.translation.google.api-key:}") String googleApiKey,
            @Value("${app.translation.google.model:nmt}") String googleModel,
            @Value("${app.translation.cache-seconds:21600}") long cacheSeconds
    ) {
        this.googleTranslationClient = restClientBuilder.baseUrl(googleBaseUrl).build();
        this.provider = provider == null ? "local" : provider.trim().toLowerCase(Locale.ROOT);
        this.allowFallback = allowFallback;
        this.googleApiKey = googleApiKey == null ? "" : googleApiKey.trim();
        this.googleModel = StringUtils.hasText(googleModel) ? googleModel.trim() : "nmt";
        this.cacheSeconds = Math.max(60L, cacheSeconds);
    }

    public String translateFromSpanish(String messageInSpanish, String targetLanguage) {
        return translate(messageInSpanish, "es", targetLanguage);
    }

    public String translateToSpanish(String message, String sourceLanguage) {
        return translate(message, sourceLanguage, "es");
    }

    public String translate(String message, String sourceLanguage, String targetLanguage) {
        String source = message == null ? "" : message.trim();
        if (source.isEmpty()) {
            return "";
        }
        String fromLanguage = normalizeLanguage(sourceLanguage);
        String toLanguage = normalizeLanguage(targetLanguage);
        if (fromLanguage.equals(toLanguage)) {
            return source;
        }
        String cacheKey = buildCacheKey(source, fromLanguage, toLanguage);
        String cached = readCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        String translated = null;
        if (isGoogleProvider(provider)) {
            translated = translateUsingGoogle(source, fromLanguage, toLanguage);
            if (!StringUtils.hasText(translated) && !allowFallback) {
                throw new ApiException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "TRANSLATION_PROVIDER_UNAVAILABLE",
                        "No se pudo traducir el mensaje y el fallback local esta deshabilitado."
                );
            }
        }

        String resolved = StringUtils.hasText(translated)
                ? translated.trim()
                : translateUsingLocalFallback(source, fromLanguage, toLanguage);
        if (StringUtils.hasText(resolved)) {
            writeCache(cacheKey, resolved);
        }
        return resolved;
    }

    private String translateUsingGoogle(String message, String sourceLanguage, String targetLanguage) {
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
                            "q", message,
                            "source", sourceLanguage,
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

    private String translateUsingLocalFallback(String message, String sourceLanguage, String targetLanguage) {
        String normalizedMessage = normalizeSentence(message);
        if ("es".equals(sourceLanguage)) {
            Map<String, String> dictionaryByLanguage = LOCAL_DICTIONARY.get(targetLanguage);
            if (dictionaryByLanguage != null && dictionaryByLanguage.containsKey(normalizedMessage)) {
                return dictionaryByLanguage.get(normalizedMessage);
            }
            return "[" + targetLanguage.toUpperCase(Locale.ROOT) + "] " + message.trim();
        }

        if ("es".equals(targetLanguage)) {
            String reverseMatch = reverseLookupToSpanish(sourceLanguage, normalizedMessage);
            return reverseMatch != null ? reverseMatch : "[ES] " + message.trim();
        }

        String translatedToSpanish = reverseLookupToSpanish(sourceLanguage, normalizedMessage);
        if (translatedToSpanish != null) {
            Map<String, String> dictionaryByLanguage = LOCAL_DICTIONARY.get(targetLanguage);
            if (dictionaryByLanguage != null) {
                String translatedFromSpanish = dictionaryByLanguage.get(normalizeSentence(translatedToSpanish));
                if (StringUtils.hasText(translatedFromSpanish)) {
                    return translatedFromSpanish;
                }
            }
        }
        return "[" + targetLanguage.toUpperCase(Locale.ROOT) + "] " + message.trim();
    }

    private String reverseLookupToSpanish(String sourceLanguage, String normalizedMessage) {
        Map<String, String> dictionaryByLanguage = LOCAL_DICTIONARY.get(sourceLanguage);
        if (dictionaryByLanguage == null || dictionaryByLanguage.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, String> entry : dictionaryByLanguage.entrySet()) {
            if (normalizeSentence(entry.getValue()).equals(normalizedMessage)) {
                return entry.getKey();
            }
        }
        return null;
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

    private String buildCacheKey(String source, String sourceLanguage, String targetLanguage) {
        return sourceLanguage + "->" + targetLanguage + "|" + normalizeSentence(source);
    }

    private String readCache(String key) {
        CacheEntry entry = translationCache.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.expiresAtEpochSeconds < (System.currentTimeMillis() / 1000L)) {
            translationCache.remove(key, entry);
            return null;
        }
        return entry.translation;
    }

    private void writeCache(String key, String value) {
        if (!StringUtils.hasText(key) || !StringUtils.hasText(value)) {
            return;
        }
        if (translationCache.size() >= MAX_CACHE_ENTRIES) {
            pruneCache();
        }
        long expiresAtEpochSeconds = (System.currentTimeMillis() / 1000L) + cacheSeconds;
        translationCache.put(key, new CacheEntry(value, expiresAtEpochSeconds));
    }

    private void pruneCache() {
        long nowEpochSeconds = System.currentTimeMillis() / 1000L;
        translationCache.entrySet().removeIf(entry -> entry.getValue().expiresAtEpochSeconds < nowEpochSeconds);
        if (translationCache.size() < MAX_CACHE_ENTRIES) {
            return;
        }
        int dropCount = translationCache.size() - MAX_CACHE_ENTRIES + 128;
        int removed = 0;
        for (String key : translationCache.keySet()) {
            translationCache.remove(key);
            removed++;
            if (removed >= dropCount) {
                break;
            }
        }
    }

    private static final class CacheEntry {
        private final String translation;
        private final long expiresAtEpochSeconds;

        private CacheEntry(String translation, long expiresAtEpochSeconds) {
            this.translation = translation;
            this.expiresAtEpochSeconds = expiresAtEpochSeconds;
        }
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
