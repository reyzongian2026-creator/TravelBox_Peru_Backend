package com.tuempresa.storage.payments.infrastructure.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Service responsible for signing QR payment URLs using HMAC-SHA256.
 *
 * <p>Each generated signature includes a timestamp component so that signatures
 * expire after a configurable window (default 30 minutes). The signature covers
 * the reservation ID, amount, and timestamp to prevent tampering.</p>
 */
@Component
public class QrUrlSigner {

    private static final Logger log = LoggerFactory.getLogger(QrUrlSigner.class);

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String FIELD_SEPARATOR = "|";

    private final String signingKey;
    private final Duration signatureValidity;

    public QrUrlSigner(
            @Value("${app.security.qr-signing-key:default-qr-signing-key-change-me}") String signingKey,
            @Value("${app.security.qr-signature-validity-minutes:30}") long validityMinutes,
            @Value("${spring.profiles.active:local}") String activeProfiles) {
        boolean isSentinel = signingKey == null || signingKey.isBlank()
                || "replace-me-in-vault".equals(signingKey)
                || "default-qr-signing-key-change-me".equals(signingKey);
        if (isSentinel) {
            if (activeProfiles.contains("prod")) {
                throw new IllegalStateException(
                        "CRITICAL: app.security.qr-signing-key is not configured. " +
                        "Set the 'tbx-back-qr-signing-key' secret in Azure Key Vault before starting in production.");
            }
            log.warn("QrUrlSigner: signing key not configured — using ephemeral fallback. DEV/LOCAL only!");
            this.signingKey = "tbx-qr-fallback-" + System.currentTimeMillis();
        } else {
            this.signingKey = signingKey;
        }
        this.signatureValidity = Duration.ofMinutes(validityMinutes);
        log.info("QrUrlSigner initialized with signature validity of {} minutes", validityMinutes);
    }

    /**
     * Signs a QR payment URL by generating an HMAC-SHA256 signature over the
     * reservation ID, amount, and current timestamp.
     *
     * @param reservationId the unique identifier for the reservation
     * @param amount        the payment amount
     * @param qrUrl         the original QR URL to be signed
     * @return a map containing {@code qrUrl}, {@code qrSignature} (base64url),
     *         and {@code qrSignatureTimestamp} (ISO-8601 instant)
     * @throws IllegalArgumentException if any required parameter is null
     * @throws IllegalStateException    if the HMAC computation fails
     */
    public Map<String, String> signQrUrl(Long reservationId, BigDecimal amount, String qrUrl) {
        if (reservationId == null) {
            throw new IllegalArgumentException("reservationId must not be null");
        }
        if (amount == null) {
            throw new IllegalArgumentException("amount must not be null");
        }
        if (qrUrl == null || qrUrl.isBlank()) {
            throw new IllegalArgumentException("qrUrl must not be null or blank");
        }

        Instant timestamp = Instant.now();
        String timestampStr = timestamp.toString();

        String dataToSign = reservationId
                + FIELD_SEPARATOR + amount.toPlainString()
                + FIELD_SEPARATOR + timestampStr;

        String signature = computeHmacBase64Url(dataToSign);

        log.debug("Signed QR URL for reservationId={}, amount={}, timestamp={}",
                reservationId, amount, timestampStr);

        Map<String, String> result = new HashMap<>();
        result.put("qrUrl", qrUrl);
        result.put("qrSignature", signature);
        result.put("qrSignatureTimestamp", timestampStr);
        return result;
    }

    /**
     * Verifies whether a previously generated QR signature is still valid
     * (i.e., not expired and matches the expected data).
     *
     * @param reservationId the reservation ID used when signing
     * @param amount        the amount used when signing
     * @param signature     the base64url-encoded signature to verify
     * @param timestamp     the timestamp that was included in the signature
     * @return {@code true} if the signature is valid and not expired
     */
    public boolean verifySignature(Long reservationId, BigDecimal amount,
                                   String signature, String timestamp) {
        if (reservationId == null || amount == null || signature == null || timestamp == null) {
            log.warn("Signature verification failed: one or more parameters are null");
            return false;
        }

        Instant signedAt;
        try {
            signedAt = Instant.parse(timestamp);
        } catch (Exception e) {
            log.warn("Signature verification failed: invalid timestamp format '{}'", timestamp);
            return false;
        }

        // Check expiry
        if (Instant.now().isAfter(signedAt.plus(signatureValidity))) {
            log.info("Signature expired for reservationId={} (signed at {})", reservationId, timestamp);
            return false;
        }

        String dataToSign = reservationId
                + FIELD_SEPARATOR + amount.toPlainString()
                + FIELD_SEPARATOR + timestamp;

        String expectedSignature = computeHmacBase64Url(dataToSign);
        boolean valid = expectedSignature.equals(signature);

        if (!valid) {
            log.warn("Signature mismatch for reservationId={}", reservationId);
        }

        return valid;
    }

    /**
     * Returns the configured signature validity duration.
     *
     * @return the duration after which a signature is considered expired
     */
    public Duration getSignatureValidity() {
        return signatureValidity;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private String computeHmacBase64Url(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(
                    signingKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(keySpec);

            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hmacBytes);

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("HMAC-SHA256 algorithm not available", e);
        } catch (InvalidKeyException e) {
            throw new IllegalStateException("Invalid signing key for HMAC-SHA256", e);
        }
    }
}
