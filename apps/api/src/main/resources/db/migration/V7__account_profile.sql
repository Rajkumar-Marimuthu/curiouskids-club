-- T-013 part 2: profile and preferences (FR-ID-06).
-- An email change waits in pending_email until the link sent to the new address is used; the
-- unique index on email still decides who gets an address. Reminder toggles cover pickup and
-- due-soon reminders only; overdue reminders cannot be switched off (BR-36).

alter table account
    add column pending_email   varchar(254),
    add column remind_pickup   boolean not null default true,
    add column remind_due_soon boolean not null default true;

alter table verification_token drop constraint verification_token_type_ck;
alter table verification_token
    add constraint verification_token_type_ck
        check (type in ('EMAIL_VERIFY', 'PASSWORD_RESET', 'STAFF_INVITE', 'EMAIL_CHANGE'));
