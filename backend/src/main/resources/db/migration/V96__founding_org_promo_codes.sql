-- Founding Organization pilot (founder-directed, 2026-08-20): a small number of
-- single-use promo codes let a hand-picked org register with free Club-tier
-- (FOUNDING_CLUB) access for a 90-day pilot, without going through Stripe
-- Checkout. `reserved_*` is set at registration time (before the org exists) to
-- prevent two concurrent registrations from racing on the same unredeemed code;
-- `organization_id`/`redeemed_at`/`pilot_ends_at` are only set once the org
-- actually activates the pilot (owner-onboarding/founding-activate).

create table founding_org_promo_code (
    id uuid primary key default gen_random_uuid(),
    code text not null unique,
    reserved_by_user_id uuid references app_user(id),
    reserved_at timestamptz,
    organization_id uuid references organization(id),
    redeemed_at timestamptz,
    pilot_ends_at timestamptz,
    pilot_status text not null default 'UNREDEEMED'
        check (pilot_status in ('UNREDEEMED', 'RESERVED', 'ACTIVE', 'CONVERTED', 'EXPIRED')),
    next_reminder_index int not null default 0
        check (next_reminder_index >= 0),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index founding_org_promo_code_organization_id_idx
    on founding_org_promo_code (organization_id)
    where organization_id is not null;

create index founding_org_promo_code_pilot_status_idx on founding_org_promo_code (pilot_status);
