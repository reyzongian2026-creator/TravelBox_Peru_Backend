alter table users
    add column if not exists email_verification_code_hash varchar(128);

alter table users
    add column if not exists password_reset_code_hash varchar(128);
