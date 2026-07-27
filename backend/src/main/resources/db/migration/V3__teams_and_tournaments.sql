-- Phase 1, vertical slices 3+4: team and tournament creation.
-- Slug columns for public pages are intentionally absent here — those are
-- added in the next slice when the public_page model ships (section 35, slice 5).

create table team (
    id              uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organization (id),
    name            text not null,
    sport           text not null,
    season          text,
    status          text not null default 'ACTIVE',
    contact_email   text,
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    constraint team_status_check check (status in ('ACTIVE', 'ARCHIVED')),
    constraint team_org_name_key unique (organization_id, name)
);

create index team_organization_id_idx on team (organization_id);

create table tournament (
    id              uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organization (id),
    name            text not null,
    sport           text,
    status          text not null default 'ACTIVE',
    start_date      date,
    end_date        date,
    location        text,
    contact_email   text,
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    constraint tournament_status_check check (status in ('ACTIVE', 'ARCHIVED')),
    constraint tournament_org_name_key unique (organization_id, name)
);

create index tournament_organization_id_idx on tournament (organization_id);
