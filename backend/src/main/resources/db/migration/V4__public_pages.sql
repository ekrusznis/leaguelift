-- Phase 1 slice 5: public page draft/publish workflow.
-- One page per entity (organization, team, tournament) enforced by unique(entity_id).
-- Slug uniqueness is enforced case-insensitively on the application layer and by unique index.

create table public_page (
    id              uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organization (id),
    page_type       text not null,
    entity_id       uuid not null,
    slug            text not null,
    title           text not null,
    summary         text,
    status          text not null default 'DRAFT',
    published_at    timestamptz,
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    constraint public_page_status_check check (status in ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    constraint public_page_type_check check (page_type in ('ORGANIZATION', 'TEAM', 'TOURNAMENT')),
    constraint public_page_entity_unique unique (entity_id)
);

create unique index public_page_slug_idx on public_page (lower(slug));
create index public_page_organization_id_idx on public_page (organization_id);
