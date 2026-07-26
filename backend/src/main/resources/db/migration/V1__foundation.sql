-- Foundation schema: identity, organizations, memberships, audit, outbox.
-- See DESIGN-DOC.md sections 14.3 and 14.1 for the governing rules:
--   * UUID primary keys, timestamptz timestamps.
--   * Explicit foreign keys and constraints.
--   * jsonb reserved for flexible metadata, not core relational structure.
--   * No floating-point money columns exist yet in this migration.

create extension if not exists pgcrypto;

create table app_user (
    id               uuid primary key default gen_random_uuid(),
    external_subject text not null,
    email            text not null,
    display_name     text not null,
    status           text not null default 'ACTIVE',
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now(),
    constraint app_user_status_check check (status in ('ACTIVE', 'SUSPENDED')),
    constraint app_user_external_subject_key unique (external_subject),
    constraint app_user_email_key unique (email)
);

create table organization (
    id                uuid primary key default gen_random_uuid(),
    name              text not null,
    slug              text not null,
    organization_type text not null,
    status            text not null default 'ACTIVE',
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    constraint organization_slug_key unique (slug),
    constraint organization_status_check check (status in ('ACTIVE', 'SUSPENDED', 'ARCHIVED')),
    constraint organization_type_check check (organization_type in (
        'RECREATIONAL_LEAGUE', 'TRAVEL_CLUB', 'INDIVIDUAL_TEAM', 'TOURNAMENT_OPERATOR',
        'BOOSTER_ORGANIZATION', 'MULTISPORT_FACILITY', 'COMMUNITY_PROGRAM', 'OTHER'
    )),
    constraint organization_slug_format_check check (slug ~ '^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$')
);

create table organization_membership (
    id              uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organization (id),
    user_id         uuid not null references app_user (id),
    role            text not null,
    status          text not null default 'ACTIVE',
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    constraint organization_membership_org_user_key unique (organization_id, user_id),
    constraint organization_membership_role_check check (role in (
        'OWNER', 'ADMINISTRATOR', 'FINANCE_MANAGER', 'TEAM_ADMINISTRATOR',
        'TOURNAMENT_ADMINISTRATOR', 'VIEWER'
    )),
    constraint organization_membership_status_check check (status in ('ACTIVE', 'INVITED', 'REVOKED'))
);

create index organization_membership_user_id_idx on organization_membership (user_id);
create index organization_membership_organization_id_idx on organization_membership (organization_id);

create table audit_event (
    id              uuid primary key default gen_random_uuid(),
    actor_user_id   uuid references app_user (id),
    organization_id uuid references organization (id),
    action          text not null,
    entity_type     text not null,
    entity_id       uuid not null,
    metadata        jsonb not null default '{}'::jsonb,
    created_at      timestamptz not null default now()
);

create index audit_event_organization_id_idx on audit_event (organization_id);
create index audit_event_entity_idx on audit_event (entity_type, entity_id);
create index audit_event_created_at_idx on audit_event (created_at);

create table outbox_event (
    id              uuid primary key default gen_random_uuid(),
    aggregate_type  text not null,
    aggregate_id    uuid not null,
    organization_id uuid references organization (id),
    event_type      text not null,
    schema_version  integer not null default 1,
    payload         jsonb not null,
    status          text not null default 'PENDING',
    attempt_count   integer not null default 0,
    available_at    timestamptz not null default now(),
    processed_at    timestamptz,
    last_error      text,
    created_at      timestamptz not null default now(),
    constraint outbox_event_status_check check (status in ('PENDING', 'PROCESSING', 'PROCESSED', 'FAILED', 'DEAD_LETTER'))
);

create index outbox_event_status_available_at_idx on outbox_event (status, available_at);
create index outbox_event_aggregate_idx on outbox_event (aggregate_type, aggregate_id);
