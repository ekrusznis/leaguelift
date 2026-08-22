-- Social Sharing & Connected Accounts, Slice 5 (brief §14). One row per publish
-- attempt, success or failure — never rewritten, so this is a real audit trail of
-- what Rally26 actually posted on a user's behalf, not just a cache of the latest
-- state.
create table social_publishing_history (
    id                      uuid primary key default gen_random_uuid(),
    draft_id                uuid not null references social_post_draft (id),
    user_id                 uuid not null references app_user (id),
    organization_id         uuid not null references organization (id),
    provider                text not null,
    social_connection_id    uuid references integration_connection (id),
    source_type             text not null,
    source_id               uuid not null,
    caption_snapshot        text not null,
    public_url              text not null,
    provider_post_id        text,
    provider_post_url       text,
    status                  text not null,
    failure_code            text,
    failure_message_safe    text,
    published_at            timestamptz,
    created_at              timestamptz not null default now(),
    constraint social_publishing_history_status_check check (status in (
        'PUBLISHING', 'PUBLISHED', 'FAILED'
    ))
);

create index social_publishing_history_user_idx on social_publishing_history (user_id, created_at desc);
create index social_publishing_history_organization_idx on social_publishing_history (organization_id, created_at desc);
