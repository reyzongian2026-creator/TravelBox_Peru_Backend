-- V51: Performance indexes for high-frequency query columns.
-- Guard every index because some environments may not carry each optional
-- table yet and Flyway must stay forward-compatible.

DO $$
BEGIN
    IF to_regclass('public.reservations') IS NOT NULL THEN
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_reservations_user_status
                 ON reservations(user_id, status)';
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_reservations_warehouse_status
                 ON reservations(warehouse_id, status)';
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_reservations_created_at
                 ON reservations(created_at DESC)';
    END IF;

    IF to_regclass('public.payment_attempts') IS NOT NULL THEN
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_payment_attempts_reservation
                 ON payment_attempts(reservation_id)';
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_payment_attempts_status_date
                 ON payment_attempts(status, created_at DESC)';
    END IF;

    IF to_regclass('public.notifications') IS NOT NULL THEN
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_notifications_user_created
                 ON notifications(user_id, created_at DESC)';
    END IF;

    IF to_regclass('public.incidents') IS NOT NULL THEN
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_incidents_reservation
                 ON incidents(reservation_id)';
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_incidents_status
                 ON incidents(status)';
    END IF;

    IF to_regclass('public.payment_webhook_events') IS NOT NULL THEN
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_webhook_events_status_date
                 ON payment_webhook_events(processing_status, received_at DESC)';
    END IF;

    IF to_regclass('public.delivery_orders') IS NOT NULL THEN
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_delivery_orders_status
                 ON delivery_orders(status)';
    END IF;
END
$$;
