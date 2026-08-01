# ADR-047: Phase 16 Slice 5 — Reusable Event Templates

**Status:** Accepted and implemented 2026-07-31

## Context

LeagueLift already supports manual organization, team, and tournament events, but staff must repeatedly re-enter the same practice duration, arrival expectations, venue, field/court, meeting point, directions, visibility, and time zone. Phase 16 calls for reusable event templates to reduce that administrative burden. The product boundary remains explicit: LeagueLift is not adding automated schedule generation, availability optimization, or a recurring-event engine.

## Decision

**1. Templates are organization-owned defaults, not events.** V33 adds `event_template` with an organization owner, a human-readable name, event type, optional title/description, duration, arrival and meeting offsets, time zone, location defaults, visibility, lifecycle status, and creator/updater audit fields. A template has no start date, opponent, RSVP state, source/provider identity, or publication state.

**2. Active template names are unique per organization.** A partial unique index enforces case-insensitive uniqueness among active templates. Archiving releases the name for future use without deleting history.

**3. Organization owners and administrators manage templates.** Active organization members may list active templates so scoped coaches and tournament staff can use them during event entry. Only OWNER/ADMINISTRATOR (and audited Platform Admin support access through the existing organization boundary) may create, update, list archived templates, or archive one.

**4. Applying a template remains an ordinary manual event creation.** The React event form reads an active template and pre-fills its supported fields. Once a start time is supplied, duration and pre-start offsets derive optional end, arrival, and meeting timestamps; explicitly entered values override those derived defaults. Submission still goes through the existing event-create endpoint and `EventService`, producing a normal `MANUAL` `DRAFT` event with the same authorization, validation, and audit behavior as manual entry. No template identifier is stored on the event because the event is an independent snapshot after creation.

**5. No recurrence or automatic publication.** Templates never create multiple events, run on a schedule, publish an event, send notifications, change existing events, or synchronize with imported sources. A user reviews the pre-filled form and explicitly creates one draft event.

**6. Archive instead of delete.** Archive is idempotent and preserves the template record and audit history. Archived templates cannot be edited or selected for new events; events previously created from their defaults remain unchanged.

## Consequences

- Staff can create common practice, game, meeting, and tournament drafts with fewer repeated inputs.
- Team/tournament staff can use organization-approved templates without receiving organization-wide template-management authority.
- Timing offsets are convenience defaults, not hidden scheduling rules; the final timestamps are visible and editable before submission.
- No recurrence model, template-to-event foreign key, or automatic scheduler is introduced.
- Phase 16 remains partial: season rollover, archive, and selective-copy controls are the final planned slice.
