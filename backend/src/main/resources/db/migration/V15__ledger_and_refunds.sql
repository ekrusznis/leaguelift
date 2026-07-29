-- Phase 5 slice 1: ledger, real payout transfers, and refunds (ADR-017).
-- Makes DESIGN-DOC.md section 8.6's ledger model real for the first time, and
-- closes the second half of ADR-005's "separate charges and transfers" model
-- — money now actually moves from LeagueLift's Stripe balance to an org's
-- connected account, and a bad transaction can be reversed.
--
-- Deliberately NOT part of this migration: PAYMENT_PROCESSING_FEE tracking
-- (Stripe deducts its own fee from LeagueLift's balance regardless of whether
-- our ledger records it — a reporting gap, not a functional one this slice),
-- and the full section 8.6 entry-type list (SALES_TAX, SHIPPING_COLLECTED,
-- FULFILLMENT_SHIPPING_COST, TEAM_ALLOCATION, HOUSEHOLD_CREDIT, CHARGEBACK,
-- PAYOUT, MANUAL_ADJUSTMENT — none of those have a computation rule yet).

-- Every entry is a signed movement (direction + amount_minor, both positive);
-- corrections are always a new reversing row, never an edit of an existing one.
create table ledger_entry (
    id                          uuid primary key default gen_random_uuid(),
    organization_id             uuid not null references organization (id),
    account_code                text not null,
    entry_type                  text not null,
    direction                   text not null,
    amount_minor                bigint not null,
    currency                    text not null default 'USD',
    source_type                 text not null,
    source_id                   uuid not null,
    external_reference          text,
    description                 text,
    -- Set only on ORGANIZATION_EARNING credit/reversal-debit rows once a
    -- transfer has consumed them — the payout-eligibility query's "already
    -- paid out" marker. Self-referencing rather than a separate join table
    -- since only one transfer can ever claim a given earning row.
    included_in_transfer_entry_id uuid references ledger_entry (id),
    effective_at                timestamptz not null default now(),
    created_at                  timestamptz not null default now(),
    constraint ledger_entry_type_check check (entry_type in (
        'CONTRIBUTION', 'GROSS_SALE', 'PRODUCTION_COST', 'LEAGUELIFT_PLATFORM_FEE',
        'ORGANIZATION_EARNING', 'TRANSFER', 'REFUND'
    )),
    constraint ledger_entry_direction_check check (direction in ('CREDIT', 'DEBIT')),
    constraint ledger_entry_source_type_check check (source_type in ('CONTRIBUTION', 'ORDER', 'TRANSFER', 'REFUND')),
    constraint ledger_entry_amount_check check (amount_minor >= 0)
);

create index ledger_entry_organization_id_idx on ledger_entry (organization_id);
create index ledger_entry_source_idx on ledger_entry (source_type, source_id);
-- The payout-eligibility query's main filter: unclaimed ORGANIZATION_EARNING rows for an org.
create index ledger_entry_unclaimed_earnings_idx on ledger_entry (organization_id, entry_type)
    where included_in_transfer_entry_id is null;

-- Needed to create a real Stripe Refund later (the paymentIntent reference) —
-- captured once at webhook-confirmation time, never re-derived.
alter table contribution add column stripe_payment_intent_id text;
alter table "order" add column stripe_payment_intent_id text;

alter table contribution add column refunded_at timestamptz;
alter table "order" add column refunded_at timestamptz;

alter table contribution drop constraint contribution_status_check;
alter table contribution add constraint contribution_status_check
    check (status in ('PENDING', 'CONFIRMED', 'CANCELED', 'REFUNDED'));

alter table "order" drop constraint order_status_check;
alter table "order" add constraint order_status_check
    check (status in ('PENDING', 'CONFIRMED', 'CANCELED', 'REFUNDED'));
