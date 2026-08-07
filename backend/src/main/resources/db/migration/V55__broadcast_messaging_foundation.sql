-- Phase 25 slice 25.1: organization/team broadcast-thread messaging foundation (ADR-074).
-- A thread is one-way in this slice: authorized staff append messages and recipients read them.
-- Every message owns an immutable recipient/delivery snapshot. No sent-message update/delete path exists.
create table message_thread (
    id                  uuid primary key default gen_random_uuid(),
    organization_id     uuid not null references organization (id),
    scope_type          text not null,
    scope_id            uuid not null,
    thread_type         text not null default 'BROADCAST',
    idempotency_key     text not null,
    title               text not null,
    audience            text not null,
    email_enabled       boolean not null default true,
    sms_enabled         boolean not null default false,
    status              text not null default 'OPEN',
    created_by_user_id  uuid not null references app_user (id),
    archived_by_user_id uuid references app_user (id),
    archived_at         timestamptz,
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now(),
    constraint message_thread_scope_type_check check (scope_type in ('ORGANIZATION', 'TEAM')),
    constraint message_thread_scope_check check (
        (scope_type = 'ORGANIZATION' and scope_id = organization_id) or scope_type = 'TEAM'
    ),
    constraint message_thread_type_check check (thread_type in ('BROADCAST')),
    constraint message_thread_key_check check (char_length(trim(idempotency_key)) between 8 and 160),
    constraint message_thread_title_check check (char_length(trim(title)) between 3 and 180),
    constraint message_thread_audience_check check (audience in ('ALL', 'STAFF', 'GUARDIANS', 'ATHLETES')),
    constraint message_thread_status_check check (status in ('OPEN', 'ARCHIVED')),
    constraint message_thread_archive_check check (
        (status = 'ARCHIVED' and archived_at is not null and archived_by_user_id is not null) or
        (status = 'OPEN' and archived_at is null and archived_by_user_id is null)
    ),
    constraint message_thread_id_org_unique unique (id, organization_id),
    constraint message_thread_idempotency_unique unique (organization_id, idempotency_key)
);
create index message_thread_scope_idx on message_thread (organization_id, scope_type, scope_id, status, updated_at desc);

create table message_entry (
    id              uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organization (id),
    thread_id       uuid not null,
    sender_user_id  uuid not null references app_user (id),
    idempotency_key text not null,
    body            text not null,
    sent_at         timestamptz not null,
    created_at      timestamptz not null default now(),
    constraint message_entry_thread_fk foreign key (thread_id, organization_id)
        references message_thread (id, organization_id),
    constraint message_entry_key_check check (char_length(trim(idempotency_key)) between 8 and 160),
    constraint message_entry_body_check check (char_length(trim(body)) between 1 and 5000),
    constraint message_entry_id_org_unique unique (id, organization_id),
    constraint message_entry_idempotency_unique unique (thread_id, idempotency_key)
);
create index message_entry_thread_idx on message_entry (thread_id, sent_at);

create table message_recipient (
    id              uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organization (id),
    message_id      uuid not null,
    recipient_key   text not null,
    recipient_type  text not null,
    user_id         uuid references app_user (id),
    household_id    uuid references household (id),
    display_name    text not null,
    email           text,
    phone           text,
    access_reason   text not null default 'TARGETED',
    in_app_visible  boolean not null default false,
    email_status    text not null default 'NONE',
    sms_status      text not null default 'NONE',
    read_at         timestamptz,
    email_sent_at   timestamptz,
    sms_sent_at     timestamptz,
    last_error      text,
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    constraint message_recipient_message_fk foreign key (message_id, organization_id)
        references message_entry (id, organization_id),
    constraint message_recipient_type_check check (recipient_type in ('STAFF', 'GUARDIAN', 'ATHLETE')),
    constraint message_recipient_access_reason_check check (access_reason in ('TARGETED', 'GUARDIAN_VISIBILITY')),
    constraint message_recipient_email_status_check check (email_status in ('NONE', 'PENDING', 'SENT', 'FAILED', 'SKIPPED')),
    constraint message_recipient_sms_status_check check (sms_status in ('NONE', 'PENDING', 'SENT', 'FAILED', 'SKIPPED')),
    constraint message_recipient_guardian_visibility_delivery_check check (
        access_reason <> 'GUARDIAN_VISIBILITY' or (in_app_visible = true and email_status = 'NONE' and sms_status = 'NONE' and user_id is not null)
    ),
    constraint message_recipient_unique unique (message_id, recipient_key)
);
create index message_recipient_user_idx on message_recipient (user_id, read_at, created_at desc) where in_app_visible = true;
create index message_recipient_delivery_idx on message_recipient (message_id, email_status, sms_status);

-- Cross-phase Help Center maintenance requirement: publish audience-scoped coverage for the new feature.
insert into support_article
    (id, slug, title, summary, body_markdown, category, audience, status, sort_order, published_at)
values
    ('25010000-0000-4000-8000-000000000001', 'manage-broadcast-message-threads',
     'Manage broadcast message threads',
     'Create organization or team broadcast threads, choose an audience, and send append-only updates.',
     E'## Create a broadcast thread\n\nAuthorized organization managers and team staff can open **Messages**, choose an organization or team scope, select the intended audience, and create a thread. Email and opted-in SMS can be enabled as delivery fallbacks; every eligible recipient also receives an in-app copy when an activated account exists.\n\n## Send updates\n\nMessages are append-only. Each send snapshots the eligible recipients and their delivery preferences at that moment, so later roster changes never rewrite who received an older message. Archive a thread when it should stop receiving new messages.\n\n## Athlete visibility\n\nWhen a broadcast targets activated athletes, linked guardians receive automatic in-app read visibility. This guardian visibility copy does not silently add guardian email or SMS delivery.',
     'Teams and Families', 'OWNER_ADMIN', 'PUBLISHED', 70, now()),
    ('25010000-0000-4000-8000-000000000002', 'coach-broadcast-message-threads',
     'Send team broadcast messages',
     'Use a team-scoped thread for one-way roster updates without opening family replies yet.',
     E'Coaches with team communication permission can create a team broadcast thread and send one-way updates to staff, guardians, activated athletes, or everyone in scope. Each message keeps its own recipient snapshot. Guardians and athletes can read only messages addressed to their accounts; recipient replies are not available in this Phase 25.1 tier.',
     'Teams and Families', 'COACH', 'PUBLISHED', 70, now()),
    ('25010000-0000-4000-8000-000000000003', 'read-broadcast-messages',
     'Read Rally26 broadcast messages',
     'Find organization and team broadcast threads sent to your account and mark messages as read.',
     E'Open **Messages** from the Rally26 navigation to see broadcast threads that include your account. Select a thread to read its message history and mark unread items as read. Phase 25.1 broadcasts are one-way, so recipient replies are not available yet. Email and SMS may also be sent when the organization enables them and your household delivery preferences allow them.',
     'Teams and Families', 'GUARDIAN', 'PUBLISHED', 70, now()),
    ('25010000-0000-4000-8000-000000000004', 'athlete-broadcast-messages',
     'Athlete broadcast messages',
     'Read team or organization updates addressed to an activated athlete account.',
     E'Activated athlete accounts can read broadcast messages addressed to athletes or to everyone in their organization/team scope. Broadcasts are one-way in this phase. Linked guardians automatically receive in-app read visibility for athlete-targeted messages; this transparency is not an optional thread setting.',
     'Getting Started', 'ATHLETE', 'PUBLISHED', 70, now());
