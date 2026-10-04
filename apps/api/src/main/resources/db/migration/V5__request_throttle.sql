-- T-012: hourly request limits for registration and emailed links (FR-ID-04, security-and-privacy.md).
-- One row per request, keyed by a hash of the email or client address; rows are deleted after a day.

create table request_throttle (
    id         uuid        primary key,
    action     varchar(20) not null,
    key_hash   varchar(80) not null,
    at         timestamptz not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint request_throttle_action_ck check (action in ('REGISTER', 'EMAIL_LINK'))
);

create index request_throttle_key_idx on request_throttle (action, key_hash, at desc);
create index request_throttle_at_idx on request_throttle (at);
