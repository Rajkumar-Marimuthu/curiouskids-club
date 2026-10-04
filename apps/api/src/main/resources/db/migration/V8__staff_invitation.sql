-- T-014: staff accounts are created by invitation from an admin (FR-ID-07, BR-31). The invited
-- person has no account yet, so invitations have their own table instead of verification_token.

create table staff_invitation (
    id          uuid         primary key,
    email       varchar(254) not null, -- stored lower-cased
    role        varchar(20)  not null,
    token_hash  varchar(64)  not null, -- SHA-256 hex; the token itself is never stored
    invited_by  uuid         references account (id), -- null when sent at first start
    expires_at  timestamptz  not null,
    accepted_at timestamptz,
    account_id  uuid         references account (id), -- the account the invitation created
    revoked_at  timestamptz,
    created_at  timestamptz  not null,
    updated_at  timestamptz  not null,
    constraint staff_invitation_role_ck check (role in ('VOLUNTEER', 'ADMIN')),
    constraint staff_invitation_hash_uq unique (token_hash),
    constraint staff_invitation_accepted_ck check ((accepted_at is null) = (account_id is null)),
    constraint staff_invitation_closed_ck check (accepted_at is null or revoked_at is null)
);

-- At most one open invitation per email; a new invitation revokes the previous one.
create unique index staff_invitation_open_uq on staff_invitation (lower(email))
    where accepted_at is null and revoked_at is null;

-- STAFF_INVITE tokens were planned in V3 but never issued.
alter table verification_token drop constraint verification_token_type_ck;
alter table verification_token
    add constraint verification_token_type_ck
        check (type in ('EMAIL_VERIFY', 'PASSWORD_RESET', 'EMAIL_CHANGE'));
