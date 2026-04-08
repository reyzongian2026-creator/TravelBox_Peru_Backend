-- =============================================================================
-- V50: Add client_idempotency_key to payment_attempts
-- =============================================================================
-- The entity field was introduced in V45 changes but the column was never
-- created.  This fixes the missing-column error that causes 500 on any
-- query touching the payment_attempts table (including /payments/intents).
-- =============================================================================

ALTER TABLE payment_attempts
    ADD COLUMN IF NOT EXISTS client_idempotency_key VARCHAR(36);

-- Partial unique index (NULLs allowed — only non-null values are unique).
CREATE UNIQUE INDEX IF NOT EXISTS uk_payment_attempts_client_idempotency_key
    ON payment_attempts (client_idempotency_key)
    WHERE client_idempotency_key IS NOT NULL;
