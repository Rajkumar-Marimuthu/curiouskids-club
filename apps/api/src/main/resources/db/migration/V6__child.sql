-- T-013: child profiles (FR-ID-05). Only a first name or nickname and an age band (BR-30);
-- at most limits.max-children per family (BR-15), enforced in ChildService under a family row lock.

create table child (
    id         uuid        primary key,
    family_id  uuid        not null references family (id) on delete cascade,
    first_name varchar(40) not null,
    age_band   varchar(5)  not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version    bigint      not null default 0,
    constraint child_first_name_ck check (length(btrim(first_name)) > 0),
    constraint child_age_band_ck check (age_band in ('0-2', '3-5', '6-8', '9-12', '13+'))
);

create index child_family_idx on child (family_id, created_at);
