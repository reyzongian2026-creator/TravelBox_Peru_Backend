package com.tuempresa.storage.reservations.application.usecase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QrCodeServiceTest {

    private QrCodeService qrCodeService;

    @BeforeEach
    void setUp() {
        qrCodeService = new QrCodeService();
    }

    @Test
    void generatePng_validText_returnsNonEmptyBytes() {
        byte[] result = qrCodeService.generatePng("https://example.com/reservation/123");

        assertNotNull(result);
        assertTrue(result.length > 0);
    }

    @Test
    void generatePng_validText_returnsPngMagicBytes() {
        // PNG files start with the 8-byte signature: 89 50 4E 47 0D 0A 1A 0A
        byte[] result = qrCodeService.generatePng("TEST");

        assertEquals((byte) 0x89, result[0]);
        assertEquals((byte) 0x50, result[1]); // 'P'
        assertEquals((byte) 0x4E, result[2]); // 'N'
        assertEquals((byte) 0x47, result[3]); // 'G'
    }

    @Test
    void generateDataUrl_validText_startsWithDataImagePrefix() {
        String result = qrCodeService.generateDataUrl("https://example.com");

        assertNotNull(result);
        assertTrue(result.startsWith("data:image/png;base64,"));
    }

    @Test
    void generateDataUrl_validText_containsBase64Content() {
        String result = qrCodeService.generateDataUrl("content");

        String base64Part = result.substring("data:image/png;base64,".length());
        assertFalse(base64Part.isBlank(), "Base64 content should not be blank");
    }

    @Test
    void generatePng_shortText_producesOutput() {
        // ZXing handles very short strings without error
        byte[] result = qrCodeService.generatePng("A");

        assertNotNull(result);
        assertTrue(result.length > 0);
    }

    @Test
    void generatePng_longText_producesOutput() {
        // ZXing should handle reasonably long strings
        String longText = "https://example.com/path?param=" + "x".repeat(200);

        byte[] result = qrCodeService.generatePng(longText);

        assertNotNull(result);
        assertTrue(result.length > 0);
    }

    @Test
    void generatePng_nullText_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> qrCodeService.generatePng(null));
    }
}
