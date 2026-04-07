-- V40: Missing FK indexes and composite indexes for performance

-- FK index on qr_handoff_cases.reservation_id
CREATE INDEX IF NOT EXISTS idx_qr_handoff_cases_reservation
    ON qr_handoff_cases(reservation_id);

-- FK index on checkout_records.reservation_id
CREATE INDEX IF NOT EXISTS idx_checkout_records_reservation
    ON checkout_records(reservation_id);

-- FK index on stored_item_evidences.reservation_id
CREATE INDEX IF NOT EXISTS idx_stored_item_evidence_reservation
    ON stored_item_evidences(reservation_id);

-- Composite index for reservation expiration scheduler
CREATE INDEX IF NOT EXISTS idx_reservations_status_expires_at
    ON reservations(status, expires_at)
    WHERE status = 'PENDING_PAYMENT' AND expires_at IS NOT NULL;

-- Index for user email lookup (login, assisted reservation)
CREATE INDEX IF NOT EXISTS idx_users_email_lower
    ON users(LOWER(email));

-- Index for reservation status + user_id (my reservations)
CREATE INDEX IF NOT EXISTS idx_reservations_user_status
    ON reservations(user_id, status);

-- Index for notification status filtering
CREATE INDEX IF NOT EXISTS idx_notifications_user_status
    ON notifications(user_id, status);
