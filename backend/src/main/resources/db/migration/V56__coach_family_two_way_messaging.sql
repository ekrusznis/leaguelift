-- Phase 25 slice 25.2: coach-to-family two-way messaging (ADR-075).
-- V55 broadcast rows remain unchanged. This migration widens the thread model and
-- adds durable membership only for CONVERSATION threads.

alter table message_thread drop constraint message_thread_type_check;
alter table message_thread add constraint message_thread_type_check
    check (thread_type in ('BROADCAST', 'CONVERSATION'));

alter table message_thread drop constraint message_thread_audience_check;
alter table message_thread add constraint message_thread_audience_check
    check (audience in ('ALL', 'STAFF', 'GUARDIANS', 'ATHLETES', 'SELECTED'));

alter table message_thread add constraint message_thread_type_audience_check check (
    (thread_type = 'BROADCAST' and audience in ('ALL', 'STAFF', 'GUARDIANS', 'ATHLETES')) or
    (thread_type = 'CONVERSATION' and scope_type = 'TEAM' and audience = 'SELECTED')
);

create table message_thread_member (
    id              uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organization (id),
    thread_id       uuid not null,
    user_id         uuid not null references app_user (id),
    member_type     text not null,
    household_id    uuid references household (id),
    participant_id  uuid references participant (id),
    display_name    text not null,
    access_reason   text not null default 'TARGETED',
    can_reply       boolean not null default true,
    joined_at       timestamptz not null default now(),
    left_at         timestamptz,
    created_at      timestamptz not null default now(),
    constraint message_thread_member_thread_fk foreign key (thread_id, organization_id)
        references message_thread (id, organization_id),
    constraint message_thread_member_type_check check (member_type in ('STAFF', 'GUARDIAN', 'ATHLETE')),
    constraint message_thread_member_access_check check (access_reason in ('TARGETED', 'GUARDIAN_VISIBILITY')),
    constraint message_thread_member_display_name_check check (char_length(trim(display_name)) between 1 and 200),
    constraint message_thread_member_guardian_visibility_check check (
        access_reason <> 'GUARDIAN_VISIBILITY' or
        (member_type = 'GUARDIAN' and household_id is not null and can_reply = false)
    ),
    constraint message_thread_member_athlete_participant_check check (
        member_type <> 'ATHLETE' or participant_id is not null
    ),
    constraint message_thread_member_left_check check (left_at is null or left_at >= joined_at)
);
create unique index message_thread_member_active_user_unique
    on message_thread_member (thread_id, user_id) where left_at is null;
create index message_thread_member_user_idx
    on message_thread_member (user_id, left_at, joined_at desc);
create index message_thread_member_thread_idx
    on message_thread_member (thread_id, left_at, access_reason, can_reply);

-- Every conversation must stay team-scoped and selected-member based. Broadcast
-- history from V55 does not need backfilled membership rows.

insert into support_article
    (id, slug, title, summary, body_markdown, category, audience, status, sort_order, published_at)
values
    ('25020000-0000-4000-8000-000000000001', 'manage-family-message-conversations',
     'Manage coach-to-family conversations',
     'Start a team-scoped two-way conversation with selected guardians or activated athletes.',
     E'## Start a family conversation\n\nOpen **Messages**, choose a team you manage, and select one or more eligible guardians or activated athletes. A conversation is limited to people connected to that team; unrelated household or athlete IDs cannot be added by request.\n\n## Replies and history\n\nSelected members may reply in the shared thread. Messages are append-only and each send snapshots in-app, email, and opted-in SMS destinations at that moment. Archiving closes the conversation to new replies without deleting its history.\n\n## Guardian transparency\n\nWhen an activated athlete is selected, that athlete''s linked guardians receive automatic in-app read visibility. An automatic guardian observer is read-only unless that guardian was also explicitly selected as a conversation member.',
     'Teams and Families', 'OWNER_ADMIN', 'PUBLISHED', 71, now()),
    ('25020000-0000-4000-8000-000000000002', 'coach-family-two-way-messages',
     'Message a family from Rally26',
     'Create a direct or small-group team conversation and receive replies in one thread.',
     E'Coaches with team communication permission can start a conversation from **Messages** with selected guardians or activated athletes on that team. The person who creates the conversation is a member of it. Selected family members can reply; automatic guardian observers of athlete conversations can read but cannot reply unless explicitly selected. Use a broadcast thread instead when replies are not needed.',
     'Teams and Families', 'COACH', 'PUBLISHED', 71, now()),
    ('25020000-0000-4000-8000-000000000003', 'reply-to-coach-messages',
     'Reply to a coach conversation',
     'Read and reply to team conversations where you are an explicitly selected family member.',
     E'Open **Messages** and choose the conversation. If you were explicitly selected, the reply box appears at the bottom of the thread. Replies stay in the same append-only history. If you are viewing only because Rally26 added a guardian transparency copy for your athlete, the thread is read-only unless the coach also selected you as a participant.',
     'Teams and Families', 'GUARDIAN', 'PUBLISHED', 71, now()),
    ('25020000-0000-4000-8000-000000000004', 'athlete-coach-conversations',
     'Reply to a coach conversation as an athlete',
     'Activated athletes may reply when a coach explicitly includes them in a team conversation.',
     E'When a coach explicitly includes your activated athlete account in a team conversation, you may read and reply in that thread. Linked guardians automatically receive in-app read visibility. Athlete-to-athlete direct messaging is not enabled by this feature and remains separately gated by Rally26 safe-sport requirements.',
     'Getting Started', 'ATHLETE', 'PUBLISHED', 71, now());
