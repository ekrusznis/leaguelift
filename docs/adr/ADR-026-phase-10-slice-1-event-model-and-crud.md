# ADR-026: Phase 10 Slice 1 — Event Model and Staff CRUD

## Status
Accepted

## Context

DESIGN-DOC.md section 14.1A specifies Phase 10's event/RSVP/calendar/maps
design target in detail — event types, core fields, statuses, tournament
child events, an RSVP model, authorization capabilities, a source/import
seam, and calendar/maps stubs. Before implementing, three founder decisions
were confirmed (2026-07-30), and the existing schema/authorization
architecture was checked to ground the design in what's actually built.

**Founder decisions:**
1. RSVP ships as a simple v1: `MAYBE` is a real response option; no
   deadline enforcement, no locking after a deadline, and staff cannot
   override a family's own response this phase (resolves §19.3 #21).
2. RSVP aggregate counts (e.g. "12 attending, 2 maybe") are shown to
   athletes/guardians for their linked participant's team, not just staff
   (resolves §19.3 #25 in the more permissive direction).
3. No per-organization on/off toggle for the whole feature — an
   organization that never creates an event simply never uses it; no
   `organization.events_enabled`-style column or settings UI.

Decisions 1 and 2 apply to slice 2 (RSVP); this ADR's own scope is slice 1
(the event data model and staff-facing CRUD) but records all three since
they shaped the overall Phase 10 design, not just one slice.

**Schema check before designing:** no `tournament_team` join table exists
(tournaments have no formal participating-team roster) — this does **not**
block Phase 10, because section 14.1A's own "core event data" already
specifies an event has its own owning team(s) even under a tournament
parent; families are notified via an event's `team_id`, never via tournament
membership. `team.team_id`/`store.team_id` already exist and are exactly
what later reporting/notification code needs — no new team-relationship
schema work is needed for events.

## Decision

**1. `event` table (V22) collapses two DESIGN-DOC.md-listed fields into one.**
Section 14.1A's "core event data" prose lists `status`, `visibility`, and
`publication status` as three separate concerns. `status` already includes
`DRAFT` in its own enum (`DRAFT, TENTATIVE, SCHEDULED, DELAYED, POSTPONED,
CANCELLED, COMPLETED`) — a `DRAFT` event already isn't published. Adding a
fourth, separate `publication_status` column on top would be redundant with
`status` doing that job. The schema keeps `status` (lifecycle) and adds
`visibility` (`PUBLIC`/`ORGANIZATION`/`TEAM` — who's allowed to see it at
all, independent of lifecycle) as the only two gating columns. No stored
display-title column either — `displayTitle()` (`event/domain/Event.kt`)
computes DESIGN-DOC.md's example ("Eastside U12 Blue vs Northshore FC") on
read from team/opponent names, so a later team rename never goes stale;
`Event.title` is only the explicit override field ("State Cup Semifinal").

**2. Authorization reuses the existing team/tournament capability-inheritance
architecture unchanged — no new AuthorizationService methods needed.**
`hasTeamCapability`/`requireTeamCapability` and `hasTournamentCapability`/
`requireTournamentCapability` already resolve both explicit `role_assignment`
grants and org owner/admin inheritance purely by checking
`capability in CapabilityRegistry.teamCapabilities(role)` (ADR-020). Adding
the new `event.*`/`team.event.manage`/`tournament.event.manage` capability
constants to the existing `COACH_READ`/`TEAM_EDITOR`/`TEAM_MANAGER`/
`TOURNAMENT_VIEWER`/`TOURNAMENT_ADMINISTRATOR` capability sets in
`CapabilityRegistry` was the entire authorization change required —
`EventService` calls the exact same `requireTeamCapability`/
`requireTournamentCapability` methods every other team/tournament-scoped
feature already calls. `organization.event.manage` is added to
`organizationCapabilities(OWNER/ADMINISTRATOR)` for context-listing/
frontend-nav purposes, but — matching the fact that no generic
`requireOrganizationCapability` method exists anywhere in this codebase yet,
organization-level actions all still gate through `MembershipService`
directly — an org-wide event (no `team_id`, no `tournament_id`) is gated by
`membershipService.requireManagerRole`, not a capability string check.

**3. An event's scope (team/tournament/org-wide) is set at creation and not
reassignable via `update`.** `EventRepository.update`'s partial-update field
list deliberately excludes `team_id`/`tournament_id` — changing which
team/tournament owns an event isn't a v1 need, and omitting it avoids a
re-authorization edge case (what if the caller has manage-access to the old
team but not the new one?) this slice doesn't need to solve.

**4. `event.rsvp.guardian`/`event.read` are added to
`CapabilityRegistry.householdCapabilities()` for context-listing only — real
guardian-scoped enforcement will bypass `hasHouseholdCapability` entirely.**
`AuthorizationService.hasHouseholdCapability`'s existing "any active org
member" branch returns `true` before even checking which capability was
asked for — appropriate for household financial *viewing* (matches
`FeeService.listForHousehold`'s existing "any active member" bar), but far
too broad for a guardian-only action like submitting an RSVP as a parent
(an org staff member is not a guardian). Slice 2 will check
`guardianRelationshipRepository.findActiveForHousehold` directly for RSVP
submission, the same way `ParentDashboardService.requireHousehold` already
bypasses the generic household-capability check for guardian-scoped
dashboard data. This ADR only adds the constants; slice 2 does the real
wiring.

**5. Team/tournament-nested routes take `organizationId` as a query
parameter, not a path segment — a deliberate, documented deviation from
section 14.1A's "suggested" (not authoritative) API surface.** The doc
itself says "All routes remain capability- and relationship-scoped; OpenAPI
is authoritative" — every existing team/tournament lookup in this codebase
requires an organization id to scope the query (`TeamRepository.findById(id,
organizationId)`), so `GET /teams/{teamId}/events` needs one too; a query
parameter was chosen over restructuring the path
(`/organizations/{organizationId}/teams/{teamId}/events`) to match the
doc's literal suggested shape as closely as possible while still being
organization-scoped.

**6. Timezone is a required, validated IANA zone id — no default.**
`EventService` calls `ZoneId.of(timezone)` and throws `ValidationException`
on failure; DESIGN-DOC.md doesn't specify a default and organizations exist
across time zones, so guessing one (e.g. always defaulting to Eastern) would
be a real correctness bug for a West Coast organization, not a convenience.

## Consequences

- Zero migration or authorization-architecture changes were needed beyond
  adding capability constants — the capability-inheritance system built for
  Phase 7 (ADR-020) generalizes cleanly to a brand-new resource type.
- `event.rsvp.read_team` is granted to `TEAM_EDITOR` and up, and to
  `TOURNAMENT_ADMINISTRATOR` — slice 2 will decide exactly what "read team
  RSVPs" returns (individual responses for staff, aggregate counts also for
  athletes/guardians per founder decision 2 above); this slice only reserves
  the capability name.
- No RSVP, calendar/ICS, maps/directions, or notification-event code exists
  yet — `event.created`/`event.time_changed`/etc. are not emitted this
  slice; audit events (`event.created`, `event.updated`, `event.published`,
  `event.cancelled`, `event.postponed`) are recorded, satisfying acceptance
  criterion #11's audit-event half but not yet its "meaningful change event
  for future notification delivery" half (slice 4).
- `EventController` resolves `displayTitle` by calling
  `TeamRepository.findById` up to twice per event (owning team, opponent
  team) with no batching — acceptable for this slice's pagination sizes
  (default 20/page); revisit with a batched lookup if list endpoints are
  ever used at a much larger page size.
- Tournament child events with `TBD` start time/opponent/area are fully
  supported by the schema (every relevant column is nullable) but this
  slice doesn't add any special "TBD" UI/DTO field — a `null` `startAt`/
  `opponentTeamId`/`area` in the response already communicates that state.

## Alternatives Considered

- **A separate `publication_status` column, matching section 14.1A's prose
  literally**: rejected — see decision 1; `status`'s existing `DRAFT` value
  already does this job, and a second column tracking the same underlying
  concept would need its own state-transition rules with no real behavioral
  difference from checking `status != DRAFT`.
- **A new generic `requireOrganizationCapability` method on
  `AuthorizationService`, so `organization.event.manage` could be enforced
  the same way team/tournament capabilities are**: rejected as broader
  architecture work than this slice needs — every existing org-level action
  already gates through `MembershipService.requireManagerRole`, and
  introducing a second, parallel org-level enforcement path for events only
  would be an inconsistency, not a simplification.
- **Restructuring team/tournament-nested routes to include `organizationId`
  in the path** (`/organizations/{id}/teams/{teamId}/events`): rejected —
  see decision 5; a query parameter keeps the route shape closer to section
  14.1A's suggested surface while still being fully org-scoped.
- **A `tournament_team` join table, built now to give tournament child
  events a "real" participating-team roster**: rejected — see Context; no
  event actually needs it, since team ownership already comes from the
  event's own `team_id`/`opponent_team_id`, not tournament membership.
- **Making an event's team/tournament reassignable via `update`**: rejected
  as unneeded v1 scope — see decision 3.
