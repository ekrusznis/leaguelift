-- Phase 17 remaining slices (ADR-050): role-aware Action Center is computed from
-- existing source-of-truth tables and therefore needs no persistence. This migration
-- adds the durable one-way announcement/reminder model and its snapshotted recipients.
--
-- One announcement is owned by exactly one organization and one ORGANIZATION/TEAM/
-- TOURNAMENT scope. System-generated campaign/event/fee/document reminders use the
-- same lifecycle and delivery pipeline as a manually-authored announcement rather
-- than creating four parallel notification systems.

create table announcement (
    id                  uuid primary key default gen_random_uuid(),
    organization_id     uuid not null references organization (id),
    scope_type           text not null,
    scope_id             uuid not null,
    kind                 text not null default 'GENERAL',
    related_entity_type  text,
    related_entity_id    uuid,
    target_household_id  uuid references household (id),
    source_key           text,
    title                text not null,
    body                 text not null,
    audience             text not null,
    status               text not null default 'DRAFT',
    email_enabled        boolean not null default true,
    sms_enabled          boolean not null default false,
    created_by_user_id   uuid not null references app_user (id),
    published_by_user_id uuid references app_user (id),
    published_at         timestamptz,
    archived_at          timestamptz,
    created_at           timestamptz not null default now(),
    updated_at           timestamptz not null default now(),
    constraint announcement_scope_type_check check (scope_type in ('ORGANIZATION', 'TEAM', 'TOURNAMENT')),
    constraint announcement_scope_presence_check check (
        (scope_type = 'ORGANIZATION' and scope_id = organization_id) or scope_type in ('TEAM', 'TOURNAMENT')
    ),
    constraint announcement_kind_check check (kind in (
        'GENERAL', 'CAMPAIGN_LAUNCH', 'EVENT_REMINDER', 'FEE_REMINDER', 'DOCUMENT_REMINDER'
    )),
    constraint announcement_related_presence_check check (
        (related_entity_type is null and related_entity_id is null) or
        (related_entity_type is not null and related_entity_id is not null)
    ),
    constraint announcement_title_check check (char_length(trim(title)) between 3 and 180),
    constraint announcement_body_check check (char_length(trim(body)) between 10 and 5000),
    constraint announcement_audience_check check (audience in ('ALL', 'STAFF', 'GUARDIANS', 'ATHLETES')),
    constraint announcement_status_check check (status in ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    constraint announcement_source_key_check check (source_key is null or char_length(trim(source_key)) between 8 and 160),
    constraint announcement_publish_state_check check (
        (status = 'DRAFT' and published_at is null and published_by_user_id is null and archived_at is null) or
        (status = 'PUBLISHED' and published_at is not null and published_by_user_id is not null and archived_at is null) or
        (status = 'ARCHIVED' and archived_at is not null)
    )
);

create unique index announcement_source_key_unique_idx
    on announcement (organization_id, source_key) where source_key is not null;
create index announcement_scope_idx on announcement (organization_id, scope_type, scope_id, status, created_at desc);
create index announcement_related_idx on announcement (related_entity_type, related_entity_id);

create table announcement_recipient (
    id                  uuid primary key default gen_random_uuid(),
    announcement_id     uuid not null references announcement (id) on delete cascade,
    organization_id     uuid not null references organization (id),
    recipient_key       text not null,
    recipient_type      text not null,
    user_id             uuid references app_user (id),
    household_id        uuid references household (id),
    display_name        text not null,
    email               text,
    phone               text,
    in_app_visible      boolean not null default false,
    email_status        text not null default 'NONE',
    sms_status          text not null default 'NONE',
    email_sent_at       timestamptz,
    sms_sent_at         timestamptz,
    read_at             timestamptz,
    last_error          text,
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now(),
    constraint announcement_recipient_key_check check (char_length(trim(recipient_key)) between 3 and 400),
    constraint announcement_recipient_type_check check (recipient_type in ('STAFF', 'GUARDIAN', 'ATHLETE')),
    constraint announcement_recipient_display_name_check check (char_length(trim(display_name)) between 1 and 180),
    constraint announcement_recipient_email_status_check check (email_status in ('NONE', 'PENDING', 'SENT', 'FAILED', 'SKIPPED')),
    constraint announcement_recipient_sms_status_check check (sms_status in ('NONE', 'PENDING', 'SENT', 'FAILED', 'SKIPPED')),
    constraint announcement_recipient_channel_check check (
        in_app_visible or email_status <> 'NONE' or sms_status <> 'NONE'
    ),
    constraint announcement_recipient_email_sent_check check (
        (email_status = 'SENT' and email_sent_at is not null) or email_status <> 'SENT'
    ),
    constraint announcement_recipient_sms_sent_check check (
        (sms_status = 'SENT' and sms_sent_at is not null) or sms_status <> 'SENT'
    ),
    constraint announcement_recipient_unique unique (announcement_id, recipient_key)
);

create index announcement_recipient_user_idx
    on announcement_recipient (user_id, read_at, created_at desc) where in_app_visible = true;
create index announcement_recipient_delivery_idx
    on announcement_recipient (announcement_id, email_status, sms_status);
