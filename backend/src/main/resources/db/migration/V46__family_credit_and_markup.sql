-- Phase 23: Family Credit & Markup Rule Engine (DESIGN-DOC.md section 14.1,
-- founder decision pass 2026-08-05 resolving section 19.3 open questions
-- #6/#7/#16/#17). Campaign contributions are the only real, already-shipped
-- source of attributable revenue today (Swag Shop storefront attribution is
-- Phase 25, not built) — attribution is scoped to campaigns specifically, not
-- a generic cross-feature abstraction.

-- A per-household, per-campaign private sharing link. The code is never
-- publicly displayed by itself; public_display_name is the supporter-facing
-- opt-in name shown on the campaign page (both privacy models the founder
-- asked for: always-private tracking, optionally-public display).
create table campaign_household_attribution_link (
    id                  uuid primary key default gen_random_uuid(),
    organization_id     uuid not null references organization (id),
    household_id        uuid not null references household (id),
    campaign_id         uuid not null references campaign (id),
    code                text not null unique,
    public_display_name text,
    created_at          timestamptz not null default now(),
    constraint campaign_household_attribution_link_unique unique (household_id, campaign_id)
);

create index campaign_household_attribution_link_campaign_id_idx on campaign_household_attribution_link (campaign_id);

-- Nullable: only set when a supporter arrives via a household's attribution
-- link. Resolved and stored at contribution-insert time (before Stripe is
-- ever called), not round-tripped through Stripe metadata.
alter table contribution add column attributed_household_id uuid references household (id);

-- One row per grant. remaining_minor decrements as it's applied/transferred,
-- supporting partial use — real typed state over pure event sourcing,
-- matching this schema's existing convention (e.g. fulfillment.status).
create table family_credit_grant (
    id               uuid primary key default gen_random_uuid(),
    organization_id  uuid not null references organization (id),
    household_id     uuid not null references household (id),
    amount_minor     bigint not null,
    remaining_minor  bigint not null,
    currency         text not null,
    status           text not null default 'AVAILABLE',
    source_type      text not null,
    source_id        uuid,
    granted_at       timestamptz not null default now(),
    available_at     timestamptz not null default now(),
    expires_at       timestamptz,
    created_at       timestamptz not null default now(),
    constraint family_credit_grant_status_check check (status in ('PENDING', 'AVAILABLE', 'EXPIRED', 'REVOKED')),
    constraint family_credit_grant_source_type_check check (source_type in ('CAMPAIGN_ATTRIBUTION', 'ORG_PROMO', 'MANUAL', 'P2P_TRANSFER')),
    constraint family_credit_grant_amount_check check (amount_minor > 0),
    constraint family_credit_grant_remaining_check check (remaining_minor >= 0 and remaining_minor <= amount_minor)
);

create index family_credit_grant_household_id_idx on family_credit_grant (household_id);
create index family_credit_grant_organization_id_idx on family_credit_grant (organization_id);
create index family_credit_grant_expires_at_idx on family_credit_grant (expires_at) where status in ('PENDING', 'AVAILABLE');

-- Junction table: which grant(s) funded which fee_adjustment. Supports FIFO
-- consumption across multiple grants per application with a full audit
-- trail, without overloading fee_adjustment itself.
create table family_credit_application (
    id                  uuid primary key default gen_random_uuid(),
    organization_id     uuid not null references organization (id),
    grant_id            uuid not null references family_credit_grant (id),
    fee_adjustment_id   uuid not null references fee_adjustment (id),
    amount_minor        bigint not null,
    applied_by_user_id  uuid not null references app_user (id),
    created_at          timestamptz not null default now(),
    constraint family_credit_application_amount_check check (amount_minor > 0)
);

create index family_credit_application_grant_id_idx on family_credit_application (grant_id);
create index family_credit_application_fee_adjustment_id_idx on family_credit_application (fee_adjustment_id);

-- A transfer decrements the sender's grant(s) remaining_minor (recorded as
-- separate REVOKED-status decrements on those grants, tracked implicitly via
-- this row, not a new grant status) and creates one new AVAILABLE grant for
-- the receiver (family_credit_grant.source_type = 'P2P_TRANSFER').
create table family_credit_transfer (
    id                    uuid primary key default gen_random_uuid(),
    organization_id       uuid not null references organization (id),
    from_household_id     uuid not null references household (id),
    to_household_id       uuid not null references household (id),
    amount_minor          bigint not null,
    initiated_by_user_id  uuid not null references app_user (id),
    created_at            timestamptz not null default now(),
    constraint family_credit_transfer_amount_check check (amount_minor > 0),
    constraint family_credit_transfer_distinct_households check (from_household_id <> to_household_id)
);

create index family_credit_transfer_from_household_id_idx on family_credit_transfer (from_household_id);
create index family_credit_transfer_to_household_id_idx on family_credit_transfer (to_household_id);

-- Satellite 1:1 settings table, mirroring organization_payout_account's shape.
-- "Post-season expiration" is simplified to a configurable N-month window
-- since this codebase has no formal season-dates entity; p2p_transfer_enabled
-- defaults false, amending section 1's Credit boundary hard rule only for a
-- specific organization that explicitly opts in (2026-08-05 founder decision).
create table organization_credit_settings (
    id                     uuid primary key default gen_random_uuid(),
    organization_id        uuid not null unique references organization (id),
    default_credit_percent integer not null default 1000,
    expiration_policy      text not null default 'ROLLOVER',
    expiration_months      integer,
    p2p_transfer_enabled   boolean not null default false,
    updated_at             timestamptz not null default now(),
    constraint organization_credit_settings_percent_check check (default_credit_percent >= 0 and default_credit_percent <= 10000),
    constraint organization_credit_settings_expiration_policy_check check (expiration_policy in ('ROLLOVER', 'EXPIRES')),
    constraint organization_credit_settings_expiration_months_check check (
        (expiration_policy = 'EXPIRES' and expiration_months is not null and expiration_months > 0)
        or (expiration_policy = 'ROLLOVER' and expiration_months is null)
    )
);

-- null printify_blueprint_id = org-wide default; non-null = a per-apparel-type
-- override. markup_value is basis points for PERCENTAGE (3000 = 30.00%) or
-- minor-unit cents for FLAT.
create table organization_markup_rule (
    id                    uuid primary key default gen_random_uuid(),
    organization_id       uuid not null references organization (id),
    printify_blueprint_id bigint,
    markup_type           text not null,
    markup_value          integer not null,
    created_at            timestamptz not null default now(),
    updated_at            timestamptz not null default now(),
    constraint organization_markup_rule_type_check check (markup_type in ('PERCENTAGE', 'FLAT')),
    constraint organization_markup_rule_value_check check (markup_value >= 0),
    constraint organization_markup_rule_unique unique (organization_id, printify_blueprint_id)
);
