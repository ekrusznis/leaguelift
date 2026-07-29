# ADR-020: Phase 7 authorization model decisions

## Status
Accepted

## Context

DESIGN-DOC.md section 4.2 specifies a capability-based authorization model — contexts
(PERSONAL, ATHLETE, HOUSEHOLD, TEAM, ORGANIZATION, TOURNAMENT, PLATFORM_ADMIN),
`resource.action` capability strings, resource-scoped role assignments with
inheritance, and deny-by-default — as a Design Target that was never built. The
current-state model (section 4.1) is simple per-organization `organization_membership`
role checks, called directly from application services. Section 10.1/10.2 similarly
describe the dashboard system's current state as a static visual preview with hardcoded
per-role JSX, though investigation at the start of this engagement found the actual
code considerably further along than that description: a real `dashboard` backend
module (Owner/Coach/Parent/Athlete services, one endpoint per card, real
`organization_membership`-based routing via `DashboardContextService`) already existed,
with several cards already real (Owner summary/team-performance/recent-activity, Parent
outstanding-balance/athletes/active-fundraisers, Coach team-page-status) and others
explicitly demo-flagged (`isDemoData`/`isRaisedDemoData`/etc.). This ADR treats that
document/code mismatch as staleness to fix, not a discovery requiring its own decision
record — DESIGN-DOC.md section 10.1 is corrected alongside this ADR.

Section 10.3 explicitly recommends building one dashboard end-to-end as a proof slice
before repeating for the others, to keep the authorization foundation's risk contained.
The founder was told this and explicitly chose to build all four target dashboards
(Coach, Athlete, Tournament, Platform Admin) in this engagement anyway, given three of
the four already had partial real backend wiring and the fourth (Platform Admin) was
blocked entirely on this phase's work (no account could ever satisfy
`CurrentUser.platformAdministrator`, which was hardcoded `false` everywhere). This is a
real, deliberate decision, not a default — see Consequences for the risk it accepts.

## Decision

**1. Additive, not a replacement.** A new `authorization` backend module
(`domain`/`persistence`/`application`/`web`) is built alongside the existing
`membership` module, not instead of it. `MembershipService`'s existing
`requireActiveMembership`/`requireManagerRole`/`requireOwnerRole` continue to protect
every endpoint that already used them — nothing about their behavior changed. The new
`AuthorizationService` treats `organization_membership` as one of its inputs (the
source of truth for the ORGANIZATION context) rather than duplicating or replacing it.

**2. Two new tables, both additive (migration V18):**
- `role_assignment` — resource-scoped role grants for the TEAM, TOURNAMENT, and
  PLATFORM contexts, plus a narrow PARTICIPANT context (an athlete's own controlled
  self-login link — see decision 6). Carries `organization_id` directly (denormalized
  from the resource) so every query is a single indexed lookup, not a join through
  `team`/`tournament`. `NULL` for both `organization_id` and `resource_id` only for
  PLATFORM grants, which aren't scoped to one organization.
- `guardian_relationship` — the real FK-backed link between an `app_user` and the
  `household_adult` they are, replacing `HouseholdRepository.findActiveAdultByEmail`'s
  interim email-matching heuristic as the *authoritative* source for the HOUSEHOLD
  context (the old heuristic is kept as a fallback — see decision 5, not migrated away
  from in this slice).

**3. `AuthorizationService` is the single entry point** for every new-model check:
`listContexts` (backs `GET /me/contexts`), `has*Capability`/`require*Capability` per
context type, and `listAccessibleTeamIds`/`listAccessibleTournamentIds` for scoping
list queries. Every method is deny-by-default: a missing grant is always "no". The one
documented exception, matching DESIGN-DOC.md section 4.2's own example, is
inheritance — an organization OWNER/ADMINISTRATOR automatically holds TEAM_MANAGER-tier
capability for every team, and TOURNAMENT_ADMINISTRATOR-tier for every tournament, in
their organization, without an explicit `role_assignment` row for each one. This
inheritance is one-directional only (org role -> resource capability); the "unless
explicitly restricted" override DESIGN-DOC.md section 4.2 mentions is **not** built —
there is no mechanism to carve out an exception for one team while keeping inheritance
for the rest. `CapabilityRegistry` is a pure, testable role -> capability-set mapping
object; `AuthorizationService` never encodes a capability grant itself, only asks the
registry.

**4. Capability catalog is a fixed, hand-maintained set** (`Capabilities` object,
mirrored as frontend string constants in `capabilityConstants.ts` — no shared-types
build pipeline exists yet, so the two must be kept in sync by hand). It covers what the
dashboards and grant endpoints in this slice actually need, not DESIGN-DOC.md section
4's full prose description of every role's future permissions. Extending it is
low-risk (add a constant, add it to the relevant registry function, add a test).

