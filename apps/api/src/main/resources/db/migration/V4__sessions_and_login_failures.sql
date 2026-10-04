-- T-012: server-side sessions (Spring Session JDBC, ADR-0004) and failed-login tracking (FR-ID-03).

-- Spring Session's standard PostgreSQL schema (spring-session-jdbc schema-postgresql.sql).
-- Framework-owned like shedlock, so it keeps the library's column names. PRINCIPAL_NAME holds the
-- account ID, never the email, so sessions can be revoked per account without personal data here.
create table spring_session (
    primary_id            char(36)     not null,
    session_id            char(36)     not null,
    creation_time         bigint       not null,
    last_access_time      bigint       not null,
    max_inactive_interval int          not null,
    expiry_time           bigint       not null,
    principal_name        varchar(100),
    constraint spring_session_pk primary key (primary_id)
);

create unique index spring_session_ix1 on spring_session (session_id);
create index spring_session_ix2 on spring_session (expiry_time);
create index spring_session_ix3 on spring_session (principal_name);

create table spring_session_attributes (
    session_primary_id char(36)     not null,
    attribute_name     varchar(200) not null,
    attribute_bytes    bytea        not null,
    constraint spring_session_attributes_pk primary key (session_primary_id, attribute_name),
    constraint spring_session_attributes_fk foreign key (session_primary_id)
        references spring_session (primary_id) on delete cascade
);

-- One row per failed login. Only hashes of the email and the client address are kept, and rows
-- are deleted after a day. Counted per email (5 in 15 minutes) and per address (20 in 15 minutes).
create table login_failure (
    id         uuid        primary key,
    email_hash varchar(64) not null,
    ip_hash    varchar(64) not null,
    at         timestamptz not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index login_failure_email_idx on login_failure (email_hash, at desc);
create index login_failure_ip_idx on login_failure (ip_hash, at desc);
create index login_failure_at_idx on login_failure (at);
