alter table users
    add column if not exists password_reset_code varchar(20);

alter table users
    add column if not exists password_reset_expires_at timestamp;
