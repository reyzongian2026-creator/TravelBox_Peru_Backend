-- V32: Performance indexes for frequently queried columns

-- Composite index for pessimistic-lock lookup: findByReservationIdAndStatusForUpdate
CREATE INDEX IF NOT EXISTS idx_payment_attempts_res_status
    ON payment_attempts(reservation_id, status);

-- FK on checkin_records.reservation_id has no supporting index
CREATE INDEX IF NOT EXISTS idx_checkin_records_reservation
    ON checkin_records(reservation_id);

-- Status-based queries on delivery_orders (timeout scheduler, admin filters)
CREATE INDEX IF NOT EXISTS idx_delivery_orders_status
    ON delivery_orders(status);
