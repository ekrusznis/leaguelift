-- Phase 6 slice 1: sponsorships proof-of-concept (ADR-018, DESIGN-DOC.md section
-- 14.1). Org admins publish fixed-price sponsorship packages; a public visitor
-- purchases one via test-mode Stripe Checkout (same charge model as contributions/
-- orders — Rally26 is merchant of record, ADR-005), confirmed through the
-- existing POST /webhooks/stripe receiver. Reuses the ledger's existing
-- CONTRIBUTION-shaped accounting (LedgerService.recordConfirmedSponsorship) rather
-- than a new entry type — see ADR-018 for why.
--
-- Deliberately NOT part of this migration: refunds (no refunded_at/
-- stripe_payment_intent_id-driven refund workflow — REFUND stays a status value for
-- schema forward-compatibility only, same as V15 added it to contribution/order
-- ahead of the refund feature itself), approval workflow, renewal reminders,
-- invoices, sponsor-contact CRM beyond name/email, and link/QR tracking. All
-- deferred to a later slice per DESIGN-DOC.md section 13's full catalog.

create table sponsor (
    id              uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organization (id),
    name            text not null,
    contact_email   text,
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now()
);

create index sponsor_organization_id_idx on sponsor (organization_id);

create table sponsorship_package (
    id                   uuid primary key default gen_random_uuid(),
    organization_id      uuid not null references organization (id),
    name                 text not null,
    description          text,
    price_minor          bigint not null,
    currency             text not null default 'USD',
    -- null means uncapped. exclusive forces an effective cap of 1 regardless of this
    -- value (SponsorshipPackage.effectiveMaxQuantity()) — enforced in application
    -- code, not a check constraint, since it depends on the exclusive column too.
    max_quantity         int,
    exclusive            boolean not null default false,
    placement_start_date date,
    placement_end_date   date,
    status               text not null default 'DRAFT',
    created_at           timestamptz not null default now(),
    updated_at           timestamptz not null default now(),
    constraint sponsorship_package_price_check check (price_minor >= 0),
    constraint sponsorship_package_max_quantity_check check (max_quantity is null or max_quantity > 0),
    constraint sponsorship_package_status_check check (status in ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
);

create index sponsorship_package_organization_id_idx on sponsorship_package (organization_id);

-- One row per purchased sponsorship. amount_minor/currency are snapshotted from the
-- package's price at purchase time (same rationale as order_item.unit_price_minor)
-- so a later price change on the package never retroactively changes what an
-- already-purchased sponsorship is worth.
create table sponsorship (
    id                          uuid primary key default gen_random_uuid(),
    organization_id             uuid not null references organization (id),
    package_id                  uuid not null references sponsorship_package (id),
    sponsor_id                  uuid not null references sponsor (id),
    amount_minor                bigint not null,
    currency                    text not null default 'USD',
    status                      text not null default 'PENDING',
    stripe_checkout_session_id  text unique,
    stripe_payment_intent_id    text,
    confirmed_at                timestamptz,
    created_at                  timestamptz not null default now(),
    constraint sponsorship_amount_check check (amount_minor >= 0),
    constraint sponsorship_status_check check (status in ('PENDING', 'CONFIRMED', 'REFUNDED'))
);

create index sponsorship_organization_id_idx on sponsorship (organization_id);
create index sponsorship_package_id_idx on sponsorship (package_id);
create index sponsorship_sponsor_id_idx on sponsorship (sponsor_id);

-- Widen the media pipeline for sponsor logos — same widening pattern
-- V14__widen_media_slots_for_products.sql used for PRODUCT/PRODUCT_DESIGN.
alter table media_asset drop constraint media_asset_usage_slot_check;
alter table media_asset add constraint media_asset_usage_slot_check
    check (intended_usage_slot in ('LOGO', 'COVER', 'PRODUCT_DESIGN', 'SPONSOR_LOGO'));

alter table media_assignment drop constraint media_assignment_entity_type_check;
alter table media_assignment add constraint media_assignment_entity_type_check
    check (entity_type in ('ORGANIZATION', 'PRODUCT', 'SPONSOR'));

alter table media_assignment drop constraint media_assignment_usage_slot_check;
alter table media_assignment add constraint media_assignment_usage_slot_check
    check (usage_slot in ('LOGO', 'COVER', 'PRODUCT_DESIGN', 'SPONSOR_LOGO'));

-- Widen the ledger's source_type so sponsorship-sourced entries can be traced back
-- to a sponsorship. No new entry_type — a confirmed sponsorship reuses the existing
-- CONTRIBUTION/RALLY26_PLATFORM_FEE/ORGANIZATION_EARNING entry types.
alter table ledger_entry drop constraint ledger_entry_source_type_check;
alter table ledger_entry add constraint ledger_entry_source_type_check
    check (source_type in ('CONTRIBUTION', 'ORDER', 'TRANSFER', 'REFUND', 'SPONSORSHIP'));
