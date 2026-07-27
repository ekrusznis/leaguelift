-- Phase 2: fee templates (reusable charge types) and fee assignments
-- (charges applied to a household, optionally scoped to a participant).
-- Amounts are stored as minor currency units (cents for USD) to avoid
-- floating-point representation issues.

create table fee_template (
    id              uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organization (id),
    name            text not null,
    description     text,
    amount_minor    bigint not null,
    currency        text not null default 'USD',
    status          text not null default 'ACTIVE',
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    constraint fee_template_status_check check (status in ('ACTIVE', 'ARCHIVED')),
    constraint fee_template_amount_check check (amount_minor >= 0)
);

create index fee_template_organization_id_idx on fee_template (organization_id);

create table fee_assignment (
    id                  uuid primary key default gen_random_uuid(),
    organization_id     uuid not null references organization (id),
    household_id        uuid not null references household (id),
    participant_id      uuid references participant (id),
    fee_template_id     uuid references fee_template (id),
    description         text not null,
    original_amount_minor bigint not null,
    currency            text not null default 'USD',
    due_date            date,
    status              text not null default 'OPEN',
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now(),
    constraint fee_assignment_status_check check (status in ('OPEN', 'PARTIALLY_PAID', 'PAID', 'WAIVED', 'CANCELLED')),
    constraint fee_assignment_amount_check check (original_amount_minor >= 0)
);

create index fee_assignment_organization_id_idx on fee_assignment (organization_id);
create index fee_assignment_household_id_idx on fee_assignment (household_id);
create index fee_assignment_participant_id_idx on fee_assignment (participant_id);
