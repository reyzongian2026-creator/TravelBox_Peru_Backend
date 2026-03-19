alter table delivery_orders
    add column if not exists assigned_courier_id bigint references users(id);

create index if not exists idx_delivery_orders_assigned_courier
    on delivery_orders(assigned_courier_id, updated_at desc);
