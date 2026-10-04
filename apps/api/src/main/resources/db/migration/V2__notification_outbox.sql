-- T-010: transactional email outbox, owned by the notification module (FR-NOT-03).
-- Rows are inserted in the business transaction and sent later by the outbox dispatcher.

create table notification_outbox (
    id                   uuid         primary key,
    type                 varchar(50)  not null,
    recipient_account_id uuid,                     -- foreign key to account added with the table (T-011)
    recipient_email      varchar(320),             -- cleared 30 days after sending
    payload              jsonb        not null default '{}'::jsonb,
    dedupe_key           varchar(200) not null,
    status               varchar(20)  not null default 'PENDING',
    attempts             integer      not null default 0,
    next_attempt_at      timestamptz  not null,
    sent_at              timestamptz,
    last_error           varchar(200),             -- exception type only, never message text
    created_at           timestamptz  not null,
    updated_at           timestamptz  not null,
    constraint notification_outbox_dedupe_key_uq unique (dedupe_key), -- DB-09
    constraint notification_outbox_status_ck check (status in ('PENDING', 'SENT', 'FAILED')),
    constraint notification_outbox_attempts_ck check (attempts between 0 and 5),
    constraint notification_outbox_sent_ck check ((status = 'SENT') = (sent_at is not null)),
    constraint notification_outbox_recipient_ck check (status = 'SENT' or recipient_email is not null)
);

-- The dispatcher's query: due PENDING rows in order.
create index notification_outbox_due_idx on notification_outbox (next_attempt_at)
    where status = 'PENDING';

-- Admin dashboard and alarm: FAILED rows.
create index notification_outbox_failed_idx on notification_outbox (created_at)
    where status = 'FAILED';
