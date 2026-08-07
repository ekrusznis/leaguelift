-- Phase 25 slice 25.4: SafeSport policy gate and guardian discontinue-contact controls (ADR-077).
-- This does NOT assert that Rally26 has passed an external SafeSport/legal review. Every organization
-- begins PENDING with athlete-created messaging disabled. A platform administrator may record an
-- APPROVED review only with a durable reference; 25.5 still checks this gate on every athlete action.

create table message_safe_sport_policy (
    organization_id                        uuid primary key references organization (id),
    review_status                          text not null default 'PENDING',
    athlete_messaging_enabled              boolean not null default false,
    review_reference                       text,
    reviewed_by_user_id                    uuid references app_user (id),
    reviewed_at                            timestamptz,
    retention_mode                         text not null default 'PRESERVE_NO_AUTOMATIC_DELETION',
    guardian_visibility_required           boolean not null default true,
    adult_minor_open_transparent_required  boolean not null default true,
    parent_discontinue_requests_enforced   boolean not null default true,
    updated_at                             timestamptz not null default now(),
    constraint message_safe_sport_review_status_check check (review_status in ('PENDING', 'APPROVED', 'REJECTED')),
    constraint message_safe_sport_retention_check check (retention_mode = 'PRESERVE_NO_AUTOMATIC_DELETION'),
    constraint message_safe_sport_hard_rules_check check (
        guardian_visibility_required = true and
        adult_minor_open_transparent_required = true and
        parent_discontinue_requests_enforced = true
    ),
    constraint message_safe_sport_enablement_check check (
        athlete_messaging_enabled = false or
        (review_status = 'APPROVED' and review_reference is not null and char_length(trim(review_reference)) between 3 and 500
         and reviewed_by_user_id is not null and reviewed_at is not null)
    ),
    constraint message_safe_sport_review_metadata_check check (
        (review_status = 'PENDING' and reviewed_by_user_id is null and reviewed_at is null) or
        (review_status in ('APPROVED', 'REJECTED') and review_reference is not null
         and char_length(trim(review_reference)) between 3 and 500 and reviewed_by_user_id is not null and reviewed_at is not null)
    )
);

insert into message_safe_sport_policy (organization_id)
select id from organization
on conflict (organization_id) do nothing;

create table message_contact_restriction (
    id                    uuid primary key default gen_random_uuid(),
    organization_id       uuid not null references organization (id),
    participant_id        uuid not null references participant (id),
    requested_by_user_id  uuid not null references app_user (id),
    kind                  text not null,
    note                  text,
    status                text not null default 'ACTIVE',
    created_at            timestamptz not null default now(),
    lifted_at             timestamptz,
    lifted_by_user_id     uuid references app_user (id),
    lift_note             text,
    constraint message_contact_restriction_kind_check check (kind in ('ADULT_TO_MINOR', 'ALL_MESSAGING')),
    constraint message_contact_restriction_status_check check (status in ('ACTIVE', 'LIFTED')),
    constraint message_contact_restriction_note_check check (note is null or char_length(trim(note)) between 1 and 1000),
    constraint message_contact_restriction_lifecycle_check check (
        (status = 'ACTIVE' and lifted_at is null and lifted_by_user_id is null and lift_note is null) or
        (status = 'LIFTED' and lifted_at is not null and lifted_by_user_id is not null
         and lift_note is not null and char_length(trim(lift_note)) between 3 and 1000)
    ),
    constraint message_contact_restriction_id_org_unique unique (id, organization_id)
);
create unique index message_contact_restriction_active_unique
    on message_contact_restriction (organization_id, participant_id, kind) where status = 'ACTIVE';
create index message_contact_restriction_guardian_idx
    on message_contact_restriction (requested_by_user_id, status, created_at desc);
create index message_contact_restriction_participant_idx
    on message_contact_restriction (organization_id, participant_id, status);

-- Restriction history is safety evidence. Lift by transition; never erase the request itself.
create trigger message_contact_restriction_no_delete
    before delete on message_contact_restriction
    for each row execute function reject_messaging_history_mutation();

insert into support_article
    (id, slug, title, summary, body_markdown, category, audience, status, sort_order, published_at)
values
    ('25040000-0000-4000-8000-000000000001', 'messaging-safe-sport-policy-gate',
     'Messaging SafeSport policy gate',
     'Understand the open-and-transparent communication rules Rally26 enforces before athlete-created messaging can be enabled.',
     E'Rally26 keeps athlete-created messaging **disabled by default** until an authorized platform review is recorded. Adult-to-minor conversations remain open and transparent: an athlete’s linked guardian is automatically included with read visibility, sent-message history is retained, and guardian discontinue-contact requests are enforced. Rally26 does not treat this technical gate as legal certification; organizations remain responsible for their governing-body and SafeSport obligations.',
     'Teams and Families', 'OWNER_ADMIN', 'PUBLISHED', 73, now()),
    ('25040000-0000-4000-8000-000000000002', 'guardian-message-contact-restrictions',
     'Pause messaging to your athlete',
     'Guardians can record a request to stop adult-to-minor communication or all Rally26 messaging for an athlete.',
     E'Open **Messages → Safety** to record a communication restriction for an athlete linked to your household. **Adult-to-minor** stops staff-originated messages to that athlete. **All messaging** also prevents athlete-created peer messaging when that feature is enabled. Rally26 keeps the request as safety history. If you later lift it, a note and timestamp are retained rather than deleting the original request.',
     'Teams and Families', 'GUARDIAN', 'PUBLISHED', 73, now());
