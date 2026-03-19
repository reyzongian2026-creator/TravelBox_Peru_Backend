create table if not exists user_warehouse_access (
    user_id bigint not null references users(id) on delete cascade,
    warehouse_id bigint not null references warehouses(id) on delete cascade,
    primary key (user_id, warehouse_id)
);

create index if not exists idx_user_warehouse_access_warehouse
    on user_warehouse_access(warehouse_id, user_id);
