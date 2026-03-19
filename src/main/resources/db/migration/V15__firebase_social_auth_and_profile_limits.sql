alter table users
    add column if not exists auth_provider varchar(40) not null default 'LOCAL';

alter table users
    add column if not exists firebase_uid varchar(160);

alter table users
    add column if not exists managed_by_admin boolean not null default false;

alter table users
    add column if not exists vehicle_plate varchar(30);

alter table users
    add column if not exists email_change_count integer not null default 0;

alter table users
    add column if not exists phone_change_count integer not null default 0;

alter table users
    add column if not exists document_change_count integer not null default 0;

create unique index if not exists uk_users_firebase_uid
    on users(firebase_uid);
