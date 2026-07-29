-- Phase 4 slice 1: apparel/merchandise commerce proof (DESIGN-DOC.md section 8.3,
-- 14.3). A supporter can view products, select a variant, add multiple variants to
-- a (frontend-only, unpersisted) cart, complete test-mode Stripe checkout, and
-- receive an order + fulfillment record. Product variants are backed by Printify's
-- real catalog (blueprint/print-provider/variant), with the print provider chosen
-- via cost-ranked US-provider selection (integration/printify module) — see the
-- accompanying ADR for why this is draft-order-only (no send-to-production call)
-- and why auto-design/personalization is explicitly out of scope.
--
-- Deliberately NOT part of this migration: `cart`/`cart_item` (the cart lives in
-- frontend state until checkout, submitted as a line-item array), `store_collection`
-- (products belong directly to a store for now), and any credit/allocation tables
-- (blocked on the same unresolved DESIGN-DOC.md section 19.3 questions #6/#16/#17
-- that deferred fundraising credits).

create table store (
    id              uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organization (id),
    team_id         uuid references team (id),
    name            text not null,
    slug            text not null,
    status          text not null default 'DRAFT',
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    constraint store_status_check check (status in ('DRAFT', 'ACTIVE', 'ARCHIVED'))
);

create unique index store_slug_idx on store (lower(slug));
create index store_organization_id_idx on store (organization_id);
create index store_team_id_idx on store (team_id);

create table product (
    id                    uuid primary key default gen_random_uuid(),
    organization_id       uuid not null references organization (id),
    store_id              uuid not null references store (id),
    name                  text not null,
    description           text,
    printify_blueprint_id bigint not null,
    -- Printify's own uploaded-image id (POST /v1/uploads/images.json, given our
    -- media pipeline's signed GET URL as the source) — set once per product and
    -- reused for every variant/order's print_areas, rather than re-uploading the
    -- same design image on every order.
    printify_image_id    text,
    -- Which of the blueprint's print-area placeholders (e.g. "front", "back") the
    -- single design image goes on this slice — see the one-design-slot-per-product
    -- scoping decision (no per-position design uploads yet).
    printify_print_position text not null default 'front',
    status                text not null default 'DRAFT',
    created_at            timestamptz not null default now(),
    updated_at            timestamptz not null default now(),
    constraint product_status_check check (status in ('DRAFT', 'ACTIVE', 'ARCHIVED'))
);

create index product_store_id_idx on product (store_id);
create index product_organization_id_idx on product (organization_id);

-- One row per sellable size/color combination. printify_print_provider_id and
-- printify_variant_id are snapshotted at creation time from a specific
-- VendorSelectionService ranking — re-running that ranking later does not
-- retroactively change an existing variant's provider (an admin would create a
-- new variant/product for that), matching how fee_payment/contribution snapshot
-- amounts rather than recomputing them.
create table product_variant (
    id                           uuid primary key default gen_random_uuid(),
    organization_id              uuid not null references organization (id),
    product_id                   uuid not null references product (id),
    label                        text not null,
    printify_print_provider_id   bigint not null,
    printify_variant_id          bigint not null,
    currency                     text not null default 'USD',
    cost_minor                   bigint not null,
    price_minor                  bigint not null,
    is_active                    boolean not null default true,
    created_at                   timestamptz not null default now(),
    updated_at                   timestamptz not null default now(),
    constraint product_variant_cost_check check (cost_minor >= 0),
    constraint product_variant_price_check check (price_minor >= 0)
);

create index product_variant_product_id_idx on product_variant (product_id);

create table "order" (
    id                          uuid primary key default gen_random_uuid(),
    organization_id             uuid not null references organization (id),
    store_id                    uuid not null references store (id),
    status                      text not null default 'PENDING',
    currency                    text not null default 'USD',
    supporter_name              text,
    supporter_email             text,
    shipping_address            jsonb,
    stripe_checkout_session_id  text unique,
    confirmed_at                timestamptz,
    created_at                  timestamptz not null default now(),
    constraint order_status_check check (status in ('PENDING', 'CONFIRMED', 'CANCELED'))
);

create index order_store_id_idx on "order" (store_id);
create index order_organization_id_idx on "order" (organization_id);

create table order_item (
    id                  uuid primary key default gen_random_uuid(),
    order_id            uuid not null references "order" (id),
    product_variant_id  uuid not null references product_variant (id),
    quantity            int not null,
    unit_price_minor    bigint not null,
    unit_cost_minor     bigint not null,
    constraint order_item_quantity_check check (quantity > 0),
    constraint order_item_price_check check (unit_price_minor >= 0),
    constraint order_item_cost_check check (unit_cost_minor >= 0)
);

create index order_item_order_id_idx on order_item (order_id);

-- One row per order, this slice. status never reaches PRODUCTION/SHIPPED here —
-- DRAFT_CREATED means a Printify order object exists but send_to_production.json
-- was never called (see the ADR). FAILED means the payment was confirmed but the
-- Printify draft-order call itself failed; the order/payment record still stands,
-- this is purely an admin-visible fulfillment-provider hiccup.
create table fulfillment (
    id                  uuid primary key default gen_random_uuid(),
    order_id            uuid not null unique references "order" (id),
    status              text not null default 'NOT_SUBMITTED',
    printify_order_id   text,
    last_error          text,
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now(),
    constraint fulfillment_status_check check (status in ('NOT_SUBMITTED', 'DRAFT_CREATED', 'FAILED'))
);
