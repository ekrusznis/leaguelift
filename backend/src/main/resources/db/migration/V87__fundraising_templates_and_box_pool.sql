-- Fundraising Templates (Phase 42, DESIGN-DOC.md §14.1Q) — slice 1-3 schema.
-- campaign gains a creator (a real prior gap — no creator was tracked at all)
-- and an optional template_key tracking which starter template, if any,
-- produced the campaign. "A table specifically for open fundraisers" is
-- deliberately satisfied by `campaign where status = 'ACTIVE'`, not a second
-- redundant table.

alter table campaign add column created_by_user_id uuid references app_user (id);
alter table campaign add column template_key text;
alter table campaign add constraint campaign_template_key_check
    check (template_key is null or template_key in ('BOX_POOL', 'BAKE_SALE', 'CAR_WASH'));

-- Box pool: a grid of claimable boxes tied to one BOX_POOL campaign. One pool
-- per campaign. Boxes are auto-created (rows * cols) as OPEN when the pool is
-- set up. A box purchase is an ordinary `contribution` row (see
-- box_pool_box.contribution_id below) — no new ledger entry/source type,
-- since a box purchase is not a structurally different kind of money
-- movement from any other contribution.

create table box_pool (
    id                  uuid primary key default gen_random_uuid(),
    campaign_id         uuid not null unique references campaign (id),
    organization_id     uuid not null references organization (id),
    sport               text not null,
    rows                integer not null,
    cols                integer not null,
    price_per_box_minor bigint not null,
    row_axis_label      text,
    col_axis_label      text,
    prize_description   text,
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now(),
    constraint box_pool_rows_check check (rows > 0 and rows <= 26),
    constraint box_pool_cols_check check (cols > 0 and cols <= 26),
    constraint box_pool_price_check check (price_per_box_minor > 0)
);

create index box_pool_organization_id_idx on box_pool (organization_id);

create table box_pool_box (
    id              uuid primary key default gen_random_uuid(),
    box_pool_id     uuid not null references box_pool (id),
    row_index       integer not null,
    col_index       integer not null,
    status          text not null default 'OPEN',
    claimant_name   text,
    claimant_email  text,
    contribution_id uuid references contribution (id),
    reserved_until  timestamptz,
    claimed_at      timestamptz,
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    constraint box_pool_box_status_check check (status in ('OPEN', 'RESERVED', 'CLAIMED')),
    constraint box_pool_box_position_unique unique (box_pool_id, row_index, col_index)
);

create index box_pool_box_pool_id_idx on box_pool_box (box_pool_id);
create index box_pool_box_contribution_id_idx on box_pool_box (contribution_id);
