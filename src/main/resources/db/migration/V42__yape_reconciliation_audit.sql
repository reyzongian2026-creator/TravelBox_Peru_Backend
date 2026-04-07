-- V42: Create yape_reconciliation_audit table for structured reconciliation traceability
CREATE TABLE IF NOT EXISTS yape_reconciliation_audit (
    id                  BIGSERIAL PRIMARY KEY,
    payment_attempt_id  BIGINT REFERENCES payment_attempts(id),
    email_amount        NUMERIC(12, 2),
    sender_name         VARCHAR(255),
    sender_email        VARCHAR(255),
    tx_date_time_raw    VARCHAR(100),
    received_at         TIMESTAMPTZ NOT NULL,
    message_id          VARCHAR(500) NOT NULL,
    subject             VARCHAR(500),
    outcome             VARCHAR(30)  NOT NULL,
    match_reason        TEXT,
    matched_fields      VARCHAR(200),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_yape_audit_attempt_id ON yape_reconciliation_audit(payment_attempt_id);
CREATE INDEX IF NOT EXISTS idx_yape_audit_message_id ON yape_reconciliation_audit(message_id);
