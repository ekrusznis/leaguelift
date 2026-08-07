-- Phase 24 slice 24.3 (DESIGN-DOC.md section 14.1G): public, unauthenticated
-- per-athlete Swag Shop storefront. Unlike campaign contributions (which need
-- a per-household attribution *code*, since one campaign can be attributed to
-- any of several households), a storefront is scoped to exactly one athlete
-- at publish time, so household attribution is a direct
-- participant_id -> household_id lookup — no attribution-link table needed.

create table athlete_storefront (
    id               uuid primary key default gen_random_uuid(),
    organization_id  uuid not null references organization (id),
    participant_id   uuid not null references participant (id),
    team_id          uuid references team (id),
    store_id         uuid not null references store (id),
    slug             text not null,
    status           text not null default 'DRAFT',
    published_at     timestamptz,
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now(),
    constraint athlete_storefront_status_check check (status in ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    constraint athlete_storefront_slug_format_check check (slug ~ '^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$')
);

create unique index athlete_storefront_slug_idx on athlete_storefront (lower(slug));
create index athlete_storefront_organization_id_idx on athlete_storefront (organization_id);
create index athlete_storefront_participant_id_idx on athlete_storefront (participant_id);

-- Explicit, curated product selection per storefront ("approved product
-- selection" — DESIGN-DOC.md section 14.1G 24.3), not implicitly every
-- active product in the underlying store.
create table athlete_storefront_product (
    storefront_id uuid not null references athlete_storefront (id),
    product_id    uuid not null references product (id),
    created_at    timestamptz not null default now(),
    primary key (storefront_id, product_id)
);

-- Nullable: only set for an order placed through a published athlete
-- storefront. Resolved and stored at order-insert time (before Stripe is
-- ever called), mirroring contribution.attributed_household_id (V46).
alter table "order" add column attributed_household_id uuid references household (id);

alter table family_credit_grant drop constraint family_credit_grant_source_type_check;
alter table family_credit_grant add constraint family_credit_grant_source_type_check check (
    source_type in ('CAMPAIGN_ATTRIBUTION', 'STOREFRONT_ATTRIBUTION', 'ORG_PROMO', 'MANUAL', 'P2P_TRANSFER')
);
