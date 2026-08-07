-- Phase 25 slice 25.5: athlete-to-athlete direct/group messaging behind the V58 SafeSport gate (ADR-078).
-- Code can ship while the feature remains disabled. No organization can create an ATHLETE_CONVERSATION
-- unless message_safe_sport_policy is APPROVED and athlete_messaging_enabled=true.

alter table message_thread drop constraint message_thread_type_check;
alter table message_thread add constraint message_thread_type_check
    check (thread_type in ('BROADCAST', 'CONVERSATION', 'ATHLETE_CONVERSATION'));

alter table message_thread drop constraint message_thread_type_audience_check;
alter table message_thread add constraint message_thread_type_audience_check check (
    (thread_type = 'BROADCAST' and audience in ('ALL', 'STAFF', 'GUARDIANS', 'ATHLETES')) or
    (thread_type in ('CONVERSATION', 'ATHLETE_CONVERSATION') and scope_type = 'TEAM' and audience = 'SELECTED')
);

insert into support_article
    (id, slug, title, summary, body_markdown, category, audience, status, sort_order, published_at)
values
    ('25050000-0000-4000-8000-000000000001', 'athlete-peer-messaging-safety',
     'Athlete peer messaging and guardian visibility',
     'How athlete direct/group conversations work when an organization has passed the Rally26 messaging safety gate.',
     E'Athlete-created messaging is available only after Rally26 records an approved SafeSport/compliance review for the organization. Athletes may contact only other activated athletes with a current shared team relationship. Every linked guardian is automatically added with read-only visibility and cannot be removed by an athlete. Messages are append-only, reportable, and subject to thread safety locks. A guardian **All messaging** restriction blocks the athlete from peer messaging until lifted.',
     'Getting Started', 'ATHLETE', 'PUBLISHED', 74, now()),
    ('25050000-0000-4000-8000-000000000002', 'guardian-athlete-peer-message-visibility',
     'Guardian visibility in athlete conversations',
     'Linked guardians automatically receive read access to their athlete’s Rally26 peer conversations.',
     E'When athlete peer messaging is enabled for an organization, Rally26 automatically adds each currently linked guardian to the athlete conversation as a read-only visibility member. Athletes cannot remove that visibility. Guardians may report individual messages and may record an **All messaging** restriction to stop peer messaging for their athlete. Relationship changes are reconciled by Rally26 before new messages are sent.',
     'Teams and Families', 'GUARDIAN', 'PUBLISHED', 74, now());
