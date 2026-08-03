-- Phase 19 slices 19.3-19.4 (ADR-055): personal Google Calendar
-- selection/mapping scaffolding plus help content. This migration stores no
-- provider credential and enables no external synchronization by itself.

create table google_calendar_connection_setting (
    connection_id             uuid primary key references integration_connection (id),
    selected_calendar_id      text,
    selected_calendar_name    text,
    selected_calendar_timezone text,
    sync_direction            text not null default 'LEAGUELIFT_TO_GOOGLE',
    automatic_sync_enabled    boolean not null default false,
    last_calendar_listed_at   timestamptz,
    created_at                timestamptz not null default now(),
    updated_at                timestamptz not null default now(),
    constraint google_calendar_setting_direction_check check (sync_direction in ('LEAGUELIFT_TO_GOOGLE'))
);

create table google_calendar_event_mapping (
    id                       uuid primary key default gen_random_uuid(),
    connection_id            uuid not null references integration_connection (id),
    event_id                 uuid not null references event (id),
    external_calendar_id     text not null,
    external_event_id        text not null,
    external_etag            text,
    sync_status              text not null default 'PENDING',
    last_export_hash         char(64),
    last_synced_at           timestamptz,
    last_error_code          text,
    last_error_message       text,
    created_at               timestamptz not null default now(),
    updated_at               timestamptz not null default now(),
    constraint google_calendar_mapping_status_check check (sync_status in ('PENDING', 'SYNCED', 'FAILED', 'DELETED')),
    constraint google_calendar_mapping_error_check check (
        (sync_status = 'FAILED' and last_error_code is not null and last_error_message is not null) or
        (sync_status <> 'FAILED')
    )
);

create unique index google_calendar_mapping_event_unique_idx
    on google_calendar_event_mapping (connection_id, event_id);
create unique index google_calendar_mapping_external_unique_idx
    on google_calendar_event_mapping (connection_id, external_calendar_id, external_event_id);
create index google_calendar_mapping_status_idx
    on google_calendar_event_mapping (connection_id, sync_status, updated_at desc);

insert into support_article
    (id, slug, title, summary, body_markdown, category, audience, status, sort_order, published_at)
values
    ('10000000-0000-4000-8000-000000000016', 'connecting-google-calendar',
     'Connecting Google Calendar',
     'How personal Google Calendar connections will work and how to keep using ICS while direct sync is unavailable.',
     E'## Personal connection\n\nGoogle Calendar is a personal integration. When LeagueLift has activated its verified Google OAuth application, any signed-in adult or permitted athlete can open **My Integrations**, authorize a Google account, and select one destination calendar. Organization owners do not connect Google Calendar on behalf of everyone else.\n\n## Current availability\n\nUntil the provider is activated and verified, LeagueLift shows **Not configured** and does not request Google credentials. Standard ICS downloads and Add to Calendar files continue to work without a Google connection.\n\n## Planned first sync direction\n\nThe first supported direction is LeagueLift to Google Calendar. LeagueLift event identity is preserved through a durable mapping so updates and cancellations can target the same external event rather than creating duplicates. Automatic synchronization remains disabled until credentialed provider testing is complete.\n\n## Disconnecting\n\nDisconnecting removes LeagueLift access to the stored provider credential. Existing events already written to Google are not silently deleted unless the user explicitly chooses a supported removal workflow.',
     'Events and RSVP', 'PUBLIC', 'PUBLISHED', 56, now()),
    ('10000000-0000-4000-8000-000000000017', 'platform-managed-integrations',
     'Why some integrations are managed by LeagueLift',
     'Why organizations never enter LeagueLift platform credentials for payments, fulfillment, communications, or storage.',
     E'## Platform-operated providers\n\nStripe, Printify, Resend, Twilio, and DigitalOcean Spaces are LeagueLift infrastructure. Their credentials belong to the LeagueLift environment, not to an organization account. Organization users should never be asked to paste these provider secrets into LeagueLift.\n\n## What organizations can see\n\nCustomer-facing pages show the resulting LeagueLift workflow, such as checkout, fulfillment status, email or SMS delivery results, and media uploads. Only authorized LeagueLift Platform Administrators can see sanitized configuration readiness and provider-operation health. Raw keys, tokens, webhook secrets, and permanent storage credentials are never returned to a browser.',
     'Troubleshooting', 'OWNER_ADMIN', 'PUBLISHED', 57, now());
