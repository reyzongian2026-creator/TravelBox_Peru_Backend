alter table users
    add column if not exists requires_real_email_completion boolean not null default false;

alter table users
    add column if not exists pending_real_email varchar(160);

update users
set requires_real_email_completion = true,
    email_verified = false,
    pending_real_email = null
where auth_provider = 'FACEBOOK'
  and lower(email) like '%@social.inkavoy.pe';
