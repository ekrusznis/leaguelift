-- Phase 16 slices 1-2: durable external identities for idempotent manual onboarding CSV imports.
-- The uploaded CSV itself is never persisted. Each successful imported shell/entity is bound
-- to one organization-scoped external id so re-uploading the same source updates/matches
-- instead of creating duplicates.

create table onboarding_import_identity (
    id                 uuid primary key default gen_random_uuid(),
    organization_id    uuid not null references organization (id),
    entity_type        text not null,
    external_id        text not null,
    entity_id          uuid not null,
    created_by_user_id uuid not null references app_user (id),
    created_at         timestamptz not null default now(),
    constraint onboarding_import_identity_type_check check (
        entity_type in ('TEAM', 'HOUSEHOLD', 'GUARDIAN', 'PARTICIPANT')
    ),
    constraint onboarding_import_identity_org_type_external_key
        unique (organization_id, entity_type, external_id),
    constraint onboarding_import_identity_org_type_entity_key
        unique (organization_id, entity_type, entity_id)
);

