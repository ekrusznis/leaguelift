# ADR-033: Phase 12 Slice 3 — ICS Feed Connector

## Status
Accepted

## Context

ADR-031 (slice 1) built the ICS Feed connect/disconnect flow but nothing consumed
a connection — no sync logic existed. This slice builds the actual scheduled
poller: fetch every ACTIVE `ICS_FEED` connection's URL, parse its `.ics` content,
and upsert matching `event` rows, mirroring the CSV import connector's identity/
dedup model (ADR-032) but on an ongoing interval instead of a one-time upload.

Slice 1's connect flow only captured a label and a feed URL — it didn't
anticipate what the poller would actually need. Two gaps surfaced once this slice
started and are fixed here, not deferred:

1. **Timezone.** A feed's `DTSTART`/`DTEND` values are frequently "floating" (no
   UTC offset, no `TZID`) — resolving them requires a zone, and nothing captured
   one at connect time.
2. **Team scope.** Synced events need to know which team (or org-wide) they
   belong to, exactly like CSV import's own per-upload team scope (ADR-032) — slice
   1 never asked for one.

A new migration (V25) adds both `timezone` (not null, defaulted to `'UTC'` for any
pre-existing row — pre-pilot, no real connections exist yet) and `team_id`
(nullable, org-wide when absent) to `event_source_connection`. The connect-ICS-feed
request, service, and frontend form all gained these two fields as part of this
slice, not as a separate corrective slice — the gap was found while building the
poller that actually needs them.

## Decision

**1. A hand-rolled ICS *reader* (`IcsFeedParser`) is the counterpart to
`IcsCalendarProvider`'s existing *generator* (ADR-028) — only the properties this
codebase's `event` model actually uses are read: `UID`, `SUMMARY`, `DESCRIPTION`,
`LOCATION`, `DTSTART`, `DTEND`, `STATUS`.** Everything else a real feed might carry
(`ORGANIZER`, `ATTENDEE`, `RRULE`, `VALARM`, `VTIMEZONE` blocks) is silently
ignored, not partially or incorrectly interpreted. No recurrence expansion — a
recurring `VEVENT` is read as the single literal occurrence it states, not
expanded into a series; nothing in this slice's scope asked for recurring-event
support. No library was added, matching the same "plain implementation over a new
dependency for one narrow, stable need" call ADR-028 already made for the
generator and ADR-032 made for CSV parsing.

**2. Every VEVENT's `UID` is the `external_event_id`; the connection's own id
(stringified) is the `connection_id`.** This reuses the exact `(provider,
connection_id, external_event_id)` unique index Phase 10 reserved and ADR-032
already put to real use — a feed's `UID` is RFC 5545's own required stable
identifier for exactly this purpose, so unlike CSV import there's no ambiguity to
resolve about what the dedup key should be.

**3. A SHA-256 hash of every synced field (`external_sync_hash`) makes an
unchanged feed re-fetch a safe no-op** — the same change-detection approach
ADR-032 established for CSV import, reused verbatim rather than inventing a
second convention.

**4. `DTSTART`/`DTEND` resolution order: explicit `TZID` parameter first, then a
`Z`-suffixed (UTC) value, then the connection's own configured timezone as the
floating-time fallback, then an all-day (`VALUE=DATE`) value as midnight in that
same fallback zone.** An unrecognized `TZID` string falls back to the
connection's timezone too, rather than failing the whole event — a best-effort
posture consistent with "ignore what we don't understand" (decision 1).

**5. Every parsed event defaults to `EventType.OTHER`, with `SUMMARY` becoming
`Event.title` directly** — an ICS feed carries a plain-text summary, not this
codebase's structured team/opponent model `displayTitle()` computes from, so
there's nothing to derive a smarter type or title from without guessing.

**6. `STATUS` maps `CONFIRMED`→`SCHEDULED`, `CANCELLED`→`CANCELLED`,
`TENTATIVE`→`TENTATIVE`; an absent or unrecognized status defaults to
`TENTATIVE` for a new event** (matching CSV import's own immediate-publish
posture, ADR-032 decision 6) **and is left alone (not overwritten) on an
existing event's update** — the poller's job is to sync content fields, not to
silently flip an event that staff may have manually published or postponed back
to whatever the source currently says, unless the source explicitly states a
status.

**7. One connection's sync failure never blocks another's, and one event's sync
failure never blocks the rest of that same feed.** `syncOne` catches around the
fetch step, the parse step, and each individual event's upsert separately —
letting an exception escape the `@Transactional` method would roll back every
successful upsert already committed in that same sync over one bad event, the
same reasoning ADR-032 applied to CSV row-level failures. A connection's
`last_sync_status`/`last_sync_error` reflects the *overall* attempt (`FAILED` if
any event failed, with a count), surfaced on the Integrations page.

**8. The poller is a plain `@Scheduled` job** (`leaguelift.ics-feed.sync.cron`,
default every 30 minutes) **scanning every ACTIVE connection across all
organizations in one pass** — mirroring `FeePaymentReminderScanner`'s exact
shape, not the outbox worker's claim/dispatch pattern, since there's no
per-event queue here, just "re-sync everything on an interval."

## Consequences

- No `VTIMEZONE`-based per-organizer timezone resolution — the connection's own
  single configured timezone is the only fallback for floating times, matching
  this event model's existing "one timezone per event" design (Phase 10).
- No recurrence (`RRULE`) support — a recurring practice published as one
  `VEVENT` with an `RRULE` syncs as a single occurrence, not a series. Revisit
  only if real usage shows recurring feeds are common.
- An ICS feed's own `STATUS` can only ever move a *new* event's initial status;
  updates never downgrade/upgrade an existing event's status automatically — see
  decision 6. A source that cancels an event won't auto-cancel the LeagueLift
  copy; this is an intentional, documented limitation, not an oversight, pending
  real usage evidence on whether that matters.
- The whole sync runs in-process on whatever instance the scheduled trigger
  fires on — no distributed lock. Acceptable at this codebase's current single-
  instance-per-environment scale; revisit if horizontal scaling of the backend
  ever actually happens (§18.4 — don't optimize for scale not yet reached).

## Alternatives Considered

- **A third-party ICS-parsing library** (e.g. ical4j, biweekly): rejected — see
  decision 1; the same narrow-and-stable-format reasoning ADR-028/ADR-032 already
  established applies equally to reading the format, not just writing it.
- **Deferring the timezone/team-scope gap to a separate "slice 1.5" migration**:
  rejected — the gap was discovered while building the very feature that needs
  it; fixing it as part of this slice (one migration, one set of DTO/form
  updates) is more honest than pretending it was planned from the start or
  leaving the poller unable to resolve floating times.
- **Letting a synced event's STATUS always overwrite the existing event's
  status on every re-sync**: rejected — see decision 6; this would let an
  external feed silently undo a staff member's own postpone/cancel action,
  which is a worse failure mode than a source-cancellation not propagating
  automatically.
