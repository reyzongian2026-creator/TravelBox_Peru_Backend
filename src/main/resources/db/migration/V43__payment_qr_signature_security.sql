-- =============================================================================
-- V43: Payment QR Signature Security
-- =============================================================================
-- Adds cryptographic QR signature verification columns to payment_attempts.
-- These columns store the hash and timestamp of the QR code signature used
-- during payment, along with the verification result and when it occurred.
-- This prevents replay attacks and ensures QR code authenticity.
-- =============================================================================

ALTER TABLE payment_attempts
    ADD COLUMN IF NOT EXISTS qr_signature_hash          VARCHAR(88),
    ADD COLUMN IF NOT EXISTS qr_signature_timestamp      BIGINT,
    ADD COLUMN IF NOT EXISTS qr_verification_success     BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS qr_verification_timestamp   TIMESTAMPTZ;

-- Index for fast lookup by QR signature hash (e.g., duplicate / replay detection)
CREATE INDEX IF NOT EXISTS idx_payment_attempts_qr_signature_hash
    ON payment_attempts (qr_signature_hash)
    WHERE qr_signature_hash IS NOT NULL;
