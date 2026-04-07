-- =============================================================================
-- V46: Amount Validation Limits
-- =============================================================================
-- Introduces two tables that support configurable amount-validation rules:
--
-- 1. amount_validation_config  – key/value pairs for limits and thresholds
--    (e.g., DAILY_LIMIT = 5000.00, MIN_AMOUNT = 1.00).
-- 2. amount_validation_audit   – per-attempt log of every validation check,
--    recording whether it passed or failed and the relevant amounts.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Table: amount_validation_config
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS amount_validation_config (
    id              BIGSERIAL       PRIMARY KEY,
    config_key      VARCHAR(60)     NOT NULL UNIQUE,
    config_value    DECIMAL(12,2)   NOT NULL,
    effective_from  TIMESTAMPTZ     NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

COMMENT ON TABLE amount_validation_config IS 'Key/value configuration for payment amount validation rules';

-- -----------------------------------------------------------------------------
-- Table: amount_validation_audit
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS amount_validation_audit (
    id                  BIGSERIAL       PRIMARY KEY,
    payment_attempt_id  BIGINT          NOT NULL
        REFERENCES payment_attempts (id) ON DELETE CASCADE,
    validation_type     VARCHAR(30)     NOT NULL,
    result              VARCHAR(20)     NOT NULL,
    amount              DECIMAL(12,2)   NOT NULL,
    limit_value         DECIMAL(12,2),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

COMMENT ON TABLE amount_validation_audit IS 'Audit trail of amount validation checks per payment attempt';
COMMENT ON COLUMN amount_validation_audit.validation_type IS 'EXACT_MATCH | DAILY_LIMIT | BOUNDS | PROMO';
COMMENT ON COLUMN amount_validation_audit.result IS 'PASSED | FAILED';

-- Index for joining back to a specific payment attempt
CREATE INDEX IF NOT EXISTS idx_amount_validation_audit_payment_attempt_id
    ON amount_validation_audit (payment_attempt_id);

-- Index for time-range queries / cleanup jobs
CREATE INDEX IF NOT EXISTS idx_amount_validation_audit_created_at
    ON amount_validation_audit (created_at);
