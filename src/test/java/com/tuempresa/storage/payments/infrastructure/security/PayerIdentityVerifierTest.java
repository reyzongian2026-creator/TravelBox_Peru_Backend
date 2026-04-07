package com.tuempresa.storage.payments.infrastructure.security;

import com.tuempresa.storage.payments.infrastructure.security.PayerIdentityVerifier.IdentityVerificationResult;
import com.tuempresa.storage.payments.infrastructure.security.PayerIdentityVerifier.MatchStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayerIdentityVerifierTest {

    @Test
    void shouldReturnExactMatchForNormalizedNames() {
        PayerIdentityVerifier verifier = new PayerIdentityVerifier(0.95);

        IdentityVerificationResult result = verifier.verifyIdentity(
                "José Pérez",
                "987654321",
                "Jose Perez",
                "payer@example.com",
                null
        );

        assertEquals(MatchStatus.EXACT_MATCH, result.status());
        assertEquals(1.0, result.confidence());
        assertTrue(result.reason().contains("Exact"));
    }

    @Test
    void shouldReturnFuzzyMatchWhenWordsOverlapAndPhoneMatches() {
        PayerIdentityVerifier verifier = new PayerIdentityVerifier(0.95);

        IdentityVerificationResult result = verifier.verifyIdentity(
                "Maria Fernanda Lopez",
                "+51 999 111 222",
                "Maria Lopez",
                "payer@example.com",
                "999111222"
        );

        assertEquals(MatchStatus.FUZZY, result.status());
        assertEquals(0.95, result.confidence(), 0.001);
        assertTrue(result.reason().contains("Phone"));
    }

    @Test
    void shouldReturnNoMatchWhenIdentityDataDoesNotAlign() {
        PayerIdentityVerifier verifier = new PayerIdentityVerifier(0.95);

        IdentityVerificationResult result = verifier.verifyIdentity(
                "Carlos Mendoza",
                "999111222",
                "Lucia Torres",
                "payer@example.com",
                "900000000"
        );

        assertEquals(MatchStatus.NO_MATCH, result.status());
        assertTrue(result.confidence() <= 0.0);
        assertTrue(result.reason().contains("Phone mismatch"));
    }

    @Test
    void shouldRejectMissingExpectedIdentity() {
        PayerIdentityVerifier verifier = new PayerIdentityVerifier(0.95);

        IdentityVerificationResult result = verifier.verifyIdentity(
                null,
                null,
                "Jose Perez",
                "payer@example.com",
                "999111222"
        );

        assertEquals(MatchStatus.NO_MATCH, result.status());
        assertEquals(0.0, result.confidence());
        assertTrue(result.reason().contains("Missing expected identity"));
    }
}
