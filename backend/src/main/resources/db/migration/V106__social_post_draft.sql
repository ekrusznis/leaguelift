-- Social Sharing & Connected Accounts, Slice 3. A generated, editable draft of a
-- Rally26-content social post (brief §7) — a snapshot at creation time, not a live
-- link to the source content, so a caption a user already started editing doesn't
-- shift under them if the source data changes later.
create table social_post_draft (
    id                  uuid primary key default gen_random_uuid(),
    source_type         text not null,
    source_id           uuid not null,
    organization_id     uuid not null references organization (id),
    team_id             uuid references team (id),
    title               text not null,
    caption             text not null,
    public_url          text not null,
    allowed_providers   jsonb not null default '[]'::jsonb,
    created_by_user_id  uuid not null references app_user (id),
    created_at          timestamptz not null default now(),
    constraint social_post_draft_source_type_check check (source_type in (
        'EVENT', 'FUNDRAISER', 'SPONSORSHIP', 'MEDIA', 'SWAG_PRODUCT', 'SWAG_SHOP'
    ))
);

create index social_post_draft_organization_idx on social_post_draft (organization_id, created_at desc);
create index social_post_draft_creator_idx on social_post_draft (created_by_user_id, created_at desc);
