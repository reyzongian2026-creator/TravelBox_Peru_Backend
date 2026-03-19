alter table reservations
    add column if not exists estimated_items integer not null default 1;
