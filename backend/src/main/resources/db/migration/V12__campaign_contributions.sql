-- Campaign contributions: test-mode online giving (Phase 3 remainder, ADR-005's
-- charge model applied concretely for the first time). A supporter pays via a
-- Stripe Checkout Session; confirmation happens via a real Stripe webhook
-- (checkout.session.completed), not the "sync refresh on browser return" pattern
-- the payout module uses for Connect onboarding — a supporter who pays and closes
-- the tab before redirecting back would otherwise leave Stripe holding confirmed
-- money Rally26 never records.
--
-- Explicitly NOT part of this migration: credit_rule/credit_event/credit_application
-- (DESIGN-DOC.md section 19.3 open questions #6/#16/#17 — exact credit percentages,
-- cross-season credits, credit expiry — are unresolved; building the credit system
-- now would mean guessing at them) and refund/dispute handling (Phase 5).

create table contribution (
    id                          uuid primary key default gen_random_uuid(),
    organization_id             uuid not null references organization (id),
    campaign_id                 uuid not null references campaign (id),
    amount_minor                bigint not null,
    currency                    text not null,
    supporter_name              text,
    is_anonymous                boolean not null default false,
    supporter_email             text,
    status                      text not null default 'PENDING',
    -- Nullable: the contribution row is inserted before Stripe returns a session id
    -- (Stripe's checkout-session metadata needs our contribution id, so ours has to
    -- exist first) and attached right after. Postgres allows multiple NULLs under a
    -- unique constraint, so this doesn't weaken the once-set uniqueness guarantee.
    stripe_checkout_session_id  text unique,
    confirmed_at                timestamptz,
    created_at                  timestamptz not null default now(),
    constraint contribution_status_check check (status in ('PENDING', 'CONFIRMED', 'CANCELED')),
    constraint contribution_amount_check check (amount_minor > 0)
);

create index contribution_campaign_id_idx on contribution (campaign_id);
create index contribution_organization_id_idx on contribution (organization_id);

-- Generic inbound-webhook ledger (DESIGN-DOC.md section 17's design-target shape,
-- built for real here for the first time). Phase 5 can reuse this same table for
-- Stripe Connect account-status webhooks; only checkout.session.completed is
-- consumed by this slice.
create table webhook_event (
    id                   uuid primary key default gen_random_uuid(),
    provider             text not null,
    external_event_id    text not null,
    event_type           text not null,
    payload              jsonb not null,
    payload_hash         text not null,
    signature_verified   boolean not null,
    processing_status    text not null,
    attempt_count        int not null default 1,
    related_entity_type  text,
    related_entity_id    uuid,
    received_at          timestamptz not null default now(),
    processed_at         timestamptz,
    last_error           text,
    constraint webhook_event_processing_status_check check (processing_status in ('PROCESSED', 'FAILED', 'IGNORED')),
    constraint webhook_event_provider_external_id_unique unique (provider, external_event_id)
);

create index webhook_event_related_entity_idx on webhook_event (related_entity_type, related_entity_id);
