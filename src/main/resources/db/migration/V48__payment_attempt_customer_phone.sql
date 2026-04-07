-- V48: Add expected_customer_phone to payment_attempts for enhanced identity verification
ALTER TABLE payment_attempts
    ADD COLUMN IF NOT EXISTS expected_customer_phone VARCHAR(30);
