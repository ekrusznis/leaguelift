# ADR-032: Phase 12 Slice 2 — CSV Import Connector

## Status
Accepted

## Context

ADR-031 (slice 1) built the Integrations page and the `event_source_connection`
foundation, deliberately excluding CSV import from that table since it's a
one-time bulk action, not an ongoing connection. This slice builds the actual
CSV upload-and-parse flow, the first real event-source connector this codebase
has (`MANUAL` aside).

## Decision

**1. One CSV upload is scoped to exactly one team (or org-wide) and one shared
timezone, supplied at upload time — never per-row.** Per-row team/timezone
columns would require name-matching an uploaded string against this
organization's real teams, a meaningfully bigger and more error-prone feature
(what happens on a typo, an ambiguous partial match, a team that doesn't exist
yet?) that nothing in section 14.1A asks for. A coach uploading their own
team's schedule already knows which team and timezone it's for.

**2. Required CSV columns are `external_id` and `event_type`**; `title`,
`description`, `opponent_name`, `start_at`, `end_at`, `arrival_at`,
`venue_name`, `address`, `area` are all optional. `external_id` is a
caller-supplied stable identifier (a game ID from the org's existing system,
a row number, anything stable across re-uploads) — without it, there is no way
to distinguish "update this row" from "this is a new event" on a re-upload,
and inventing a fuzzy match (same date + same opponent?) would silently merge
or duplicate rows in ways an org could easily miss. Requiring it explicitly is
more honest than guessing.

**3. `connection_id` (reserved on `event` since Phase 10, populated for the
first time here) is the string form of the target team id, or `"org:{orgId}"`
for an org-wide import — not a random per-upload id.** This makes the
`(provider, connection_id, external_event_id)` unique index do exactly what
decision 2 needs: re-uploading the *same* team's CSV with the same
`external_id` values updates the existing events (an intentional, expected
correction workflow), while two different teams' CSVs reusing the same
`external_id` strings don't collide with each other.

**4. Change detection uses a SHA-256 hash of every imported field, stored in
`external_sync_hash`** (reserved since Phase 10, populated for the first time
here) — re-uploading an unchanged CSV is a safe no-op (row skipped, counted as
"unchanged," no DB write), and only rows whose content actually changed since
the last import get a real `UPDATE`. No CSV-parsing library was added — a
hand-rolled RFC 4180 parser (`CsvUtil.parse`) handles quoted fields, embedded
commas/newlines, and escaped quotes, mirroring the "plain implementation over
a new dependency for one narrow, stable need" call this codebase already made
for ICS generation (ADR-028).

**5. `start_at`/`end_at`/`arrival_at` must be ISO-8601 instants (e.g.
`2026-09-05T15:30:00Z`), not separate date/time columns or a bare local
date-time.** A bare local time is ambiguous without re-deriving it against the
upload's own `timezone` field per-row, and real spreadsheet exports vary
enough in date/time formatting that guessing a locale-specific format would be
fragile. Requiring a single unambiguous instant format up front, with a clear
per-row validation error otherwise, is simpler and more honest than trying to
be lenient.

**6. Imported events start `TENTATIVE` (published, family-visible), not
`DRAFT`** — unlike `EventService.create`'s manual single-event flow, where
`DRAFT`-first lets a staff member preview before publishing one event at a
time. An org uploading a schedule has already reviewed it outside the system;
forcing a manual publish click per imported row (potentially dozens) would be
pure friction with no real benefit. This is a deliberate, documented departure
from `EventRepository.insert`'s previous DRAFT-only behavior, now controlled by
a new `initialStatus` parameter (defaulting to `DRAFT` for every existing
caller, so manual creation is unaffected).

**7. Bulk CSV import writes directly through `EventRepository`, bypassing
`EventService` and Phase 10 slice 4's per-change notification wiring
entirely.** A bulk schedule backfill firing one notification email per
imported row (potentially dozens at once) would be a flood, not a helpful
signal — nothing in the notification design (section 14.1A, ADR-029) was
built with a bulk-import case in mind. One audit event per *upload* (not per
row) is recorded instead, capturing created/updated/error counts.

**8. Row-level failures never abort the batch.** Each row is validated and
imported independently inside the same transaction; a malformed row (missing
`external_id`, an unrecognized `event_type`, a bad instant format) is
collected as a row-level error and the rest of the file still processes,
returned as a summary (`createdCount`/`updatedCount`/`unchangedCount`/
`errors`) so an admin can fix just the flagged rows and re-upload — not an
all-or-nothing failure that discards a mostly-good file over one typo.

## Consequences

- Imported events are immediately visible to families with no per-row review
  step — a genuine trust shift from the manual-creation flow's DRAFT-first
  default, justified by decision 6's reasoning but worth knowing explicitly.
- No notification fires for any CSV-imported event, created or updated — see
  decision 7. This is a real, deliberate gap (not an oversight) that a future
  slice could revisit (e.g. a single digest-style "N new events were added to
  your schedule" notification) if real usage shows it's wanted.
- A CSV upload can only ever create events under one team or org-wide per
  upload — importing a mixed multi-team file requires either multiple uploads
  (one per team) or waiting for a future per-row team-matching feature.
- The whole import runs in one database transaction — a very large file could
  hold a transaction open for a while. No pagination/chunking was built since
  nothing about typical schedule sizes (tens to low hundreds of rows) suggests
  this matters yet.

## Alternatives Considered

- **A third-party CSV parsing library** (e.g. Apache Commons CSV, opencsv):
  rejected — see decision 4; the actual format needed here (comma-separated,
  quoted-field escaping) is small, stable, and well-specified (RFC 4180), the
  same reasoning ADR-028 already used for ICS generation.
- **Fuzzy/best-effort event matching** (same date + same opponent counts as
  "the same event" across uploads, no `external_id` required): rejected — see
  decision 2; silently merging or duplicating events based on a guess is a
  worse failure mode than requiring one explicit, always-correct identifier
  column.
- **Per-row team name and timezone columns**: rejected — see decision 1; name-
  matching against real teams is real, separate scope with its own edge cases,
  not something to fold into a first CSV-import slice.
- **Keeping imported events DRAFT-first like manual creation**: rejected — see
  decision 6; the review step manual creation's DRAFT default protects against
  doesn't apply the same way to a bulk import the org already vetted before
  uploading.