**5. `MembershipRole.TEAM_ADMINISTRATOR`/`TOURNAMENT_ADMINISTRATOR` now grant zero
organization-level capabilities in the new model.** Before this phase, an org member
with the `TEAM_ADMINISTRATOR` role got *implicit* access to every team in the
organization (`CoachDashboardService` called `teamRepository.findAll(organizationId)`
directly) — the exact gap `db/seed/V9000`'s own comment flagged: "not actually scoped
to just Varsity Soccer." Real team/tournament access now requires an explicit
`role_assignment` row (or organization OWNER/ADMINISTRATOR inheritance). This is the
one call-site behavior change in this ADR beyond pure addition, and it is
intentional — closing this gap is most of what "wire the Coach dashboard to live data"
actually means. It does **not** touch `MembershipService`'s own methods or any other
module's authorization; only `CoachDashboardService` and the new
`TournamentDashboardService` consult `AuthorizationService` for team/tournament
scoping. `ParentDashboardService.requireAccess` is widened additively (a real
`guardian_relationship` grants access, checked before the existing email-match
fallback) rather than migrated.

**6. Athlete self-login formalizes an existing precedent, not a new product surface.**
DESIGN-DOC.md section 4.6 is explicit: participant/athlete login is a Design Target
gated on an unbuilt under-13 consent/privacy workflow, and `AthleteDashboardService`'s
own class doc already described its one seeded account
(`db/seed/V9000`, `maya.johnson@example.com`) as "a controlled test account... not a
standard product account." This ADR does not open general athlete self-service
signup. It adds `role_assignment(context_type = PARTICIPANT, role = ATHLETE_SELF)` as
the mechanism that *formalizes* that existing precedent — a guardian-authorized,
explicitly-recorded link from one `app_user` to one `participant`
(`AuthorizationService.linkAthleteSelf`, requiring the granting user to already be a
recorded guardian of that participant's household, or an org manager) — with no REST
endpoint exposed for it in this slice (no consent-workflow UI exists to call it from).
`db/seed/V9001` links the existing seeded athlete account this way. Athlete self
capabilities (`athlete.*`) deliberately exclude anything fee/payment/credit/report
related, matching the Athlete Dashboard's "Never" list in DESIGN-DOC.md section 10.2.

**7. Platform Administrator is now real, and only real via an explicit grant.**
Before this phase, `CurrentUser.platformAdministrator` was hardcoded `false` in
`JwtCurrentUserConverter` — the field existed (and `MembershipService` already had a
synthetic-membership bypass for it) but no code path could ever set it `true`. It is
now resolved on every request from a `role_assignment(context_type = PLATFORM, role =
PLATFORM_ADMINISTRATOR)` row — looked up fresh per request, not embedded in the JWT, so
a revoked grant takes effect immediately without waiting for the token to expire. There
is no self-service or API path to grant platform admin — only direct database
seeding/migration, consistent with DESIGN-DOC.md section 7.2 ("platform access is a
separate permission, never inferred from email or frontend state") and there being no
existing platform-admin management UI to build a grant flow into.

**8. Dashboard routing is widened, not redesigned.** `DashboardContextService` now also
consults `role_assignment`/`guardian_relationship` so a user whose *only* access is a
team/tournament/platform/guardian/athlete grant (no `organization_membership` row at
all) still routes to the correct dashboard. `DashboardRole` gains `TOURNAMENT_ADMIN`
and `PLATFORM_ADMIN`. This remains *routing to one primary dashboard*, not the full
interactive context-switching UI DESIGN-DOC.md section 4.2 describes as the long-term
target (a user holding multiple contexts — e.g. an org owner who is also a guardian —
still lands on exactly one dashboard, chosen by a fixed priority order: platform admin
> organization membership > team-only > tournament-only > guardian > athlete). `GET
/me/contexts` does return every context the user holds (so `useContexts` has complete
data for widget/nav gating within whichever dashboard they land on), but there is no
UI control to actually switch which dashboard is active mid-session — `DashboardShell`'s
context switcher remains the same static, inert element it was before this phase.

**9. Nav/widget registries are applied where this phase touches nav, not
repo-wide.** `frontend/src/dashboard/registry/navRegistry.tsx` is a data-driven
registry (`contextTypes` + `requiredCapabilities` per item) that replaces the Coach
dashboard's hardcoded `NAV_ITEMS` array (including the capability-gated Fees/Members/
Settings items DESIGN-DOC.md section 10.2 calls for) and backs the two brand-new
dashboards (Tournament, Platform Admin). Owner/Parent/Athlete's existing static
`NAV_ITEMS` arrays are **not** migrated to the registry in this slice — they were not
part of this engagement's dashboard-wiring scope (only Coach/Athlete/Tournament/
Platform Admin were), and touching them isn't necessary for the registry pattern itself
to be demonstrated and real.

**10. Tournament and Platform Admin dashboards are deliberately minimal.** Tournament
ships two cards (tournament identity, public-page status) — both entirely real. The
full nav DESIGN-DOC.md section 10.2 describes (Participating Teams, Divisions, Apparel,
Fundraising, Sponsors, Orders, Reports) depends on domain concepts that don't exist yet
(`tournament_team` remains design-target-only per section 8.3) and is left out entirely
rather than backed by invented/demo data. Platform Admin ships organizations/users
counts, an organization list, and webhook/outbox health counts — the subset of section
10.2's nav list (Organizations, Users, Integrations) the current schema genuinely
supports; Pilot Applications, Subscriptions, Payments, Payouts, Orders, Audit, Feature
Flags, and Support have no backing aggregate query and are left out.

