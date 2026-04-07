-- =============================================================================
-- V47: Rate Limiting Audit
-- =============================================================================
-- Records every rate-limit violation so the ops team can detect abuse patterns,
-- tune thresholds, and feed data into alerting pipelines.
-- =============================================================================

CREATE TABLE IF NOT EXISTS rate_limit_violations (
    id              BIGSERIAL       PRIMARY KEY,
    user_id         BIGINT          NOT NULL
        REFERENCES users (id) ON DELETE CASCADE,
    endpoint        VARCHAR(120)    NOT NULL,
    attempt_count   INT             NOT NULL,
    limit_threshold INT             NOT NULL,
    source_ip       VARCHAR(45),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

COMMENT ON TABLE rate_limit_violations IS 'Audit log of rate-limit violations per user and endpoint';
COMMENT ON COLUMN rate_limit_violations.source_ip IS 'Supports both IPv4 and IPv6 (max 45 chars)';

-- Primary query pattern: "violations by user X on endpoint Y, newest first"
CREATE INDEX IF NOT EXISTS idx_rate_limit_violations_user_endpoint_created
    ON rate_limit_violations (user_id, endpoint, created_at DESC);

-- Supports scheduled cleanup jobs that purge old rows by date
CREATE INDEX IF NOT EXISTS idx_rate_limit_violations_created_at
    ON rate_limit_violations (created_at);
