-- Add missing indexes for performance

CREATE INDEX IF NOT EXISTS idx_payment_attempts_reservation_id
    ON payment_attempts(reservation_id);

CREATE INDEX IF NOT EXISTS idx_reservations_created_at
    ON reservations(created_at);

CREATE INDEX IF NOT EXISTS idx_incidents_created_at
    ON incidents(created_at);

CREATE INDEX IF NOT EXISTS idx_incidents_status
    ON incidents(status);
