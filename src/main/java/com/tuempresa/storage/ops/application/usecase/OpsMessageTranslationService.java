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
        en.put("hola, por favor presenta tu qr para validar tu reserva y maleta.", "Hello, please show your QR to validate your reservation and luggage.");
        en.put("tu entrega fue validada. comparte este pin con el operador o courier para completar.", "Your handoff was validated. Share this PIN with the operator or courier to complete.");
        en.put("el operador necesita aprobar la entrega. te avisaremos cuando este listo.", "The operator needs to approve the handoff. We will notify you when it is ready.");
        en.put("tu reserva esta lista para recojo. presenta tu qr y pin de seguridad.", "Your reservation is ready for pickup. Show your QR and security PIN.");
        en.put("aprobacion requerida para continuar.", "Approval required to continue.");
        en.put("escanea el qr de la reserva.", "Scan the reservation QR code.");
        en.put("validando reserva...", "Validating reservation...");
        en.put("etiquetando equipaje...", "Tagging luggage...");
        en.put("capturando fotos del equipaje.", "Capturing luggage photos.");
        en.put("almacenando reserva.", "Storing reservation.");
        en.put("generando pin de seguridad.", "Generating security PIN.");
        en.put("preparando entrega.", "Preparing delivery.");
        en.put("entrega completada.", "Delivery completed.");
        en.put("reserva no encontrada.", "Reservation not found.");
        en.put("codigo qr invalido.", "Invalid QR code.");
        en.put("la reserva ya fue procesada.", "Reservation already processed.");
        en.put("error al procesar la reserva.", "Error processing reservation.");
        en.put("operacion cancelada.", "Operation cancelled.");
        en.put("falta permisos para esta accion.", "Missing permissions for this action.");
        byLanguage.put("en", Map.copyOf(en));

        Map<String, String> pt = new LinkedHashMap<>();
        pt.put("hola, por favor presenta tu qr para validar tu reserva y maleta.", "Ola, por favor apresente seu QR para validar sua reserva e bagagem.");
        pt.put("tu entrega fue validada. comparte este pin con el operador o courier para completar.", "Sua entrega foi validada. Compartilhe este PIN com o operador ou courier para concluir.");
        pt.put("el operador necesita aprobar la entrega. te avisaremos cuando este listo.", "O operador precisa aprovar a entrega. Avisaremos quando estiver pronto.");
        pt.put("tu reserva esta lista para recojo. presenta tu qr y pin de seguridad.", "Sua reserva esta pronta para retirada. Apresente seu QR e PIN de seguranca.");
        pt.put("aprobacion requerida para continuar.", "Aprovacao necessaria para continuar.");
        pt.put("escanea el qr de la reserva.", "Escaneie o QR da reserva.");
        pt.put("validando reserva...", "Validando reserva...");
        pt.put("etiquetando equipaje...", "Etiquetando bagagem...");
        pt.put("capturando fotos del equipaje.", "Capturando fotos da bagagem.");
        pt.put("almacenando reserva.", "Armazenando reserva.");
        pt.put("generando pin de seguridad.", "Gerando PIN de seguranca.");
        pt.put("preparando entrega.", "Preparando entrega.");
        pt.put("entrega completada.", "Entrega concluida.");
        pt.put("reserva no encontrada.", "Reserva nao encontrada.");
        pt.put("codigo qr invalido.", "Codigo QR invalido.");
        pt.put("la reserva ya fue procesada.", "A reserva ja foi processada.");
        pt.put("error al procesar la reserva.", "Erro ao processar reserva.");
        pt.put("operacion cancelada.", "Operacao cancelada.");
        pt.put("falta permisos para esta accion.", "Permissoes insuficientes para esta acao.");
        byLanguage.put("pt", Map.copyOf(pt));

        Map<String, String> de = new LinkedHashMap<>();
        de.put("hola, por favor presenta tu qr para validar tu reserva y maleta.", "Hallo, bitte zeigen Sie Ihren QR-Code zur Validierung Ihrer Reservierung und Ihres Gepacks.");
        de.put("tu entrega fue validada. comparte este pin con el operador o courier para completar.", "Ihre Ubergabe wurde validiert. Teilen Sie diesen PIN mit dem Operator oder Kurier zur Fertigstellung.");
        de.put("el operador necesita aprobar la entrega. te avisaremos cuando este listo.", "Der Operator muss die Ubergabe genehmigen. Wir benachrichtigen Sie, wenn es bereit ist.");
        de.put("tu reserva esta lista para recojo. presenta tu qr y pin de seguridad.", "Ihre Reservierung ist zur Abholung bereit. Zeigen Sie Ihren QR-Code und Sicherheits-PIN.");
        de.put("aprobacion requerida para continuar.", "Genehmigung erforderlich um fortzufahren.");
        de.put("escanea el qr de la reserva.", "Scannen Sie den Reservierungs-QR-Code.");
        de.put("validando reserva...", "Validiere Reservierung...");
        de.put("etiquetando equipaje...", "Etikettiere Gepack...");
        de.put("capturando fotos del equipaje.", "Mache Fotos vom Gepack.");
        de.put("almacenando reserva.", "Speichere Reservierung.");
        de.put("generando pin de seguridad.", "Generiere Sicherheits-PIN.");
        de.put("preparando entrega.", "Bereite Ubergabe vor.");
        de.put("entrega completada.", "Ubergabe abgeschlossen.");
        de.put("reserva no encontrada.", "Reservierung nicht gefunden.");
        de.put("codigo qr invalido.", "Ungultiger QR-Code.");
        de.put("la reserva ya fue procesada.", "Reservierung wurde bereits verarbeitet.");
        de.put("error al procesar la reserva.", "Fehler bei der Reservierungsverarbeitung.");
        de.put("operacion cancelada.", "Vorgang abgebrochen.");
        de.put("falta permisos para esta accion.", "Erforderliche Berechtigungen fehlen.");
        byLanguage.put("de", Map.copyOf(de));

        Map<String, String> fr = new LinkedHashMap<>();
        fr.put("hola, por favor presenta tu qr para validar tu reserva y maleta.", "Bonjour, veuillez presenter votre QR pour valider votre reservation et vos bagages.");
        fr.put("tu entrega fue validada. comparte este pin con el operador o courier para completar.", "Votre remise a ete validee. Partagez ce PIN avec l'operateur ou le coursier pour terminer.");
        fr.put("el operador necesita aprobar la entrega. te avisaremos cuando este listo.", "L'operateur doit approuver la remise. Nous vous informerons lorsqu'elle sera prete.");
        fr.put("tu reserva esta lista para recojo. presenta tu qr y pin de seguridad.", "Votre reservation est prete pour le retrait. Apresentez votre QR et votre code PIN.");
        fr.put("aprobacion requerida para continuar.", "Approbation requise pour continuer.");
        fr.put("escanea el qr de la reserva.", "Scannez le code QR de la reservation.");
        fr.put("validando reserva...", "Validation de la reservation...");
        fr.put("etiquetando equipaje...", "Etiquetage des bagages...");
        fr.put("capturando fotos del equipaje.", "Capture de photos des bagages.");
        fr.put("almacenando reserva.", "Stockage de la reservation.");
        fr.put("generando pin de seguridad.", "Generation du code PIN de securite.");
        fr.put("preparando entrega.", "Preparation de la remise.");
        fr.put("entrega completada.", "Remise terminee.");
        fr.put("reserva no encontrada.", "Reservation non trouvee.");
        fr.put("codigo qr invalido.", "Code QR invalide.");
        fr.put("la reserva ya fue procesada.", "La reservation a deja ete traitee.");
        fr.put("error al procesar la reserva.", "Erreur lors du traitement de la reservation.");
        fr.put("operacion cancelada.", "Operation annulee.");
        fr.put("falta permisos para esta accion.", "Permissions insuffisantes pour cette action.");
        byLanguage.put("fr", Map.copyOf(fr));

        Map<String, String> it = new LinkedHashMap<>();
        it.put("hola, por favor presenta tu qr para validar tu reserva y maleta.", "Ciao, per favore mostra il tuo QR per validare la tua prenotazione e il bagaglio.");
        it.put("tu entrega fue validada. comparte este pin con el operador o courier para completar.", "La tua consegna e stata validata. Condividi questo PIN con l'operatore o il corriere per completare.");
        it.put("el operador necesita aprobar la entrega. te avisaremos cuando este listo.", "L'operatore deve approvare la consegna. Ti avviseremo quando sara pronto.");
        it.put("tu reserva esta lista para recojo. presenta tu qr y pin de seguridad.", "La tua prenotazione e pronta per il ritiro. Mostra il tuo QR e il codice PIN di sicurezza.");
        it.put("aprobacion requerida para continuar.", "Approvazione richiesta per continuare.");
        it.put("escanea el qr de la reserva.", "Scansiona il codice QR della prenotazione.");
        it.put("validando reserva...", "Validazione della prenotazione...");
        it.put("etiquetando equipaje...", "Etichettatura del bagaglio...");
        it.put("capturando fotos del equipaje.", "Acquisizione foto del bagaglio.");
        it.put("almacenando reserva.", "Memorizzazione della prenotazione.");
        it.put("generando pin de seguridad.", "Generazione del PIN di sicurezza.");
        it.put("preparando entrega.", "Preparazione della consegna.");
        it.put("entrega completada.", "Consegna completata.");
        it.put("reserva no encontrada.", "Prenotazione non trovata.");
        it.put("codigo qr invalido.", "Codice QR non valido.");
        it.put("la reserva ya fue procesada.", "La prenotazione e stata gia elaborata.");
        it.put("error al procesar la reserva.", "Errore durante l'elaborazione della prenotazione.");
        it.put("operacion cancelada.", "Operazione annullata.");
        it.put("falta permisos para esta accion.", "Autorizzazioni insufficienti per questa azione.");
        byLanguage.put("it", Map.copyOf(it));

        return Map.copyOf(byLanguage);
    }
}