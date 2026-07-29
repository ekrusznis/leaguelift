-- Phase 2 remainder: manual/offline payment recording and manual discounts/credits
-- against a fee assignment (DESIGN-DOC.md section 14.3 "record payment/adjustment,
-- apply manual discount/approved credit"; resolves the open question in section 19.3
-- item 18 — manual/offline payments are permitted, per a 2026-07-28 founder decision).
--
-- Deliberately NOT the full attribution-sourced "Family Credits" system from section
-- 13 (merchandise/campaign/sponsorship-attribution credits) — that depends on
-- fundraising-contribution and apparel-purchase data that doesn't exist yet.
-- fee_adjustment is simpler: an admin manually reduces one fee's balance with a
-- reason. Live Stripe-charge payment recording is a separate, later concern (Phase 5)
-- — this table is for payments an admin records by hand (cash/check/etc.) today.
--
-- Rows are immutable-with-void, never deleted or edited (DESIGN-DOC.md section 8.1
-- rules 4/5): a mistaken entry gets voided_at/voided_by_user_id/void_reason set,
-- excluded from balance math, but kept for audit history.

create table fee_payment (
    id                  uuid primary key default gen_random_uuid(),
    organization_id     uuid not null references organization (id),
    fee_assignment_id   uuid not null references fee_assignment (id),
    household_id        uuid not null references household (id),
    amount_minor        bigint not null,
    currency            text not null default 'USD',
    method              text not null,
    paid_at             date not null,
    note                text,
    recorded_by_user_id uuid not null references app_user (id),
    voided_at           timestamptz,
    voided_by_user_id   uuid references app_user (id),
    void_reason         text,
    created_at          timestamptz not null default now(),
    constraint fee_payment_method_check check (method in ('CASH', 'CHECK', 'VENMO', 'ZELLE', 'OTHER')),
    constraint fee_payment_amount_check check (amount_minor > 0)
);

create index fee_payment_organization_id_idx on fee_payment (organization_id);
create index fee_payment_fee_assignment_id_idx on fee_payment (fee_assignment_id);
create index fee_payment_household_id_idx on fee_payment (household_id);

create table fee_adjustment (
    id                  uuid primary key default gen_random_uuid(),
    organization_id     uuid not null references organization (id),
    fee_assignment_id   uuid not null references fee_assignment (id),
    household_id        uuid not null references household (id),
    adjustment_type     text not null,
    amount_minor        bigint not null,
    currency            text not null default 'USD',
    reason              text,
    created_by_user_id  uuid not null references app_user (id),
    voided_at           timestamptz,
    voided_by_user_id   uuid references app_user (id),
    void_reason         text,
    created_at          timestamptz not null default now(),
    constraint fee_adjustment_type_check check (adjustment_type in ('DISCOUNT', 'CREDIT', 'CORRECTION')),
    constraint fee_adjustment_amount_check check (amount_minor > 0)
);

create index fee_adjustment_organization_id_idx on fee_adjustment (organization_id);
create index fee_adjustment_fee_assignment_id_idx on fee_adjustment (fee_assignment_id);
create index fee_adjustment_household_id_idx on fee_adjustment (household_id);
