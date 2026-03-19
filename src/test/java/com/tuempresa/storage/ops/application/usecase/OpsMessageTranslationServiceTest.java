package com.tuempresa.storage.ops.application.usecase;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpsMessageTranslationServiceTest {

    @Test
    void shouldUseLocalDictionaryForKnownEnglishSentence() {
        OpsMessageTranslationService service = new OpsMessageTranslationService(
                RestClient.builder(),
                "local",
                true,
                "https://translation.googleapis.com",
                "",
                "nmt"
        );

        String translated = service.translateFromSpanish(
                "Hola, por favor presenta tu QR para validar tu reserva y maleta.",
                "en"
        );

        assertEquals(
                "Hello, please show your QR to validate your reservation and luggage.",
                translated
        );
    }

    @Test
    void shouldFallbackToTaggedMessageWhenSentenceIsUnknown() {
        OpsMessageTranslationService service = new OpsMessageTranslationService(
                RestClient.builder(),
                "local",
                true,
                "https://translation.googleapis.com",
                "",
                "nmt"
        );

        String translated = service.translateFromSpanish("Mensaje personalizado", "de");

        assertEquals("[DE] Mensaje personalizado", translated);
    }
}
