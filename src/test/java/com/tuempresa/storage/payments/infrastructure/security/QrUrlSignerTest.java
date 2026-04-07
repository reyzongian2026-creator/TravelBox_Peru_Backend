package com.tuempresa.storage.payments.infrastructure.security;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QrUrlSignerTest {

    @Test
    void shouldSignAndVerifyQrPayload() {
        QrUrlSigner signer = new QrUrlSigner("test-signing-key", 30);

        Map<String, String> signed = signer.signQrUrl(99L, new BigDecimal("120.50"), "https://example.com/qr");

        assertEquals("https://example.com/qr", signed.get("qrUrl"));
        assertNotNull(signed.get("qrSignature"));
        assertNotNull(signed.get("qrSignatureTimestamp"));
        assertTrue(signer.verifySignature(
                99L,
                new BigDecimal("120.50"),
                signed.get("qrSignature"),
                signed.get("qrSignatureTimestamp")
        ));
    }

    @Test
    void shouldRejectTamperedAmountOrTimestamp() {
        QrUrlSigner signer = new QrUrlSigner("test-signing-key", 30);
        Map<String, String> signed = signer.signQrUrl(99L, new BigDecimal("120.50"), "https://example.com/qr");

        assertFalse(signer.verifySignature(
                99L,
                new BigDecimal("120.51"),
                signed.get("qrSignature"),
                signed.get("qrSignatureTimestamp")
        ));
        assertFalse(signer.verifySignature(
                99L,
                new BigDecimal("120.50"),
                signed.get("qrSignature"),
                "not-an-instant"
        ));
    }

    @Test
    void shouldRejectExpiredSignature() throws InterruptedException {
        QrUrlSigner signer = new QrUrlSigner("test-signing-key", 0);
        Map<String, String> signed = signer.signQrUrl(99L, new BigDecimal("120.50"), "https://example.com/qr");

        Thread.sleep(5L);

        assertFalse(signer.verifySignature(
                99L,
                new BigDecimal("120.50"),
                signed.get("qrSignature"),
                signed.get("qrSignatureTimestamp")
        ));
    }

    @Test
    void shouldRejectInvalidArgumentsOnSign() {
        QrUrlSigner signer = new QrUrlSigner("test-signing-key", 30);

        assertThrows(IllegalArgumentException.class,
                () -> signer.signQrUrl(null, new BigDecimal("10.00"), "https://example.com/qr"));
        assertThrows(IllegalArgumentException.class,
                () -> signer.signQrUrl(1L, null, "https://example.com/qr"));
        assertThrows(IllegalArgumentException.class,
                () -> signer.signQrUrl(1L, new BigDecimal("10.00"), " "));
    }
}
