-- =============================================================================
-- V44: Payment Identity Verification
-- =============================================================================
-- Adds payer identity columns to payment_attempts so the system can correlate
-- the person who initiated the Yape/QR payment with the reservation owner.
--   - payer_phone_number: raw phone used for the transfer
--   - payer_identity_hash: SHA-256(phone + name) for deterministic matching
--   - identity_verification_confidence: 0.00 – 1.00 confidence score
--   - identity_verification_status: classification of the match result
-- =============================================================================

ALTER TABLE payment_attempts
    ADD COLUMN IF NOT EXISTS payer_phone_number                VARCHAR(20),
    ADD COLUMN IF NOT EXISTS payer_identity_hash               VARCHAR(88),
    ADD COLUMN IF NOT EXISTS identity_verification_confidence   DECIMAL(3,2),
    ADD COLUMN IF NOT EXISTS identity_verification_status       VARCHAR(30);

COMMENT ON COLUMN payment_attempts.payer_identity_hash IS 'SHA-256 hash of phone + name for deterministic identity matching';
COMMENT ON COLUMN payment_attempts.identity_verification_confidence IS 'Confidence score between 0.00 and 1.00';
COMMENT ON COLUMN payment_attempts.identity_verification_status IS 'EXACT_MATCH | FUZZY | MANUAL_REVIEW';

-- Index for lookups / fraud checks by phone number
CREATE INDEX IF NOT EXISTS idx_payment_attempts_payer_phone_number
    ON payment_attempts (payer_phone_number)
    WHERE payer_phone_number IS NOT NULL;
