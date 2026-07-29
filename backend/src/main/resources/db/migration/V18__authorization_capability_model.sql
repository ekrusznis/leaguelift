-- Phase 7: capability-based authorization model (DESIGN-DOC.md section 4.2, ADR-020).
--
-- Additive, not a replacement: organization_membership (V1) remains the source of
-- truth for the ORGANIZATION context. This migration adds the two tables the
-- capability model needs for the contexts organization_membership cannot answer:
--
--   * role_assignment      -- resource-scoped roles for TEAM, TOURNAMENT, and PLATFORM
--                              contexts, plus a narrowly-scoped PARTICIPANT self-link
--                              context (an athlete's own controlled login, see ADR-020
--                              and db/seed's dashboard-role fixture for the existing
--                              precedent this formalizes).
--   * guardian_relationship -- the real FK-backed replacement for
--                              HouseholdRepository.findActiveAdultByEmail's interim
--                              email-matching heuristic (DESIGN-DOC.md section 8.3).

create table role_assignment (
    id              uuid primary key default gen_random_uuid(),
    organization_id uuid references organization (id),
    user_id         uuid not null references app_user (id),
    context_type    text not null,
    resource_id     uuid,
    role            text not null,
    status          text not null default 'ACTIVE',
    granted_by      uuid references app_user (id),
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    constraint role_assignment_context_type_check check (context_type in ('TEAM', 'TOURNAMENT', 'PLATFORM', 'PARTICIPANT')),
    constraint role_assignment_status_check check (status in ('ACTIVE', 'REVOKED')),
    constraint role_assignment_role_check check (role in (
        'COACH_READ', 'TEAM_EDITOR', 'TEAM_MANAGER',
        'TOURNAMENT_VIEWER', 'TOURNAMENT_ADMINISTRATOR',
        'PLATFORM_ADMINISTRATOR',
        'ATHLETE_SELF'
    )),
    -- PLATFORM is not organization-owned (a platform admin isn't scoped to one org);
    -- every other context type must carry both an organization and a resource id.
    constraint role_assignment_scope_presence_check check (
        (context_type = 'PLATFORM' and resource_id is null and organization_id is null)
        or (context_type <> 'PLATFORM' and resource_id is not null and organization_id is not null)
    )
);

-- coalesce(...,'00000000...') lets the partial unique index also cover the PLATFORM
-- row (resource_id null) without a separate index.
create unique index role_assignment_unique_active_idx
    on role_assignment (user_id, context_type, coalesce(resource_id, '00000000-0000-0000-0000-000000000000'::uuid), role)
    where status = 'ACTIVE';
create index role_assignment_user_idx on role_assignment (user_id) where status = 'ACTIVE';
create index role_assignment_org_idx on role_assignment (organization_id) where status = 'ACTIVE';
create index role_assignment_resource_idx on role_assignment (context_type, resource_id) where status = 'ACTIVE';

create table guardian_relationship (
    id                 uuid primary key default gen_random_uuid(),
    organization_id    uuid not null references organization (id),
    household_id       uuid not null references household (id),
    household_adult_id uuid not null references household_adult (id),
    user_id            uuid not null references app_user (id),
    status             text not null default 'ACTIVE',
    created_at         timestamptz not null default now(),
    updated_at         timestamptz not null default now(),
    constraint guardian_relationship_status_check check (status in ('ACTIVE', 'REVOKED'))
);

create unique index guardian_relationship_unique_active_idx
    on guardian_relationship (user_id, household_adult_id) where status = 'ACTIVE';
create index guardian_relationship_household_idx on guardian_relationship (household_id) where status = 'ACTIVE';
create index guardian_relationship_organization_idx on guardian_relationship (organization_id) where status = 'ACTIVE';
