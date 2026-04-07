-- =============================================================================
-- V45: Payment Confirmation Atomicity
-- =============================================================================
-- Ensures that at most ONE payment attempt per reservation can reach the
-- 'CONFIRMED' status.  This is enforced at the database level with a partial
-- unique index, which is the PostgreSQL-idiomatic way to express a conditional
-- unique constraint.
--
-- A second partial index speeds up queries that look for pending payments on
-- a given reservation (e.g., timeout / cleanup jobs).
-- =============================================================================

-- Prevent multiple confirmed payments for the same reservation.
-- Only one row per reservation_id can have status = 'CONFIRMED'.
CREATE UNIQUE INDEX IF NOT EXISTS uq_payment_attempts_reservation_confirmed
    ON payment_attempts (reservation_id)
    WHERE status = 'CONFIRMED';

-- Speed up lookups for pending payments per reservation (timeout jobs, UI).
CREATE INDEX IF NOT EXISTS idx_payment_attempts_reservation_pending
    ON payment_attempts (reservation_id, status)
    WHERE status = 'PENDING';
