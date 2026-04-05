-- V31: Add cancellation/refund policy support
-- New columns on payment_attempts for idempotency, booking classification, and optimistic locking
-- New column on reservations for booking type classification
-- New cancellation_records table for full audit trail

-- ============================================================================
-- payment_attempts: new columns
-- ============================================================================
ALTER TABLE payment_attempts ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;
ALTER TABLE payment_attempts ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(80);
ALTER TABLE payment_attempts ADD COLUMN IF NOT EXISTS provider_fee_amount NUMERIC(12,2);
ALTER TABLE payment_attempts ADD COLUMN IF NOT EXISTS booking_type VARCHAR(20);
ALTER TABLE payment_attempts ADD COLUMN IF NOT EXISTS cancellation_policy_applied VARCHAR(30);
ALTER TABLE payment_attempts ADD COLUMN IF NOT EXISTS payment_method_label VARCHAR(60);
ALTER TABLE payment_attempts ADD COLUMN IF NOT EXISTS confirmed_at TIMESTAMPTZ;

-- Unique constraint on idempotency_key (allowing nulls for existing rows)
CREATE UNIQUE INDEX IF NOT EXISTS uk_payment_attempts_idempotency_key
    ON payment_attempts (idempotency_key) WHERE idempotency_key IS NOT NULL;

-- ============================================================================
-- reservations: booking_type column
-- ============================================================================
ALTER TABLE reservations ADD COLUMN IF NOT EXISTS booking_type VARCHAR(20);

-- ============================================================================
-- cancellation_records: new audit trail table
-- ============================================================================
CREATE TABLE IF NOT EXISTS cancellation_records (
    id                           BIGSERIAL PRIMARY KEY,
    reservation_id               BIGINT       NOT NULL,
    payment_attempt_id           BIGINT,
    idempotency_key              VARCHAR(80)  NOT NULL UNIQUE,
    booking_type                 VARCHAR(20)  NOT NULL,
    policy_type                  VARCHAR(30)  NOT NULL,
    policy_window                VARCHAR(200),
    gross_paid_amount            NUMERIC(12,2) NOT NULL,
    cancellation_penalty_amount  NUMERIC(12,2) NOT NULL,
    refund_amount_to_customer    NUMERIC(12,2) NOT NULL,
    retained_amount_by_business  NUMERIC(12,2) NOT NULL,
    provider_fee_amount          NUMERIC(12,2),
    provider_fee_refundable      BOOLEAN DEFAULT FALSE,
    payment_method_surcharge_amount NUMERIC(12,2),
    net_business_loss            NUMERIC(12,2),
    status                       VARCHAR(30)  NOT NULL,
    refund_provider_reference    VARCHAR(120),
    refund_provider_message      VARCHAR(500),
    reason                       VARCHAR(500),
    actor_user_id                BIGINT,
    actor_role                   VARCHAR(40),
    reservation_start_at         TIMESTAMPTZ,
    payment_confirmed_at         TIMESTAMPTZ,
    requested_at                 TIMESTAMPTZ  NOT NULL,
    completed_at                 TIMESTAMPTZ,
    previous_reservation_status  VARCHAR(40),
    previous_payment_status      VARCHAR(30),
    created_at                   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at                   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by                   VARCHAR(255),
    updated_by                   VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_cancellation_records_reservation
    ON cancellation_records (reservation_id);
CREATE INDEX IF NOT EXISTS idx_cancellation_records_status
    ON cancellation_records (status);
