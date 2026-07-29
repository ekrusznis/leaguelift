-- Stripe Connect Express onboarding scaffolding (ADR-005, Phase 2 remainder).
-- This slice only covers connected-account onboarding (an org clicks "Connect
-- payouts", completes Stripe's hosted flow, we store the resulting account status) —
-- no live charge routing yet, which DESIGN-DOC.md section 16 explicitly gates behind
-- this ADR and reserves for Phase 5.
--
-- Fields mirror Stripe's own account object (stripe_account_id, details_submitted,
-- charges_enabled, payouts_enabled) rather than inventing a separate status enum —
-- consistent with section 16's "Stripe is the source of truth, LeagueLift keeps
-- synchronized records" principle.

create table organization_payout_account (
    id                 uuid primary key default gen_random_uuid(),
    organization_id    uuid not null unique references organization (id),
    stripe_account_id  text not null unique,
    details_submitted  boolean not null default false,
    charges_enabled    boolean not null default false,
    payouts_enabled    boolean not null default false,
    created_at         timestamptz not null default now(),
    updated_at         timestamptz not null default now()
);