## Consequences

**What was migrated to the new model:** Coach dashboard team scoping (the headline
gap this phase closes), Athlete dashboard (real participant/guardian/team data via the
self-link), Parent dashboard authorization (additive `guardian_relationship` check),
dashboard routing (`DashboardContextService`), and `platformAdministrator` resolution
(`JwtCurrentUserConverter`).

**What was *not* migrated, and remains on the pre-Phase-7 model:** every other
application service that calls `MembershipService.requireActiveMembership`/
`requireManagerRole`/`requireOwnerRole` directly (organizations, teams, tournaments,
households, participants, fees, fundraising, media, payouts, store, orders,
sponsorship — i.e. nearly the entire existing API surface) is unchanged. This was a
deliberate scope decision, not an oversight: DESIGN-DOC.md section 20.2 and this
engagement's brief both call for one vertical slice at a time and warn against a
big-bang authorization rewrite. A future phase should decide whether/how far to extend
`AuthorizationService`-based checks into those modules — likely starting with `team`/
`tournament` write endpoints (create/update/archive), which today still allow any org
manager to act on any team regardless of the new TEAM-context model, an inconsistency
this ADR accepts for now rather than resolves.

**Household authorization has two parallel mechanisms.** `ParentDashboardService` and
`AuthorizationService.hasHouseholdCapability` both check `guardian_relationship` first,
then fall back to the pre-existing email-match heuristic — but they are two separate
code paths (not one shared call), so a future change to one's fallback logic could
silently diverge from the other. Unifying them was judged lower priority than shipping
the real `guardian_relationship` check at all within this engagement's budget.

**No restriction-override mechanism.** Organization OWNER/ADMINISTRATOR inheritance for
teams/tournaments is all-or-nothing per organization; there is no way to grant an owner
access to every team except one. If a real product need for this emerges, it requires a
new `role_assignment` status (e.g. `DENIED`) and an explicit precedence rule between
inheritance and an explicit deny — not built.

**No context-switching UI.** `GET /me/contexts` is real and complete, but nothing in
the frontend lets a user who holds multiple contexts move between the dashboards for
each one within a session; they always land on the one primary dashboard `/me/
dashboard-context`'s fixed priority order picks. A user acting in multiple capacities
(e.g. a team coach who is also a guardian) must currently rely on whichever dashboard
they're routed to exposing what they need, or (for org staff) the synthetic
"any active member can view any household" bypass already in `ParentDashboardService`.

**Team-role and tournament-role grant/revoke has a backend API but no admin UI.**
`POST/DELETE /organizations/{id}/teams/{teamId}/role-assignments` (and the tournament
equivalent) exist, are tested, and are documented in `docs/openapi.yaml`, but there is
no frontend screen to call them — an org admin cannot yet assign a coach to a team
through the product UI, only via the API directly or `db/seed`. Building that UI was
judged out of scope for "wire dashboards to live data" and is a natural next slice.

**No PLATFORM or PARTICIPANT grant endpoint exists**, by design (see decisions 6/7) —
both require a workflow (consent UI, platform-admin-management UI) this engagement did
not build.

**Tournament and Platform Admin dashboards are intentionally thin** (see decision 10);
several nav items DESIGN-DOC.md section 10.2 lists for each have no backing data and
are simply not present, rather than shown with placeholder/demo content.

## Alternatives Considered

**Big-bang migration of every `MembershipService` call site to `AuthorizationService`
in this same pass.** Rejected: DESIGN-DOC.md section 20.2 explicitly warns against this
kind of scope expansion, and the brief called for the shared foundation to be "rock
solid" precisely because four dashboards would depend on it at once — spending the
engagement's budget re-touching every existing endpoint would have traded a tested,
narrow foundation for a wide, undertested one.

**Storing capability sets in the database** (a `capability` table, or per-role
capability rows editable by an admin) instead of a hand-maintained Kotlin registry.
Rejected for this phase: no product requirement exists yet for an organization to
customize its own role definitions, and a code-based registry is trivially unit-tested
and reviewed as a diff, unlike a runtime-editable table. Revisit if/when
per-organization custom roles become a real requirement.

**Building the full interactive context-switcher UI in this pass**, since `/me/
contexts` already returns everything it would need. Rejected on time/scope grounds — it
was not the headline of "wire the four dashboards to live data" and risked leaving
several things half-built; a fixed-priority single-dashboard landing was judged the
smaller, still-complete workflow per DESIGN-DOC.md section 20.6's stated precedence
("when completeness conflicts with a testable pilot workflow, choose the smallest
complete workflow").

**Opening general athlete self-service login** instead of the guardian-authorized
self-link. Rejected outright — this would violate DESIGN-DOC.md's youth-data boundary
(section 1) and section 4.6's explicit "not built yet" status pending a real consent/
privacy design, neither of which this engagement was asked to design.
