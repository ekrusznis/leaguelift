# ADR-029: Phase 10 Slice 4 — Notification Wiring

## Status
Accepted

## Context

Slices 1-3 (ADR-026/027/028) built the event data model, staff CRUD, RSVP, and the
calendar/maps stubs. DESIGN-DOC.md section 14.1A's last unbuilt piece is: "emit
`event.created`, `event.time_changed`, `event.location_changed`, `event.area_assigned`,
`event.arrival_time_changed`, `event.postponed`, `event.cancelled`,
`tournament.event_added`, `tournament.event_updated`, and `event.rsvp_changed`. Notify
only for meaningful recipient-visible changes... Delivery uses in-app state first and
Phase 8 email/SMS infrastructure when available." This slice wires all ten event types
into Phase 8's outbox worker (ADR-022).

Two scope questions were resolved with the founder before implementation:

1. **In-app delivery**: no dedicated per-user notification/inbox table exists in this
   codebase. Rather than build one speculatively, the existing cross-org Activity Feed
   (`GET /me/activity`, reuses `audit_event`, Phase 7 completion pass) is treated as
   sufficient "in-app state" — every notification-worthy action here already calls
   `AuditService.record`, so it already surfaces there. This slice's new work is the
   email/SMS half only.
2. **RSVP-change recipients**: unlike every other notification in this slice (family-
   facing), `event.rsvp_changed` notifies **team staff**, not the submitting family —
   they already know what they just did. This is new territory for this codebase (no
   prior handler has ever resolved a *staff* recipient list; every existing handler
   resolves a household contact).

## Decision

**1. `EventService` writes one outbox event per changed dimension, resolved directly
against the event's before/after snapshot — no separate "meaningful change" detector
class.** `update()`'s existing COALESCE-style repository call already means a non-null
parameter is exactly "the caller intends to change this field"; combined with a
before/after diff (`before.startAt != after.startAt`, etc.), a genuinely no-op "re-set to
the same value" edit still fires no notification. A single `update()` call touching both
`startAt` and `venueName` writes two distinct, separately-worded outbox events
(`event.time_changed` and `event.location_changed`), not one generic "something changed."

**2. Notifications never fire for a still-`DRAFT` event.** Every new `event` row starts
`DRAFT` (`EventRepository.insert` hardcodes it) and is invisible to anyone but staff
until `publish()`. `create()` therefore never notifies; `publish()` is the moment
`event.created` (or `tournament.event_added`, see decision 4) actually fires, since
that's the real "this became recipient-visible" transition. `update()`/`cancel()`/
`postpone()` all guard on the event's status *before* the action — editing, cancelling,
or postponing a DRAFT event that families never saw fires no notification, only the
existing staff-facing audit event.

**3. Family recipients are resolved from `Event.teamId`'s roster only — never
`opponentTeamId`, and never for an org-wide event with no team.** This exactly mirrors
RSVP's existing scope (`EventRsvpService.submit` already requires the event to have an
owning team and the participant to be on its roster) — resolving the *opponent* team's
families too would double the resolution work for a notification type nothing in section
14.1A asks for, and an org-wide event has no roster at all to resolve. A new
`HouseholdRepository.findActiveForTeam(teamId, organizationId)` joins
`household -> participant -> participant_team`; the household's existing
`email_reminders_opt_out`/`sms_reminders_opt_in` flags (ADR-023/ADR-024) are applied by
the caller before the recipient ever reaches the outbox payload — a schedule-change
alert is judged a recurring-style notice, not a one-time transactional confirmation like
an order receipt, so it respects the same opt-out/opt-in a fee-payment reminder does.

