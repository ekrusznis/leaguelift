-- Phase 24.1: reusable Swag Brand Asset Library plus durable product archival.
-- Brand assets point at already-confirmed media assets. Products snapshot both the
-- brand-asset provenance and the media asset used for fulfillment; later source
-- archival never rewrites an existing product or order.

create table swag_brand_asset (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organization (id),
    team_id uuid references team (id),
    media_asset_id uuid not null references media_asset (id),
    name text not null,
    category text not null,
    status text not null default 'ACTIVE',
    created_by_user_id uuid not null references app_user (id),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint swag_brand_asset_name_check check (char_length(trim(name)) between 1 and 120),
    constraint swag_brand_asset_category_check check (category in (
        'PRIMARY', 'ALTERNATE', 'LIGHT', 'DARK', 'WORDMARK', 'MASCOT',
        'SMALL_PRINT', 'WIDE', 'SEASONAL', 'COMMEMORATIVE'
    )),
    constraint swag_brand_asset_status_check check (status in ('ACTIVE', 'ARCHIVED')),
    constraint swag_brand_asset_id_organization_unique unique (id, organization_id)
);

create index swag_brand_asset_organization_idx
    on swag_brand_asset (organization_id, status, created_at desc);
create index swag_brand_asset_team_idx
    on swag_brand_asset (team_id, status, created_at desc)
    where team_id is not null;
create index swag_brand_asset_media_asset_idx
    on swag_brand_asset (media_asset_id);

alter table product
    add column swag_brand_asset_id uuid,
    add constraint product_swag_brand_asset_scope_fk
        foreign key (swag_brand_asset_id, organization_id)
        references swag_brand_asset (id, organization_id);

create index product_swag_brand_asset_idx
    on product (swag_brand_asset_id)
    where swag_brand_asset_id is not null;
