-- V51: Performance indexes for high-frequency query columns
-- Applied 2026-04 — missing indexes causing full table scans on common filters

-- Reservations: most common filters
CREATE INDEX IF NOT EXISTS idx_reservations_user_status
    ON reservations(user_id, status);

CREATE INDEX IF NOT EXISTS idx_reservations_warehouse_status
    ON reservations(warehouse_id, status);

CREATE INDEX IF NOT EXISTS idx_reservations_created_at
    ON reservations(created_at DESC);

-- Payment attempts: by reservation and status
CREATE INDEX IF NOT EXISTS idx_payment_attempts_reservation
    ON payment_attempts(reservation_id);

CREATE INDEX IF NOT EXISTS idx_payment_attempts_status_date
    ON payment_attempts(status, created_at DESC);

-- Notification records: by user and date (most common query pattern)
CREATE INDEX IF NOT EXISTS idx_notification_records_user_created
    ON notification_records(user_id, created_at DESC);

-- Incidents: by reservation and status
CREATE INDEX IF NOT EXISTS idx_incidents_reservation
    ON incidents(reservation_id);

CREATE INDEX IF NOT EXISTS idx_incidents_status
    ON incidents(status);

-- Payment webhook events: by processing status for scheduler queries
CREATE INDEX IF NOT EXISTS idx_webhook_events_status_date
    ON payment_webhook_events(processing_status, received_at DESC);

-- Delivery orders: by status for assignment scheduler
CREATE INDEX IF NOT EXISTS idx_delivery_orders_status
    ON delivery_orders(status);
