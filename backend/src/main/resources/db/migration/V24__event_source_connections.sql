-- Phase 12 slice 1 (ADR-031): the connector foundation for org-connected event
-- sources. Only providers with an ongoing, stateful connection get a row here —
-- CSV_IMPORT is a one-time bulk action (slice 2) and never appears in this table;
-- `event.provider`/`connection_id` (reserved since Phase 10, V22) is populated
-- directly by the CSV import endpoint instead. ICS_FEED is the only provider with
-- a real implementation this slice; MAXPREPS/GAMECHANGER are allowed by the check
-- constraint so a future real connection can be stored, but nothing creates one yet
-- (they render as disabled "coming soon" cards on the Integrations page).
--
-- Multiple connections per organization are allowed (an org may subscribe to more
-- than one external ICS feed); the partial unique index only prevents the same URL
-- being connected twice while ACTIVE, not across all time (a re-connect after
-- disconnecting is fine).

create table event_source_connection (
    id                  uuid primary key default gen_random_uuid(),
    organization_id     uuid not null references organization (id),
    provider            text not null,
    label               text not null,
    feed_url            text,
    status              text not null default 'ACTIVE',
    last_synced_at      timestamptz,
    last_sync_status    text,
    last_sync_error     text,
    created_by_user_id  uuid not null references app_user (id),
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now(),
    constraint event_source_connection_provider_check check (provider in ('ICS_FEED', 'MAXPREPS', 'GAMECHANGER')),
    constraint event_source_connection_status_check check (status in ('ACTIVE', 'DISCONNECTED')),
    constraint event_source_connection_last_sync_status_check check (last_sync_status is null or last_sync_status in ('SUCCESS', 'FAILED')),
    constraint event_source_connection_ics_feed_url_check check (provider != 'ICS_FEED' or feed_url is not null)
);

create index event_source_connection_organization_id_idx on event_source_connection (organization_id);

create unique index event_source_connection_active_feed_url_idx on event_source_connection (organization_id, feed_url)
    where status = 'ACTIVE' and feed_url is not null;
