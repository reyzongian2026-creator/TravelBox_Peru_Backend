package com.tuempresa.storage.payments.infrastructure.security;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service that performs strict payer identity verification by comparing
 * expected identity fields (name, phone) against the values reported
 * by the payment provider.
 *
 * <p>Name matching uses Unicode-aware normalization (accent removal) and
 * supports exact, word-based, and substring strategies. Phone matching
 * extracts the Peruvian country code (+51) before comparison.</p>
 */
@Component
public class PayerIdentityVerifier {

    private static final Logger log = LoggerFactory.getLogger(PayerIdentityVerifier.class);

    /** Pattern to strip combining diacritical marks after NFD normalization. */
    private static final Pattern DIACRITICS_PATTERN =
            Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    /** Minimum number of matching words required for word-based fuzzy matching. */
    private static final int MIN_WORD_MATCH_COUNT = 2;

    private final double confidenceThreshold;

    public PayerIdentityVerifier(
            @Value("${app.payments.identity-verification.confidence-threshold:0.95}")
            double confidenceThreshold) {
        this.confidenceThreshold = confidenceThreshold;
        log.info("PayerIdentityVerifier initialized with confidence threshold {}", confidenceThreshold);
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Verifies the payer's identity by comparing expected name/phone against
     * the sender information provided by the payment gateway.
     *
     * @param expectedName  the name associated with the reservation
     * @param expectedPhone the phone number associated with the reservation
     * @param senderName    the sender's name as reported by the payment provider
     * @param senderEmail   the sender's email (currently used for logging only)
     * @param senderPhone   the sender's phone number (may be {@code null})
     * @return an {@link IdentityVerificationResult} describing the match outcome
     */
    public IdentityVerificationResult verifyIdentity(String expectedName,
                                                     String expectedPhone,
                                                     String senderName,
                                                     String senderEmail,
                                                     String senderPhone) {

        // Guard: missing expected identity data
        if (StringUtils.isBlank(expectedName) || StringUtils.isBlank(expectedPhone)) {
            log.warn("Missing expected identity data (name={}, phone={})", expectedName, expectedPhone);
            return new IdentityVerificationResult(
                    0.0, MatchStatus.NO_MATCH, "Missing expected identity");
        }

        // Guard: missing sender name
        if (StringUtils.isBlank(senderName)) {
            log.warn("Sender name is blank; cannot perform identity verification");
            return new IdentityVerificationResult(
                    0.0, MatchStatus.NO_MATCH, "Missing sender name from payment provider");
        }

        // ---- Name matching ----
        String normExpectedName = normalizeName(expectedName);
        String normSenderName = normalizeName(senderName);

        NameMatchResult nameResult = matchNames(normExpectedName, normSenderName);
        log.debug("Name match: expected='{}', sender='{}', result={}",
                normExpectedName, normSenderName, nameResult);

        // ---- Phone matching ----
        PhoneMatchResult phoneResult = matchPhones(expectedPhone, senderPhone);
        log.debug("Phone match: expected='{}', sender='{}', result={}",
                expectedPhone, senderPhone, phoneResult);

        // ---- Composite decision ----
        return computeCompositeResult(nameResult, phoneResult, senderEmail);
    }

    /**
     * Returns the configured confidence threshold below which a match
     * is rejected.
     *
     * @return the confidence threshold (0.0 - 1.0)
     */
    public double getConfidenceThreshold() {
        return confidenceThreshold;
    }

    // =========================================================================
    // Result types
    // =========================================================================

    /**
     * Immutable result of an identity verification check.
     *
     * @param confidence a value between 0.0 and 1.0
     * @param status     the overall match status
     * @param reason     a human-readable explanation
     */
    public record IdentityVerificationResult(
            double confidence,
            MatchStatus status,
            String reason
    ) {}

    /** Possible outcomes for identity verification. */
    public enum MatchStatus {
        EXACT_MATCH,
        FUZZY,
        NO_MATCH
    }

    // =========================================================================
    // Internal types
    // =========================================================================

    private record NameMatchResult(double confidence, MatchStatus status, String reason) {}

    private record PhoneMatchResult(boolean matched, boolean available, String reason) {}

    // =========================================================================
    // Name matching
    // =========================================================================

    private NameMatchResult matchNames(String expected, String sender) {
        // 1. Exact match
        if (expected.equals(sender)) {
            return new NameMatchResult(1.0, MatchStatus.EXACT_MATCH, "Exact name match");
        }

        // 2. Word-based match (at least MIN_WORD_MATCH_COUNT common words)
        Set<String> expectedWords = tokenize(expected);
        Set<String> senderWords = tokenize(sender);

        long commonWords = expectedWords.stream()
                .filter(senderWords::contains)
                .count();

        if (commonWords >= MIN_WORD_MATCH_COUNT) {
            return new NameMatchResult(0.85, MatchStatus.FUZZY,
                    "Word-based match (" + commonWords + " common words)");
        }

        // 3. Contains check (one-way)
        if (sender.contains(expected) || expected.contains(sender)) {
            return new NameMatchResult(0.70, MatchStatus.FUZZY,
                    "Substring match");
        }

        // 4. No match
        return new NameMatchResult(0.0, MatchStatus.NO_MATCH,
                "Name does not match");
    }

    // =========================================================================
    // Phone matching
    // =========================================================================

    private PhoneMatchResult matchPhones(String expected, String sender) {
        if (StringUtils.isBlank(sender)) {
            return new PhoneMatchResult(false, false, "Sender phone not provided");
        }

        String normExpected = normalizePhone(expected);
        String normSender = normalizePhone(sender);

        if (normExpected.equals(normSender)) {
            return new PhoneMatchResult(true, true, "Phone exact match");
        }

        return new PhoneMatchResult(false, true, "Phone mismatch");
    }

    // =========================================================================
    // Composite result
    // =========================================================================

    private IdentityVerificationResult computeCompositeResult(NameMatchResult nameResult,
                                                              PhoneMatchResult phoneResult,
                                                              String senderEmail) {
        // EXACT_MATCH: name exact + (phone exact OR phone absent)
        if (nameResult.status() == MatchStatus.EXACT_MATCH
                && (phoneResult.matched() || !phoneResult.available())) {

            double confidence = phoneResult.matched() ? 1.0 : nameResult.confidence();
            return new IdentityVerificationResult(
                    confidence,
                    MatchStatus.EXACT_MATCH,
                    buildReason(nameResult, phoneResult));
        }

        // FUZZY: name fuzzy + identity reasonable (phone matches or not available)
        if (nameResult.status() == MatchStatus.FUZZY
                && (phoneResult.matched() || !phoneResult.available())) {

            double confidence = phoneResult.matched()
                    ? Math.min(nameResult.confidence() + 0.10, 1.0)
                    : nameResult.confidence();

            return new IdentityVerificationResult(
                    confidence,
                    MatchStatus.FUZZY,
                    buildReason(nameResult, phoneResult));
        }

        // NO_MATCH: anything else
        double confidence = nameResult.confidence();
        String reason = buildReason(nameResult, phoneResult);

        // Downgrade if phone was available and mismatched
        if (phoneResult.available() && !phoneResult.matched()) {
            confidence = Math.max(confidence - 0.30, 0.0);
            reason += " | Phone mismatch reduces confidence";
        }

        return new IdentityVerificationResult(confidence, MatchStatus.NO_MATCH, reason);
    }

    // =========================================================================
    // Normalization helpers
    // =========================================================================

    /**
     * Normalizes a name by lowering case, trimming, collapsing whitespace,
     * and removing diacritical marks (e.g., a with acute -> a).
     */
    private String normalizeName(String name) {
        if (name == null) {
            return "";
        }
        String normalized = Normalizer.normalize(name.trim().toLowerCase(), Normalizer.Form.NFD);
        normalized = DIACRITICS_PATTERN.matcher(normalized).replaceAll("");
        // Collapse multiple spaces
        normalized = normalized.replaceAll("\\s+", " ");
        return normalized;
    }

    /**
     * Normalizes a phone number by stripping all non-digit characters and
     * removing the Peruvian country code prefix (+51 / 51).
     */
    private String normalizePhone(String phone) {
        if (phone == null) {
            return "";
        }
        String digits = phone.replaceAll("[^0-9]", "");
        // Strip leading Peruvian country code (51)
        if (digits.startsWith("51") && digits.length() > 9) {
            digits = digits.substring(2);
        }
        return digits;
    }

    private Set<String> tokenize(String text) {
        return Arrays.stream(text.split("\\s+"))
                .filter(w -> !w.isBlank())
                .collect(Collectors.toSet());
    }

    private String buildReason(NameMatchResult nameResult, PhoneMatchResult phoneResult) {
        return nameResult.reason() + " | " + phoneResult.reason();
    }
}
