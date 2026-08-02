-- Phase 18 slices 1-2: manual catalog/vendor management and operational fulfillment.
-- Provider-backed records retain their real Printify identifiers. Manual records use
-- an explicit MANUAL source and nullable provider fields; no fake provider IDs are
-- introduced. Fulfillment changes are append-audited through fulfillment_history,
-- and reprint/replacement work is stored in its own durable lifecycle table.

create table manual_vendor (
    id              uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organization (id),
    name            text not null,
    contact_name    text,
    contact_email   text,
    phone           text,
    website_url     text,
    notes           text,
    status          text not null default 'ACTIVE',
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    constraint manual_vendor_status_check check (status in ('ACTIVE', 'ARCHIVED'))
);

create unique index manual_vendor_active_name_idx
    on manual_vendor (organization_id, lower(name)) where status = 'ACTIVE';
create index manual_vendor_organization_idx on manual_vendor (organization_id, status, name);

alter table product add column catalog_source text not null default 'PRINTIFY';
alter table product add column manual_vendor_id uuid references manual_vendor (id);
alter table product alter column printify_blueprint_id drop not null;
alter table product add constraint product_catalog_source_check
    check (catalog_source in ('PRINTIFY', 'MANUAL'));
alter table product add constraint product_catalog_source_fields_check check (
    (catalog_source = 'PRINTIFY' and printify_blueprint_id is not null and manual_vendor_id is null)
    or
    (catalog_source = 'MANUAL' and printify_blueprint_id is null and printify_image_id is null)
);
create index product_manual_vendor_idx on product (manual_vendor_id) where manual_vendor_id is not null;

alter table product_variant add column catalog_source text not null default 'PRINTIFY';
alter table product_variant add column sku text;
alter table product_variant add column size text;
alter table product_variant add column color text;
alter table product_variant alter column printify_print_provider_id drop not null;
alter table product_variant alter column printify_variant_id drop not null;
alter table product_variant add constraint product_variant_catalog_source_check
    check (catalog_source in ('PRINTIFY', 'MANUAL'));
alter table product_variant add constraint product_variant_source_fields_check check (
    (catalog_source = 'PRINTIFY' and printify_print_provider_id is not null and printify_variant_id is not null)
    or
    (catalog_source = 'MANUAL' and printify_print_provider_id is null and printify_variant_id is null)
);
create unique index product_variant_active_sku_idx
    on product_variant (organization_id, lower(sku)) where is_active = true and sku is not null;

alter table fulfillment add column source text not null default 'PRINTIFY';
alter table fulfillment add column manual_vendor_id uuid references manual_vendor (id);
alter table fulfillment add column vendor_order_reference text;
alter table fulfillment add column carrier text;
alter table fulfillment add column tracking_number text;
alter table fulfillment add column tracking_url text;
alter table fulfillment add column internal_notes text;
alter table fulfillment add column attention_reason text;
alter table fulfillment add column status_changed_at timestamptz not null default now();
alter table fulfillment add column shipped_at timestamptz;
alter table fulfillment add column delivered_at timestamptz;

alter table fulfillment drop constraint fulfillment_status_check;
alter table fulfillment add constraint fulfillment_status_check check (status in (
    'NOT_SUBMITTED', 'DRAFT_CREATED', 'FAILED', 'READY', 'IN_PRODUCTION',
    'SHIPPED', 'DELIVERED', 'NEEDS_ATTENTION', 'CANCELED'
));
alter table fulfillment add constraint fulfillment_source_check check (source in ('PRINTIFY', 'MANUAL'));
alter table fulfillment add constraint fulfillment_source_fields_check check (
    (source = 'PRINTIFY' and manual_vendor_id is null)
    or
    (source = 'MANUAL' and printify_order_id is null)
);
alter table fulfillment add constraint fulfillment_attention_reason_check check (
    status <> 'NEEDS_ATTENTION' or attention_reason is not null
);
update fulfillment set status_changed_at = updated_at;

create table fulfillment_history (
    id              uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organization (id),
    fulfillment_id  uuid not null references fulfillment (id),
    previous_status text,
    new_status      text not null,
    note            text not null,
    actor_user_id   uuid references app_user (id),
    created_at      timestamptz not null default now(),
    constraint fulfillment_history_previous_status_check check (
        previous_status is null or previous_status in (
            'NOT_SUBMITTED', 'DRAFT_CREATED', 'FAILED', 'READY', 'IN_PRODUCTION',
            'SHIPPED', 'DELIVERED', 'NEEDS_ATTENTION', 'CANCELED'
        )
    ),
    constraint fulfillment_history_new_status_check check (new_status in (
        'NOT_SUBMITTED', 'DRAFT_CREATED', 'FAILED', 'READY', 'IN_PRODUCTION',
        'SHIPPED', 'DELIVERED', 'NEEDS_ATTENTION', 'CANCELED'
    ))
);
create index fulfillment_history_fulfillment_idx on fulfillment_history (fulfillment_id, created_at desc);
create index fulfillment_history_organization_idx on fulfillment_history (organization_id, created_at desc);

insert into fulfillment_history (organization_id, fulfillment_id, previous_status, new_status, note, actor_user_id, created_at)
select o.organization_id, f.id, null, f.status, 'Existing fulfillment state recorded when Phase 18 operations were enabled.', null, f.created_at
from fulfillment f
join "order" o on o.id = f.order_id;

create table fulfillment_reprint (
    id                     uuid primary key default gen_random_uuid(),
    organization_id        uuid not null references organization (id),
    fulfillment_id         uuid not null references fulfillment (id),
    order_id               uuid not null references "order" (id),
    status                 text not null default 'REQUESTED',
    reason                 text not null,
    vendor_order_reference text,
    carrier                text,
    tracking_number        text,
    tracking_url           text,
    internal_notes         text,
    requested_by_user_id   uuid not null references app_user (id),
    created_at             timestamptz not null default now(),
    updated_at             timestamptz not null default now(),
    shipped_at             timestamptz,
    delivered_at           timestamptz,
    constraint fulfillment_reprint_status_check check (
        status in ('REQUESTED', 'IN_PRODUCTION', 'SHIPPED', 'DELIVERED', 'CANCELED')
    )
);
create index fulfillment_reprint_order_idx on fulfillment_reprint (order_id, created_at desc);
create index fulfillment_reprint_organization_idx on fulfillment_reprint (organization_id, status, created_at desc);
create unique index fulfillment_reprint_one_open_idx on fulfillment_reprint (fulfillment_id)
    where status in ('REQUESTED', 'IN_PRODUCTION', 'SHIPPED');
