create table qr_handoff_cases (
    id bigserial primary key,
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now(),
    created_by varchar(255) not null default 'system',
    updated_by varchar(255) not null default 'system',
    reservation_id bigint not null unique references reservations(id),
    customer_language varchar(10) not null default 'es',
    customer_qr_payload varchar(180) not null,
    stage varchar(50) not null,
    bag_tag_id varchar(80) unique,
    bag_tag_qr_payload varchar(220),
    bag_units integer not null default 1,
    pickup_pin_hash varchar(120),
    pin_expires_at timestamp with time zone,
    pin_attempt_count integer not null default 0,
    pin_locked_until timestamp with time zone,
    identity_validated boolean not null default false,
    luggage_matched boolean not null default false,
    operator_approval_requested boolean not null default false,
    operator_approval_granted boolean not null default false,
    latest_message_for_customer varchar(320),
    latest_message_translated varchar(320),
    delivery_completed boolean not null default false,
    delivered_at timestamp with time zone
);

create index idx_qr_handoff_cases_reservation on qr_handoff_cases(reservation_id);
create index idx_qr_handoff_cases_stage on qr_handoff_cases(stage);

create table qr_handoff_approvals (
    id bigserial primary key,
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now(),
    created_by varchar(255) not null default 'system',
    updated_by varchar(255) not null default 'system',
    reservation_id bigint not null references reservations(id),
    requested_by_user_id bigint not null references users(id),
    approved_by_user_id bigint references users(id),
    status varchar(30) not null,
    message_for_operator varchar(260) not null,
    message_for_customer varchar(320),
    message_for_customer_translated varchar(320),
    approved_at timestamp with time zone
);

create index idx_qr_handoff_approvals_reservation on qr_handoff_approvals(reservation_id);
create index idx_qr_handoff_approvals_status on qr_handoff_approvals(status);
