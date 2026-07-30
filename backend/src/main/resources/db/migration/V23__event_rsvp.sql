-- Phase 10 slice 2 (ADR-027): RSVP — DESIGN-DOC.md section 14.1A. One row per
-- (event, participant): "a later response replaces the effective response while
-- preserving history" is satisfied by updating this row in place (updated_at moves,
-- created_at doesn't) plus an `event.rsvp_changed` audit_event capturing the
-- old/new response for history — not a separate append-only history table.
--
-- Simple v1 policy (founder decision, 2026-07-30): MAYBE is a real response; no
-- deadline/locking/staff-override exists this slice — every response column here is
-- exactly what that policy needs, nothing more.

create table event_rsvp (
    id                    uuid primary key default gen_random_uuid(),
    event_id              uuid not null references event (id),
    participant_id        uuid not null references participant (id),
    response              text not null,
    note                  text,
    responded_by_user_id  uuid not null references app_user (id),
    source                text not null,
    created_at            timestamptz not null default now(),
    updated_at            timestamptz not null default now(),
    constraint event_rsvp_response_check check (response in ('ATTENDING', 'NOT_ATTENDING', 'MAYBE', 'NO_RESPONSE')),
    constraint event_rsvp_source_check check (source in ('SELF', 'GUARDIAN', 'ADMIN')),
    constraint event_rsvp_unique unique (event_id, participant_id)
);

create index event_rsvp_event_id_idx on event_rsvp (event_id);
create index event_rsvp_participant_id_idx on event_rsvp (participant_id);
