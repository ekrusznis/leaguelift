-- DESIGN-DOC.md §14.6 item #4 (Dispute/chargeback handling). Rally26 is the merchant
-- of record (ADR-005, separate charges/transfers), so a Stripe dispute always lands
-- against Rally26's own account/charge, never a connected organization's — this table
-- tracks it back to the org via the same source_type/source_id shape ledger_entry
-- already uses. evidence_due_by is stored purely for visibility (the Stripe Dashboard
-- deadline) — evidence submission itself stays manual, not built here.

create table payment_dispute (
    id                  uuid primary key,
    organization_id     uuid not null references organization (id),
    source_type         text not null check (source_type in ('CONTRIBUTION', 'ORDER', 'SPONSORSHIP', 'FEE_PAYMENT')),
    source_id           uuid not null,
    stripe_dispute_id   text not null unique,
    stripe_charge_id    text not null,
    amount_minor        bigint not null check (amount_minor >= 0),
    currency             text not null,
    reason              text not null,
    status              text not null check (status in ('NEEDS_RESPONSE', 'UNDER_REVIEW', 'WON', 'LOST')),
    evidence_due_by     timestamptz,
    opened_at           timestamptz not null,
    resolved_at         timestamptz,
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now()
);

create index payment_dispute_organization_idx on payment_dispute (organization_id, opened_at desc);
create index payment_dispute_source_idx on payment_dispute (source_type, source_id);

-- New ledger entry/source types for chargeback handling (CHARGEBACK was already
-- named as a design target in LedgerEntryType's own doc comment).
alter table ledger_entry drop constraint ledger_entry_type_check;
alter table ledger_entry add constraint ledger_entry_type_check check (entry_type in (
    'CONTRIBUTION', 'GROSS_SALE', 'PRODUCTION_COST', 'RALLY26_PLATFORM_FEE',
    'ORGANIZATION_EARNING', 'TRANSFER', 'REFUND', 'OFFLINE_SETTLEMENT', 'MANUAL_ADJUSTMENT',
    'CHARGEBACK', 'CHARGEBACK_FEE'
));
alter table ledger_entry drop constraint ledger_entry_source_type_check;
alter table ledger_entry add constraint ledger_entry_source_type_check check (source_type in (
    'CONTRIBUTION', 'ORDER', 'TRANSFER', 'REFUND', 'SPONSORSHIP', 'CORRECTION', 'FEE_PAYMENT', 'DISPUTE'
));

-- Lookups needed to route an incoming Stripe dispute (keyed by payment_intent, not
-- checkout session) back to its source record and organization.
create index order_stripe_payment_intent_idx on "order" (stripe_payment_intent_id) where stripe_payment_intent_id is not null;
create index contribution_stripe_payment_intent_idx on contribution (stripe_payment_intent_id) where stripe_payment_intent_id is not null;
create index sponsorship_stripe_payment_intent_idx on sponsorship (stripe_payment_intent_id) where stripe_payment_intent_id is not null;
create index fee_payment_stripe_payment_intent_idx on fee_payment (stripe_payment_intent_id) where stripe_payment_intent_id is not null;
