# ADR-034: Phase 12 Slice 4 — Source-Owned vs. Overlay Field Enforcement

## Status
Accepted

## Context

Section 14.1A has stated since before Phase 10 began: *"source-owned core fields
normally include official opponent, start time, venue, and official status.
Rally26 overlay fields include arrival time, meeting point, uniform
instructions, parent-facing notes, publication, and RSVP settings. Editing a
source-owned field must be explicit: temporary local override, detach from
source, or update in the source system. Never silently fight the provider on
every sync."* Slices 2-3 built two real connectors (CSV import, ICS feed) that
now actually write source-owned fields — until this slice, nothing stopped a
staff member from directly overwriting them through the normal `PATCH` event
endpoint, which the next sync would then silently clobber right back (or worse,
the staff edit would silently win until the next sync, with no indication
either value was ever "the real one").

This closes open question #22 (external-event override policy) for the
"explicit" mechanism this codebase actually implements — one of the doc's three
named options, not all three.

## Decision

**1. `EventService.update()` rejects any attempt to set a source-owned field
(`status`, `startAt`, `endAt`, `venueName`, `address`, `latitude`, `longitude`,
`opponentTeamId`, `opponentName`) when `Event.provider` is non-null.** The check
is purely presence-based — matching `update()`'s own existing "non-null means
change it" coalesce convention (the same one every other call site already
relies on) — not a diff against current values, so even setting a source-owned
field to its own current value is rejected on an imported event. This is
deliberately strict: the point is "these fields sync from the source, don't
touch them here," not "only reject genuinely different values."

**2. Only "detach from source" is built, not "temporary local override."** Of
section 14.1A's three named options, detach is the simplest to reason about
correctly: a new `EventService.detachFromSource` clears `provider`/
`connection_id`/`external_event_id`/`external_sync_hash`/`source_updated_at` and
resets `source_type` back to `MANUAL`, after which the event behaves exactly
like one that was always manually created — no more sync will ever touch it
again, and every field becomes freely editable through the normal path. A
"temporary override that reverts to the source's value on next sync" would need
a whole second data model (per-field override flags plus original-vs-overridden
value tracking) that section 14.1A itself frames as one option among three, not
a requirement — building it now would be speculative ahead of any real usage
signal that staff actually want a *temporary* override rather than a permanent
detach. "Update in the source system" needs no code at all — it's just "edit
the org's own CSV/calendar and re-sync."

**3. Overlay fields (`title`, `description`, `arrivalAt`, `meetingAt`, `area`,
`meetingPoint`, `directionsNotes`, `visibility`) were never restricted and stay
that way.** Slices 2-3's sync writers never touch these fields either — a
CSV/ICS sync only ever writes the source-owned set plus title/description as a
one-time initial value, so there's no "fight the provider" risk for the overlay
set to begin with; `area` is grouped as overlay (not source-owned) since
`event.area_assigned`'s own existence as a distinct Phase 10 slice 4
notification type already implies it's routinely assigned locally, after the
source's own schedule is set — treating it as source-owned would block the
exact workflow that notification type exists for.

## Consequences

- An org that wants to permanently fork one imported event away from its feed
  (e.g., a one-off time change the source system won't reflect) must detach it
  first — a two-step action (detach, then edit), not a single edit. This is the
  intended friction: it makes "this event no longer tracks its source" an
  explicit, auditable (`event.detached_from_source`) choice rather than a side
  effect of an ordinary edit.
- A detached event has no way to re-attach to the same source connection later
  — the only path back is deleting and letting the next sync recreate it fresh
  (which would create a *new* event, since the old row's `external_event_id` is
  now gone). No "re-attach" action was built; nothing in section 14.1A asks for
  one, and it's a materially different (and riskier) operation than detaching.
- CSV import (ADR-032) and ICS feed sync (ADR-033) are entirely unaffected —
  both write through `EventRepository` directly, never through
  `EventService.update()`, so this enforcement only ever applies to the
  staff-facing edit path, exactly as intended.

## Alternatives Considered

- **A diff-based check (reject only if the new value actually differs from the
  current one)**: rejected — see decision 1; a presence-based check is simpler
  to reason about and matches `update()`'s own existing null-means-unchanged
  convention exactly, rather than introducing a second kind of comparison
  semantics for nine specific fields.
- **Building "temporary local override" now, since section 14.1A names it
  first**: rejected — see decision 2; the doc lists three options, not a
  mandate to build all three, and the override model is real, separate scope
  with no current evidence anyone needs it over the simpler detach path.
- **Treating `area` as source-owned** (grouping it with venue, since both
  describe "where"): rejected — see decision 3; `event.area_assigned`'s
  existence as its own notification type is itself evidence this codebase
  already treats area assignment as a distinct, locally-driven action, not
  part of the source's own core schedule data.
