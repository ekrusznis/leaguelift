-- Phase 1 remainder: media asset pipeline (DESIGN-DOC.md section 11, ADR-012).
-- First real slice covers organization logo + cover image only; team/tournament
-- logos are an intentional fast-follow that reuses these same three tables — the
-- entity_type/usage_slot check constraints below are deliberately narrow (only what
-- this slice needs) and are widened with a later ALTER TABLE ... when that slice
-- begins, per DESIGN-DOC.md section 20.1 (build only what the active milestone needs).
--
-- media_variant exists per the design doc's three-table model but no application
-- code writes to it yet in this slice (no resizing/derivatives are generated —
-- DESIGN-DOC.md section 11.3 fast-follow). It's created now so the eventual variant
-- writer doesn't need a schema migration to land.
--
-- Reads always go through a backend-issued short-lived signed GET URL (see
-- MediaReadService) rather than a public-read bucket ACL — "originals always
-- private" is a hard rule in section 11.3 and this slice has no public "variant"
-- object to front with a CDN yet. See ADR-012.

create table media_asset (
    id                     uuid primary key default gen_random_uuid(),
    organization_id        uuid not null references organization (id),
    uploaded_by_user_id    uuid not null references app_user (id),
    intended_usage_slot    text not null,
    original_file_name     text not null,
    declared_content_type  text not null,
    detected_content_type  text,
    storage_key            text not null,
    byte_size              bigint,
    checksum_sha256        text,
    width_px               integer,
    height_px              integer,
    status                 text not null default 'PENDING_UPLOAD',
    rejection_reason       text,
    created_at             timestamptz not null default now(),
    updated_at             timestamptz not null default now(),
    constraint media_asset_status_check check (status in
        ('PENDING_UPLOAD', 'UPLOADED', 'PROCESSING', 'READY', 'FAILED', 'REJECTED', 'ARCHIVED')),
    constraint media_asset_usage_slot_check check (intended_usage_slot in ('LOGO', 'COVER')),
    constraint media_asset_storage_key_unique unique (storage_key)
);

create index media_asset_organization_id_idx on media_asset (organization_id);
create index media_asset_uploaded_by_user_id_idx on media_asset (uploaded_by_user_id);

create table media_variant (
    id              uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organization (id),
    asset_id        uuid not null references media_asset (id),
    variant_name    text not null,
    storage_key     text not null,
    content_type    text not null,
    byte_size       bigint not null,
    width_px        integer,
    height_px       integer,
    created_at      timestamptz not null default now(),
    constraint media_variant_asset_variant_unique unique (asset_id, variant_name),
    constraint media_variant_storage_key_unique unique (storage_key)
);

create index media_variant_organization_id_idx on media_variant (organization_id);
create index media_variant_asset_id_idx on media_variant (asset_id);

create table media_assignment (
    id                 uuid primary key default gen_random_uuid(),
    organization_id    uuid not null references organization (id),
    asset_id           uuid not null references media_asset (id),
    entity_type        text not null,
    entity_id          uuid not null,
    usage_slot         text not null,
    publication_status text not null default 'PRIVATE',
    visibility         text not null,
    alt_text           text,
    created_at         timestamptz not null default now(),
    updated_at         timestamptz not null default now(),
    constraint media_assignment_entity_type_check check (entity_type in ('ORGANIZATION')),
    constraint media_assignment_usage_slot_check check (usage_slot in ('LOGO', 'COVER')),
    constraint media_assignment_publication_status_check check (publication_status in
        ('PRIVATE', 'APPROVED', 'PUBLISHED', 'RETIRED')),
    constraint media_assignment_visibility_check check (visibility in
        ('PUBLIC', 'AUTHENTICATED', 'ORGANIZATION_PRIVATE', 'TEAM_PRIVATE',
         'HOUSEHOLD_PRIVATE', 'SELF_PRIVATE', 'PLATFORM_PRIVATE'))
);

create index media_assignment_organization_id_idx on media_assignment (organization_id);
create index media_assignment_asset_id_idx on media_assignment (asset_id);
-- Enforces "at most one active assignment per entity+slot" at the DB level while
-- still allowing full RETIRED history to accumulate.
create unique index media_assignment_active_slot_idx on media_assignment (entity_type, entity_id, usage_slot)
    where publication_status <> 'RETIRED';
