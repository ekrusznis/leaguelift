-- Phase 25 slice 25.3: messaging safety/moderation foundation (ADR-076).
-- Athlete-created conversations remain deliberately disabled. This slice provides
-- the safety gate they must pass: immutable message retention, user reporting,
-- manager review history, and reversible thread safety locks.

alter table message_thread add column safety_locked_at timestamptz;
alter table message_thread add column safety_locked_by_user_id uuid references app_user (id);
alter table message_thread add column safety_lock_reason text;
alter table message_thread add constraint message_thread_safety_lock_check check (
    (safety_locked_at is null and safety_locked_by_user_id is null and safety_lock_reason is null) or
    (safety_locked_at is not null and safety_locked_by_user_id is not null and
     char_length(trim(safety_lock_reason)) between 5 and 1000)
);

-- Needed for an FK that proves a report's message belongs to its exact thread and organization.
alter table message_entry add constraint message_entry_id_thread_org_unique unique (id, thread_id, organization_id);

create table message_safety_report (
    id                  uuid primary key default gen_random_uuid(),
    organization_id     uuid not null references organization (id),
    thread_id           uuid not null,
    message_id          uuid not null,
    reporter_user_id    uuid not null references app_user (id),
    reason              text not null,
    details             text,
    status              text not null default 'OPEN',
    assigned_to_user_id uuid references app_user (id),
    resolution_note     text,
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now(),
    resolved_at         timestamptz,
    constraint message_safety_report_message_fk foreign key (message_id, thread_id, organization_id)
        references message_entry (id, thread_id, organization_id),
    constraint message_safety_report_thread_fk foreign key (thread_id, organization_id)
        references message_thread (id, organization_id),
    constraint message_safety_report_reason_check check (
        reason in ('HARASSMENT', 'BULLYING', 'INAPPROPRIATE_CONTENT', 'SAFETY_CONCERN', 'SPAM', 'OTHER')
    ),
    constraint message_safety_report_status_check check (status in ('OPEN', 'IN_REVIEW', 'RESOLVED', 'DISMISSED')),
    constraint message_safety_report_details_check check (details is null or char_length(trim(details)) between 1 and 2000),
    constraint message_safety_report_resolution_check check (
        (status in ('OPEN', 'IN_REVIEW') and resolved_at is null) or
        (status in ('RESOLVED', 'DISMISSED') and resolved_at is not null and
         resolution_note is not null and char_length(trim(resolution_note)) between 1 and 2000)
    ),
    constraint message_safety_report_id_org_unique unique (id, organization_id),
    constraint message_safety_report_id_thread_org_unique unique (id, thread_id, organization_id)
);
create unique index message_safety_report_active_reporter_message_unique
    on message_safety_report (message_id, reporter_user_id) where status in ('OPEN', 'IN_REVIEW');
create index message_safety_report_management_idx
    on message_safety_report (organization_id, status, created_at desc);
create index message_safety_report_reporter_idx
    on message_safety_report (reporter_user_id, created_at desc);

create table message_moderation_event (
    id              uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organization (id),
    report_id       uuid,
    thread_id       uuid not null,
    message_id      uuid,
    actor_user_id   uuid not null references app_user (id),
    event_type      text not null,
    note            text,
    created_at      timestamptz not null default now(),
    constraint message_moderation_event_report_fk foreign key (report_id, thread_id, organization_id)
        references message_safety_report (id, thread_id, organization_id),
    constraint message_moderation_event_thread_fk foreign key (thread_id, organization_id)
        references message_thread (id, organization_id),
    constraint message_moderation_event_message_fk foreign key (message_id, organization_id)
        references message_entry (id, organization_id),
    constraint message_moderation_event_type_check check (
        event_type in ('REPORT_CREATED', 'REVIEW_STARTED', 'RESOLVED', 'DISMISSED', 'THREAD_LOCKED', 'THREAD_UNLOCKED')
    ),
    constraint message_moderation_event_note_check check (note is null or char_length(trim(note)) between 1 and 2000)
);
create index message_moderation_event_report_idx on message_moderation_event (report_id, created_at, id);
create index message_moderation_event_thread_idx on message_moderation_event (thread_id, created_at desc);

-- Safety-review evidence is append-only in Phase 25. No product/API path may edit or
-- delete a sent message or moderation event. Final legal retention duration remains
-- a pre-launch policy decision; until then the application retains this history.
create or replace function reject_messaging_history_mutation() returns trigger as $$
begin
    raise exception 'Messaging safety history is append-only';
end;
$$ language plpgsql;

create trigger message_entry_append_only
    before update or delete on message_entry
    for each row execute function reject_messaging_history_mutation();

create trigger message_moderation_event_append_only
    before update or delete on message_moderation_event
    for each row execute function reject_messaging_history_mutation();

create trigger message_safety_report_no_delete
    before delete on message_safety_report
    for each row execute function reject_messaging_history_mutation();

insert into support_article
    (id, slug, title, summary, body_markdown, category, audience, status, sort_order, published_at)
values
    ('25030000-0000-4000-8000-000000000001', 'review-message-safety-reports',
     'Review message safety reports',
     'Review reported messages, record a decision, and temporarily safety-lock a thread when conversation must pause.',
     E'## Safety review queue\n\nOpen **Messages** and use the safety review section for an organization or team you manage. Every report points to the exact retained message, its thread, the reporter, and the selected reason.\n\n## Locking a thread\n\nA safety lock pauses every new broadcast, coach reply, and family reply without deleting or rewriting history. Record a clear reason when locking and a review note before unlocking.\n\n## Moderation history\n\nStarting review, resolving, dismissing, locking, and unlocking create append-only moderation events. Sent message content is also append-only in this phase. Athlete-created direct/group conversations are still disabled until the separate safe-sport review is completed and recorded.',
     'Teams and Families', 'OWNER_ADMIN', 'PUBLISHED', 72, now()),
    ('25030000-0000-4000-8000-000000000002', 'coach-message-safety-tools',
     'Use message safety tools as a coach',
     'Review safety reports for teams you manage and pause a conversation while a concern is investigated.',
     E'Coaches with team communication permission can review reports tied to that team, start a review, resolve or dismiss with a recorded note, and apply a temporary safety lock. A locked thread remains readable but cannot receive new messages until an authorized reviewer unlocks it. Reports and moderation history are retained for safety review.',
     'Teams and Families', 'COACH', 'PUBLISHED', 72, now()),
    ('25030000-0000-4000-8000-000000000003', 'report-a-message-safety-concern',
     'Report a message safety concern',
     'Report a specific Rally26 message you can see without deleting the conversation or notifying other thread members.',
     E'Open **Messages**, find the specific message, and choose **Report**. Select the closest reason and optionally add context. Rally26 records the exact message and thread for authorized safety reviewers. Other conversation members do not receive the report itself. You can view the status of reports you submitted from the Messages safety area.',
     'Teams and Families', 'GUARDIAN', 'PUBLISHED', 72, now()),
    ('25030000-0000-4000-8000-000000000004', 'athlete-report-a-message',
     'Report a message as an athlete',
     'Activated athletes can report a specific message they can see to the organization safety-review queue.',
     E'If a message makes you uncomfortable or seems unsafe, open **Messages**, choose **Report** on that message, select a reason, and add context if useful. The report goes to authorized organization/team reviewers and does not remove the message from the retained conversation history. Linked guardians keep their normal visibility to athlete conversations. Athlete-to-athlete direct messaging is still not enabled by this safety-foundation slice.',
     'Getting Started', 'ATHLETE', 'PUBLISHED', 72, now());
