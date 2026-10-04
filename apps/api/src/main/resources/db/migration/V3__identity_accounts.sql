-- T-011: families, accounts and single-use tokens, owned by the identity module
-- (docs/architecture/domain-model.md, FR-ID-01, FR-ID-02).

create table family (
    id           uuid         primary key,
    display_name varchar(100) not null, -- the registering parent's name
    phone        varchar(30),
    flagged_at   timestamptz,
    created_at   timestamptz  not null,
    updated_at   timestamptz  not null,
    version      bigint       not null default 0
);

create table account (
    id                uuid         primary key,
    email             varchar(254) not null,
    password_hash     varchar(255) not null,
    role              varchar(20)  not null,
    family_id         uuid         references family (id),
    email_verified_at timestamptz,
    status            varchar(20)  not null default 'ACTIVE',
    consent_version   varchar(20),
    consent_at        timestamptz,
    created_at        timestamptz  not null,
    updated_at        timestamptz  not null,
    version           bigint       not null default 0,
    constraint account_role_ck check (role in ('MEMBER', 'VOLUNTEER', 'ADMIN')),
    constraint account_status_ck check (status in ('ACTIVE', 'DISABLED')),
    -- Members belong to a family; staff accounts have none.
    constraint account_family_ck check ((role = 'MEMBER') = (family_id is not null)),
    -- BR-28: members record which terms they accepted, and when.
    constraint account_consent_ck check (
        role <> 'MEMBER' or (consent_version is not null and consent_at is not null))
);

-- Email is case-insensitive unique (stored lower-cased; the index also guards direct SQL).
create unique index account_email_uq on account (lower(email));
create index account_family_idx on account (family_id);

create table verification_token (
    id         uuid        primary key,
    account_id uuid        not null references account (id),
    type       varchar(20) not null,
    token_hash varchar(64) not null, -- SHA-256 hex; the token itself is never stored
    expires_at timestamptz not null,
    used_at    timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint verification_token_type_ck
        check (type in ('EMAIL_VERIFY', 'PASSWORD_RESET', 'STAFF_INVITE')),
    constraint verification_token_hash_uq unique (token_hash)
);

create index verification_token_account_idx on verification_token (account_id, type);

-- T-010 left this open until accounts existed.
alter table notification_outbox
    add constraint notification_outbox_account_fk
    foreign key (recipient_account_id) references account (id);