**4. A tournament child event (`Event.tournamentId` set) collapses every changed
dimension into one `tournament.event_updated` notification instead of the granular
`event.time_changed`/`event.location_changed`/etc. set, and uses `tournament.event_added`
instead of `event.created` at publish.** This follows section 14.1A's own two-type
split, and sidesteps a real schema gap: this codebase has no `tournament_team` roster
table (ADR-026 already flagged this as a non-blocker for the event model itself), so a
tournament-wide event with no `teamId` has no family roster to resolve at all — the
outbox event still gets written (for the audit trail / a future consumer), but its
recipient list is honestly empty rather than faked. A tournament child event that *does*
carry a `teamId` (a specific team's pool/bracket game) still resolves real family
recipients through that team's roster exactly like decision 3 — the tournament/team
fields aren't mutually exclusive in the schema, only in which authorization path
`requireManageAccess` checks.

**5. `event.postponed`/`event.cancelled` are universal — never a `tournament.*`
variant** (section 14.1A lists them once, not duplicated per tournament), consistent
with how `EventService.cancel`/`postpone` already treat team- and tournament-owned
events identically apart from which capability is checked.

**6. `event.rsvp_changed` notifies team staff via a new
`AuthorizationService.listTeamStaffUserIds(organizationId, teamId, capability)`** —
explicit `role_assignment` grants at TEAM scope filtered by
`CapabilityRegistry.teamCapabilities`, unioned with every org OWNER/ADMINISTRATOR (a new
`MembershipRepository.listActiveManagers`, unpaginated, since resolving *who to notify*
is a different need than the existing paginated member-list endpoint) when
`TEAM_MANAGER`'s tier already includes the requested capability — the same inheritance
`hasTeamCapability` encodes, just inverted from "can this one user act" to "which users
can act." `EventRsvpService.submit` excludes the current submitter from that set so a
coach submitting an RSVP on a family's behalf doesn't get emailed their own action.
Email only, no SMS — an RSVP change wasn't judged SMS-worthy.

**7. Nine of the ten notification event types share one
`EventChangeNotificationHandler` class, registered as nine separate `@Bean`-produced
instances (`EventNotificationHandlerConfig`), rather than nine near-identical
`@Component` classes.** Every type in decision 1's list does the exact same thing — email
+ SMS every resolved recipient the same `changeSummary` string — so the difference
between them is entirely in the `eventType` string and the wording the writer embeds in
the payload, not in handling logic.
[`OutboxWorker`](../../backend/src/main/kotlin/com/leaguelift/outbox/application/OutboxWorker.kt)
only needs `List<OutboxEventHandler>` with distinct `eventType` values to build its
dispatch map — it doesn't care whether those come from nine `@Component` classes or one
class instantiated nine times via `@Bean` factory methods, so this is a pure
implementation-consolidation choice, not an architecture change. `event.rsvp_changed`
is the tenth type, with its own `EventRsvpChangeNotificationHandler` (different payload
shape — staff emails, not household contacts).

## Consequences

- No new database migration, no new capability, no new REST endpoint — this slice is
  pure outbox-producer/consumer wiring on top of what slices 1-3 already built.
- A tournament-wide event with no `teamId` gets an audit trail and an outbox row for
  `tournament.event_added`/`tournament.event_updated`, but no family ever receives an
  email or SMS about it — an honest limitation flowing from the same missing
  `tournament_team` roster ADR-026 already flagged, not a new gap this slice invented.
- "In-app state" for this slice is entirely the pre-existing Activity Feed. If a
  dedicated, markable-read, family-facing notification inbox is wanted later, it's a
  separate, larger feature — deliberately not built speculatively here.
- `resolveFamilyRecipients`/`findActiveForTeam` is a straightforward (not batched/
  cached) query per notification-worthy write — proportionate at this codebase's
  pre-pilot scale, consistent with its general "simplicity over premature optimization"
  posture elsewhere.

## Alternatives Considered

- **A single generic `OutboxEventHandler` per event type, one `@Component` class each
  (nine classes)**: rejected — see decision 7; the bodies would be identical apart from
  the `eventType` string, and the shared-class-with-nine-`@Bean`s approach keeps
  `OutboxWorker`'s "one handler per registered event type" contract intact without the
  duplication.
- **Resolving families for tournament-wide events via `opponentTeamId` or some other
  proxy roster**: rejected — there is no real tournament participant/team roster in this
  schema; inventing a proxy would be exactly the kind of fabricated workaround ADR-025/
  ADR-026 both already declined to do for the analogous "revenue by tournament" and
  "tournament team roster" gaps.
- **Treating event-change notifications as always-on transactional emails (ignoring
  `email_reminders_opt_out`)**, matching order-confirmation precedent: rejected — a
  schedule change is judged closer to a recurring reminder than a one-time payment
  receipt; respecting the same opt-out/opt-in fee reminders already use is the more
  conservative, less surprising default.
- **Notifying the submitting family (not staff) on `event.rsvp_changed`**: rejected per
  the founder's decision — the family already knows what they just submitted; team
  staff visibility into changing responses is the actual notification-worthy gap.
- **A new dedicated per-user notification inbox table**: rejected this slice — no
  acceptance criterion explicitly asks for it, the existing Activity Feed already
  satisfies "in-app state," and building one speculatively risks a schema that doesn't
  match whatever a real future requirement turns out to need.
