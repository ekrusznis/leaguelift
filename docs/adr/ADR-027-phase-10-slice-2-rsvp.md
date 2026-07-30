# ADR-027: Phase 10 Slice 2 — RSVP

## Status
Accepted

## Context

ADR-026 (slice 1) built the event data model and staff CRUD, and recorded
three founder decisions covering all of Phase 10: RSVP ships as a simple v1
(MAYBE enabled, no deadline/lock/staff-override); RSVP aggregate counts are
shown to athletes/guardians, not staff-only; no per-organization feature
toggle. This slice implements RSVP itself against those decisions.

The key design risk flagged in ADR-026 (decision 4) was confirmed before
writing any code: `AuthorizationService.hasHouseholdCapability`'s "any
active org member" branch returns `true` before even checking which
capability was requested — appropriate for household financial *viewing*
(`FeeService.listForHousehold`'s existing bar), but wrong for RSVP
*submission*, which effectively impersonates a specific guardian. An org
staff member submitting an RSVP "as" a family they merely administer would
misrepresent who actually responded.

## Decision

**1. `event_rsvp` (V23) is one row per `(event_id, participant_id)`, upserted
in place — not an append-only history table.** DESIGN-DOC.md section
14.1A's "a later response replaces the effective response while preserving
history or audit metadata" is satisfied by `EventRsvpRepository.upsert`'s
`ON CONFLICT (event_id, participant_id) DO UPDATE` (preserving `created_at`,
moving `updated_at`) plus an `event.rsvp_changed` audit event recording the
previous and new response as metadata. A second table tracking every past
response was rejected as unneeded — see Alternatives.

**2. RSVP submission requires a real self/guardian/staff relationship,
resolved directly — never through `hasHouseholdCapability`.**
`EventRsvpService.resolveSource` checks, in order: (a)
`AuthorizationService.hasParticipantCapability(currentUser, participantId,
EVENT_RSVP_SELF)` — the athlete's own `role_assignment(PARTICIPANT,
ATHLETE_SELF)` link — source `SELF`; (b)
`GuardianRelationshipRepository.findActiveForHousehold(currentUser.userId,
participant.householdId)` — a real guardian relationship — source
`GUARDIAN`; (c) `event.update` capability at the event's team/tournament
scope (a coach/tournament admin recording an RSVP on a family's behalf,
e.g. for a participant without their own account) — source `ADMIN`. No
path reaches `hasHouseholdCapability`.

**3. An RSVP requires the event to have an owning team, and the participant
to be on that team's active roster.** `event.teamId == null` (an org-wide
or team-less tournament event) throws `ValidationException` — there's no
roster to check eligibility against. This is a real v1 boundary, not an
oversight: a tournament-level event with no specific team (e.g. a director's
meeting) genuinely has no defined set of eligible RSVP-ers yet.

**4. Read access has two tiers, not one.** `EventRsvpService.getRsvps`
returns the full individual-response list only to a caller with
`event.rsvp.read_team` at the event's team/tournament scope (staff);
a guardian/athlete with any participant on the event's team gets
[`RsvpSummary`] counts computed from the same underlying data but an empty
`responses` list — satisfying the founder's decision that aggregate counts
are visible to families while individual responses (who specifically said
what) stay staff-only. `RsvpSummary.of` computes counts in Kotlin from the
already-fetched list rather than a separate SQL `GROUP BY` — a team's
roster is small (tens, not thousands), so this avoids a second query for no
real benefit.

**5. `EventService` gains `listForHousehold`/`listForParticipant` — the
"combined schedule" reads — using `hasHouseholdCapability` (the broader
check), not the stricter guardian-relationship-only path.** Unlike RSVP
*submission*, viewing a schedule carries no impersonation risk — an org
staff member seeing a household's schedule is the same "any active member
can view" posture `FeeService.listForHousehold` already has. An athlete's
own participant-schedule view additionally accepts
`hasParticipantCapability(..., ATHLETE_SCHEDULE_VIEW)` — reusing the
existing Phase 7 capability built for exactly this future use, rather than
adding a redundant `EVENT_READ` grant to `athleteSelfCapabilities()`.

**6. `AthleteDashboardService.getOverview`/`getWeekEvents` are wired to real
data, closing a gap flagged in their own doc comments since Phase 7.** Both
previously returned hardcoded empty/null specifically because "no
events/schedule model exists yet (Phase 10 — Not started)" — now one does.
Both reuse `EventService.listForParticipant` (the athlete's own self-link
already satisfies that method's authorization, so this isn't a new
authorization path, just a new caller) and a shared `toScheduleItem`/
`upcomingEvents` helper filtering out `CANCELLED`/`COMPLETED` events and
sorting by `startAt` (nulls — TBD events — sort first, since there's no way
to know their real position in time). Orders remain honestly empty; that
gap is unrelated to events (`order` still has no participant association).

## Consequences

- Zero new `AuthorizationService` methods were needed for RSVP submission
  either — `hasParticipantCapability`/`findActiveForHousehold`/
  `hasTeamCapability`/`hasTournamentCapability` (all pre-existing) were
  sufficient composed together in `EventRsvpService`.
- RSVP is unavailable for org-wide events and team-less tournament events
  (decision 3) — acceptable for this slice; if a real organization needs
  RSVP on such an event, that's a future decision about what "eligible
  participant" even means without a roster, not a bug to silently patch
  around.
- `EventRsvpsResult.responses` being empty is the *only* signal a
  guardian/athlete-only caller gets that they're not seeing the full list —
  the API doesn't distinguish "empty because summary-only" from "empty
  because nobody's responded yet" at the JSON level beyond the summary
  counts themselves being non-zero. Acceptable for a v1 API; a future
  `canViewIndividualResponses: Boolean` field could disambiguate if a real
  frontend need arises.
- `AthleteDashboardService` now depends on `EventService`, its first
  dependency on the `event` module — a reasonable, expected coupling given
  the dashboard's own doc comment has named this exact gap since Phase 7.
- Calendar/ICS output, maps/directions, and notification-event emission
  (`event.rsvp_changed` as a real outbox event, not just an audit row)
  remain slices 3 and 4 — RSVP changes are audited but don't yet notify
  anyone via email/SMS.

## Alternatives Considered

- **An append-only `event_rsvp_history` table alongside a "current" row**:
  rejected — DESIGN-DOC.md's own phrasing ("preserving history **or** audit
  metadata," not "and") allows the simpler reading, and the audit_event
  table already exists specifically for this purpose; a second history
  table would duplicate what audit_event already does for every other
  mutable entity in this codebase.
- **Gating RSVP submission through `hasHouseholdCapability` after all, with
  a narrower capability**: rejected — even a capability-scoped check
  through that method still hits the "any active org member returns true
  immediately" branch before the capability is ever consulted; the method
  would need its own restructuring to support a stricter mode, which is
  more invasive than just calling the guardian-relationship repository
  directly in `EventRsvpService`.
- **Allowing RSVP on team-less events by checking organization-wide
  participant membership instead of a specific roster**: rejected — that
  would let any participant in the organization RSVP to any org-wide event,
  which isn't "eligibility," just an absence of a check; simply disallowing
  RSVP on such events is more honest than fabricating a permissive default.
- **A separate `GET .../rsvp-summary` endpoint distinct from `GET
  .../rsvps`**: rejected — one endpoint returning a shape that varies by
  caller (full list vs. summary-only) matches how `EventRsvpsResult` is
  already structured, and avoids two endpoints whose only difference is
  authorization tier.
