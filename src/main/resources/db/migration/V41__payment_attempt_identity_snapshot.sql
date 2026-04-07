-- V41: Add identity snapshot fields to payment_attempts for Yape reconciliation
ALTER TABLE payment_attempts
    ADD COLUMN IF NOT EXISTS expected_customer_email       VARCHAR(255),
    ADD COLUMN IF NOT EXISTS expected_customer_name        VARCHAR(255),
    ADD COLUMN IF NOT EXISTS expected_method               VARCHAR(50),
    ADD COLUMN IF NOT EXISTS manual_transfer_requested_at  TIMESTAMPTZ;
