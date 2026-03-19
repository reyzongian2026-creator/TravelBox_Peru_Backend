alter table payment_attempts
    add column if not exists refund_amount numeric(12,2);

alter table payment_attempts
    add column if not exists refund_fee numeric(12,2);

alter table payment_attempts
    add column if not exists refund_reason varchar(500);

alter table payment_attempts
    add column if not exists refunded_at timestamp with time zone;

