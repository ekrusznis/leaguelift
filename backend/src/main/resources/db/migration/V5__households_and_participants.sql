-- Phase 2: household family units, guardian contacts, participant records,
-- and participant-to-team assignments.

create table household (
    id              uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organization (id),
    display_name    text not null,
    contact_email   text,
    contact_phone   text,
    notes           text,
    status          text not null default 'ACTIVE',
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    constraint household_status_check check (status in ('ACTIVE', 'ARCHIVED'))
);

create index household_organization_id_idx on household (organization_id);

create table household_adult (
    id              uuid primary key default gen_random_uuid(),
    household_id    uuid not null references household (id),
    organization_id uuid not null references organization (id),
    first_name      text not null,
    last_name       text not null,
    email           text,
    phone           text,
    relationship    text,
    is_primary      boolean not null default false,
    status          text not null default 'ACTIVE',
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    constraint household_adult_status_check check (status in ('ACTIVE', 'ARCHIVED'))
);

create index household_adult_household_id_idx on household_adult (household_id);
create index household_adult_organization_id_idx on household_adult (organization_id);

create table participant (
    id              uuid primary key default gen_random_uuid(),
    household_id    uuid not null references household (id),
    organization_id uuid not null references organization (id),
    first_name      text not null,
    last_name       text not null,
    date_of_birth   date,
    notes           text,
    status          text not null default 'ACTIVE',
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    constraint participant_status_check check (status in ('ACTIVE', 'INACTIVE', 'ARCHIVED'))
);

create index participant_household_id_idx on participant (household_id);
create index participant_organization_id_idx on participant (organization_id);

create table participant_team (
    id              uuid primary key default gen_random_uuid(),
    participant_id  uuid not null references participant (id),
    team_id         uuid not null references team (id),
    organization_id uuid not null references organization (id),
    status          text not null default 'ACTIVE',
    joined_at       date,
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    constraint participant_team_status_check check (status in ('ACTIVE', 'INACTIVE')),
    constraint participant_team_unique unique (participant_id, team_id)
);

create index participant_team_participant_id_idx on participant_team (participant_id);
create index participant_team_team_id_idx on participant_team (team_id);
