alter table qr_handoff_cases
    add column if not exists pickup_pin_preview varchar(12);

alter table stored_item_evidences
    add column if not exists bag_unit_index integer;

alter table stored_item_evidences
    add column if not exists locked boolean not null default false;

create index if not exists idx_stored_item_evidences_reservation_type
    on stored_item_evidences(reservation_id, type);

create index if not exists idx_stored_item_evidences_reservation_bag
    on stored_item_evidences(reservation_id, bag_unit_index);
