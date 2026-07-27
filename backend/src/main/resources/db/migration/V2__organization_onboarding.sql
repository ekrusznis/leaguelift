-- Phase 1, vertical slice 1: organization onboarding (DESIGN-DOC.md section 35 #1).
-- Adds organization profile fields (sports, contact info) and adult administrator
-- invitations. Logo/cover-image storage is deferred to the next slice ("File uploads
-- and branding") so this migration doesn't add asset columns that nothing populates
-- yet.

-- sports is a small, order-preserving list of free-form sport names with no need for
-- per-sport relational queries yet, so jsonb (a JSON array of strings) is used here
-- rather than a text[] column, to avoid JDBC array-binding complexity for a field
-- this simple. Revisit as a proper table if sports ever need their own attributes.
alter table organization
    add column sports jsonb not null default '[]'::jsonb,
    add column contact_email text,
    add column contact_phone text;

create table invitation (
    id                  uuid primary key default gen_random_uuid(),
    organization_id     uuid not null references organization (id),
    email               text not null,
    role                text not null,
    status              text not null default 'PENDING',
    invited_by_user_id  uuid not null references app_user (id),
    token               text not null,
    expires_at          timestamptz not null,
    accepted_at         timestamptz,
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now(),
    constraint invitation_role_check check (role in (
        'ADMINISTRATOR', 'FINANCE_MANAGER', 'TEAM_ADMINISTRATOR',
        'TOURNAMENT_ADMINISTRATOR', 'VIEWER'
    )),
    -- OWNER is deliberately not an invitable role here: ownership transfer is its own
    -- controlled workflow (DESIGN-DOC.md section 7.2), not a generic invitation.
    constraint invitation_status_check check (status in ('PENDING', 'ACCEPTED', 'REVOKED', 'EXPIRED')),
    constraint invitation_token_key unique (token)
);

create index invitation_organization_id_idx on invitation (organization_id);
create index invitation_email_idx on invitation (email);

-- At most one PENDING invitation per (organization, email) at a time.
create unique index invitation_pending_org_email_key
    on invitation (organization_id, email)
    where status = 'PENDING';
