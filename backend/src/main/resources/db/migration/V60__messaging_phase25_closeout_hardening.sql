-- Phase 25 slice 25.6: messaging lifecycle/guardian-visibility closeout hardening (ADR-079).
-- Membership/recipient/thread rows are retained; lifecycle changes happen by archive/left_at/status updates.

create index if not exists message_thread_open_conversation_reconcile_idx
    on message_thread (thread_type, status, updated_at)
    where status = 'OPEN' and thread_type in ('CONVERSATION', 'ATHLETE_CONVERSATION');

create trigger message_thread_no_delete
    before delete on message_thread
    for each row execute function reject_messaging_history_mutation();
create trigger message_thread_member_no_delete
    before delete on message_thread_member
    for each row execute function reject_messaging_history_mutation();
create trigger message_recipient_no_delete
    before delete on message_recipient
    for each row execute function reject_messaging_history_mutation();

insert into support_article
    (id, slug, title, summary, body_markdown, category, audience, status, sort_order, published_at)
values
    ('25060000-0000-4000-8000-000000000001', 'messaging-membership-and-guardian-changes',
     'Messaging when rosters or guardians change',
     'How Rally26 keeps future conversation access aligned with current team and guardian relationships without rewriting history.',
     E'Rally26 periodically reconciles open conversations. Athletes who leave a team and guardians who are no longer linked stop receiving **new** conversation messages, while already-delivered history remains retained. Newly linked guardians are added as read-only visibility members before future athlete messages. A coach or staff member who loses team communication permission cannot keep sending simply because they were an older thread member.',
     'Teams and Families', 'OWNER_ADMIN', 'PUBLISHED', 75, now()),
    ('25060000-0000-4000-8000-000000000002', 'messaging-safety-and-privacy-summary',
     'Rally26 messaging safety and privacy',
     'A summary of guardian visibility, reporting, safety locks, contact restrictions, and athlete messaging controls.',
     E'Phase 25 messaging preserves sent messages and recipient history, supports message-level reporting and append-only moderation history, allows authorized reviewers to safety-lock a thread, enforces guardian visibility for athlete conversations, records guardian contact restrictions, and gates athlete-created messaging behind an explicit platform SafeSport/compliance review. These controls support an organization’s safety program but do not replace its governing-body, SafeSport, legal, or mandated-reporting obligations.',
     'Teams and Families', 'GUARDIAN', 'PUBLISHED', 75, now()),
    ('25060000-0000-4000-8000-000000000003', 'athlete-messaging-safety-summary',
     'Your Rally26 messaging safety controls',
     'What athletes should know about guardian visibility, reports, team eligibility, and safety locks.',
     E'Your Rally26 athlete conversations are limited to eligible teammates when the feature is enabled. Linked guardians receive read visibility automatically. You can report a specific message, and authorized reviewers can pause a thread while a concern is reviewed. Leaving a team removes your ability to send new messages in that team conversation; retained history is not silently rewritten or deleted.',
     'Getting Started', 'ATHLETE', 'PUBLISHED', 75, now());
