create table if not exists email_outbox (
    id bigserial primary key,
    created_at timestamp not null,
    updated_at timestamp not null,
    created_by varchar(255) not null default 'system',
    updated_by varchar(255) not null default 'system',
    recipient varchar(190) not null,
    subject varchar(220) not null,
    html_body text not null,
    text_body text,
    event_type varchar(80) not null,
    dedup_key varchar(200),
    status varchar(20) not null,
    provider varchar(40),
    attempt_count integer not null default 0,
    next_attempt_at timestamp,
    sent_at timestamp,
    last_attempt_at timestamp,
    last_error varchar(500)
);

create unique index if not exists ux_email_outbox_dedup_key
    on email_outbox (dedup_key)
    where dedup_key is not null;

create index if not exists ix_email_outbox_status_next_attempt
    on email_outbox (status, next_attempt_at, created_at);

