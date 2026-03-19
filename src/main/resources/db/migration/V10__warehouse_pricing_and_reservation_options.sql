alter table warehouses
    add column price_per_hour_small numeric(12,2) not null default 4.00;
alter table warehouses
    add column price_per_hour_medium numeric(12,2) not null default 4.50;
alter table warehouses
    add column price_per_hour_large numeric(12,2) not null default 5.50;
alter table warehouses
    add column price_per_hour_extra_large numeric(12,2) not null default 6.50;
alter table warehouses
    add column pickup_fee numeric(12,2) not null default 14.00;
alter table warehouses
    add column dropoff_fee numeric(12,2) not null default 14.00;
alter table warehouses
    add column insurance_fee numeric(12,2) not null default 7.50;

alter table reservations
    add column bag_size varchar(20) not null default 'MEDIUM';
alter table reservations
    add column pickup_requested boolean not null default false;
alter table reservations
    add column dropoff_requested boolean not null default false;
alter table reservations
    add column extra_insurance boolean not null default false;
alter table reservations
    add column storage_amount numeric(12,2) not null default 0.00;
alter table reservations
    add column pickup_fee numeric(12,2) not null default 0.00;
alter table reservations
    add column dropoff_fee numeric(12,2) not null default 0.00;
alter table reservations
    add column insurance_fee numeric(12,2) not null default 0.00;
