alter table reservations
    add column if not exists late_pickup_surcharge numeric(12,2) not null default 0.00;
