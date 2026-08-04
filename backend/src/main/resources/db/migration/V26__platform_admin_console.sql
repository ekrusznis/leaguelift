-- Phase 14: Rally26 employee Platform Admin support console.
--
-- The platform role is intentionally distinct from organization ADMINISTRATOR.
-- Rename the persisted role to match the public/application context name exactly:
-- PLATFORM_ADMIN. Platform access remains a separately granted PLATFORM role_assignment
-- and is never inferred from email address or organization membership.

alter table role_assignment drop constraint role_assignment_role_check;

update role_assignment
set role = 'PLATFORM_ADMIN', updated_at = now()
where role = 'PLATFORM_ADMINISTRATOR';

-- Historical local-only seed V9001 is immutable and still inserts the legacy value.
-- On a clean local database it runs after the real migrations (V9000+), so canonicalize
-- that input before constraints are evaluated. The legacy value is never persisted and
-- never recognized by application authorization.
create function canonicalize_platform_admin_role()
returns trigger
language plpgsql
as $$
begin
    if new.context_type = 'PLATFORM' and new.role = 'PLATFORM_ADMINISTRATOR' then
        new.role := 'PLATFORM_ADMIN';
    end if;
    return new;
end;
$$;

create trigger role_assignment_platform_admin_role_canonicalizer
before insert or update of context_type, role on role_assignment
for each row execute function canonicalize_platform_admin_role();

alter table role_assignment add constraint role_assignment_role_check check (role in (
    'COACH_READ', 'TEAM_EDITOR', 'TEAM_MANAGER',
    'TOURNAMENT_VIEWER', 'TOURNAMENT_ADMINISTRATOR',
    'PLATFORM_ADMIN',
    'ATHLETE_SELF'
));

-- Prevent a PLATFORM row from carrying a tenant/resource role (or vice versa).
-- This makes PLATFORM_ADMIN an exact employee grant, not merely any active row
-- whose context_type happens to be PLATFORM.
alter table role_assignment add constraint role_assignment_context_role_check check (
    (context_type = 'TEAM' and role in ('COACH_READ', 'TEAM_EDITOR', 'TEAM_MANAGER'))
    or (context_type = 'TOURNAMENT' and role in ('TOURNAMENT_VIEWER', 'TOURNAMENT_ADMINISTRATOR'))
    or (context_type = 'PLATFORM' and role = 'PLATFORM_ADMIN')
    or (context_type = 'PARTICIPANT' and role = 'ATHLETE_SELF')
);

-- A support-access session is not impersonation. The Rally26 employee remains the
-- authenticated actor, supplies a reason, and receives time-bounded access to one
-- organization workspace. Every start/end/expiry event is auditable, and organization
-- API requests made by a Platform Admin must carry this session id.
create table platform_support_access (
    id                      uuid primary key default gen_random_uuid(),
    platform_admin_user_id  uuid not null references app_user (id),
    organization_id         uuid not null references organization (id),
    reason                  text not null,
    status                  text not null default 'ACTIVE',
    expires_at              timestamptz not null,
    ended_at                timestamptz,
    created_at              timestamptz not null default now(),
    constraint platform_support_access_status_check check (status in ('ACTIVE', 'ENDED', 'EXPIRED')),
    constraint platform_support_access_reason_check check (char_length(trim(reason)) between 10 and 500)
);

create index platform_support_access_admin_idx
    on platform_support_access (platform_admin_user_id, status, expires_at);
create index platform_support_access_org_idx
    on platform_support_access (organization_id, status, expires_at);
create unique index platform_support_access_one_active_per_admin_idx
    on platform_support_access (platform_admin_user_id)
    where status = 'ACTIVE';
