# ADR-021: Phase 7 completion decisions

## Status
Accepted

## Context

ADR-020 shipped Phase 7's authorization foundation and four dashboards
(2026-07-29), but left a documented list of gaps as deliberate scope cuts, not
oversights: nav/widget registries only covered Coach/Tournament/Platform Admin
(not Owner/Parent/Athlete, and no widget registry existed at all); team/tournament
role-assignment grant/revoke was API-only, no admin UI; a coach with multiple teams
had no way to pick which one's cards to view; Documents, Activity Feed, and Global
Search (§13 Platform UX Services) were deferred entirely, lowest priority within
that engagement's budget; and most `MembershipService`-protected endpoints outside
the four dashboards were left unmigrated to the new capability model, per ADR-020's
explicit warning against a big-bang migration.

Later the same day, the founder asked to close out this remaining list — "fully
complete phase 7" — and worked through several scope decisions live as the gaps
became concrete:

- Confirmed the ADR-020 leftovers (registries, admin UI, team selector, Documents,
  Activity Feed, Search) as in scope.
- Asked for Platform Admin's Orders/Payments/Payouts/Audit nav items to get real
  data where a backing table already exists, rather than staying stubs.
- Initially asked for the full `MembershipService` → `AuthorizationService`
  migration to be included — the option ADR-020 explicitly recommended against —
  then reversed that decision partway through the session ("lets remove the
  migrations to a later phase, not entirely needed here") once the additive work
  was mostly done.
- Stated that no interactive context-switching UI is needed: a user's dashboard is
  determined entirely by their role, and role changes are an owner action, not a
  per-session user action. This was said *after* a context-switcher had already
  been partially built in this same session, in response to the "fully complete
  phase 7" framing (§13/ADR-020 lists context-switching as a stated design target).
  The founder also flagged, separately, that a user can hold more than one role at
  once (e.g. a parent who is also a coach) as a real case the "no switcher" answer
  doesn't fully cover, and asked for that to be tracked as a future-phase refactor
  rather than resolved now — confirming the interim answer is today's existing
  fixed-priority routing (`DashboardContextService`: platform admin > organization
  membership > team-only > tournament-only > guardian > athlete).
- Requested a demo-data audit alongside real seed data broad enough to verify
  dashboard calculations, not just render without erroring.
- Added a new roadmap item (a platform-admin data-management console) explicitly
  scoped to *after* Phase 12/testing-QA, not part of this pass.

## Decision

**1. Nav and widget registries now cover all six dashboards.**
`frontend/src/dashboard/registry/navRegistry.tsx` gained Owner/Parent/Athlete
entries (unconditional — no new capability gating, preserving each dashboard's
pre-migration nav exactly). A new `frontend/src/dashboard/registry/widgetRegistry.tsx`
gives every dashboard's card list the same registry-driven shape nav already had;
today every entry is unconditional (mirrors current behavior 1:1) but the
mechanism now exists for a future capability-gated widget to be a one-line
addition instead of new plumbing.

**2. Team/tournament role-assignment grant/revoke has a real admin UI.**
An expandable "Manage access" panel on the Teams/Tournaments lists
(`OrganizationDetailPage`) lists current grants and lets an org manager pick from
existing org members (a new `GET /organizations/{id}/members` email/display-name
enrichment, and a new `GET .../role-assignments` list endpoint, both added to
support this) and grant/revoke a role. Still constrained to existing org members —
there is no user-search-and-invite flow bundled in.

**3. Coach team selector is built.** A coach with multiple assigned teams gets a
header dropdown (populated from `GET .../coach/teams`, already real) that drives
`teamId` query params newly added to the Team Page Status and Fundraising
Progress endpoints; the backend resolves and validates the selection against the
caller's actual accessible teams (rejecting an unrelated team with the same
`TEAM_ACCESS_DENIED` error the rest of `CoachDashboardService` already uses).
Defaults to the alphabetically-first accessible team, matching the pre-selector
behavior exactly when no explicit selection is made.

**4. Documents is built** (migration V19), generalizing the media pipeline
(§11.3) rather than building a parallel storage system: `media_asset` gained a
`DOCUMENT` usage slot (PDF only, via a new magic-byte check) and
`media_assignment` gained a `HOUSEHOLD` entity type. The existing "at most one
active assignment per entity+slot" unique index does not fit a document *list*,
so it was narrowed to exclude the `DOCUMENT` slot, and a new `DocumentService`
inserts directly through `MediaAssignmentRepository` rather than through
`MediaAssignmentService.assign()`'s single-slot retire-and-replace orchestration
(which would have silently archived a household's previous document every time a
new one was added). A new `document_acknowledgment` table records which guardian
(`household_adult`, the same authoritative-person concept `guardian_relationship`
already uses) has acknowledged/signed which document and when — idempotent,
one row per document per guardian. An org admin can also broadcast one document
to every household in the organization at once (e.g. a season waiver), which
creates one assignment per household sharing the same underlying asset.
Uploading a document is still org-manager-only, same as every other media
upload — a guardian cannot self-upload a document to their own household in this
slice; a documented scope cut, not an oversight.

**5. Cross-org Activity Feed is built** (`GET /me/activity`), deliberately
reusing the existing `audit_event` table/`AuditService` rather than a new event
log — every write already calls `AuditService.record`, so that table was already
the authoritative activity history. Scoped to every organization the caller
holds an active membership in, or platform-wide with no organization filter for
a platform administrator. Surfaced via the dashboard shell's notification bell,
previously decorative.

**6. Global Search is built**, scoped deliberately narrower than §13's literal
"organizations/teams/participants/households" for privacy reasons: an org member
can search teams/participants/households *within their own organization*
(`GET /organizations/{id}/search`), and a platform administrator can search
organizations platform-wide (`GET /platform/search`). It is **not** offered on
the Parent/Coach/Athlete dashboards — an org-wide household search would let a
guardian discover other families' households, crossing the household-privacy
boundary §1/§10.2 already draws ("cannot view unrelated households"). The header
search bar on those three dashboards remains the pre-existing decorative
placeholder.

**7. Platform Admin dashboard's Orders/Payments/Payouts/Audit are real.**
Orders is confirmed/pending/refunded counts from `"order"`, no organization
filter. Payments sums `ledger_entry` platform-wide by type/direction (gross
processed = `GROSS_SALE` + `CONTRIBUTION` credits; platform fees =
`RALLY26_PLATFORM_FEE` debits; refunded = `REFUND` debits). Payouts counts
organizations with `organization_payout_account.payouts_enabled = true` and sums
`TRANSFER` debits. Audit is **not** a new endpoint — it reuses `GET /me/activity`
from decision 5, which is already platform-wide for a platform administrator.
Pilot Applications, Subscriptions, Feature Flags, and Support remain out — no
backing aggregate query exists for any of them, and inventing one was judged out
of scope (see decision 10 below for where a fuller version of this dashboard is
tracked).

**8. Demo-data audit.** Every `isDemoData`/`is*DemoData` flag across all six
dashboard services was reviewed. One real bug was found and fixed: Owner
dashboard's `getFinancialOverview` had `isFundraisingDemoData = false` (asserting
the whole response was real) while `apparelSalesMinor`/`pendingPayoutMinor` were
still hardcoded literals — a flag/data mismatch, not merely undone work. Both are
now real (`ledger_entry` GROSS_SALE sum; `PayoutAccountService.getPayoutSummary`'s
net-available). `getReportsSnapshot`'s three dollar values are now the same real
queries; `trendPercent` is pinned to `0.0` with `isDemoData = false` on the
metric — no historical/time-series snapshot mechanism exists to compute a real
period-over-period change, and fabricating one would violate the same
truthfulness standard §12.2 states for the marketing site, so it reads as "no
measured change" rather than asserting a specific number. Every other demo flag
(Coach schedule/announcements/required-actions/attendance, Owner attention-
required/upcoming-events/onboarding-progress, Parent family-credits/schedule/
required-actions/organization-updates, Athlete's entirely-demo dataset) was
confirmed to still genuinely lack a backing model and was left as-is, accurately
flagged.

**9. Seed data breadth.** A new `db/seed/V9002__dev_seed_qa_breadth_fixtures.sql`
adds a second team and household to the existing Riverside organization, fee
assignments in every status (OPEN/PARTIALLY_PAID/PAID) with known-correct roll-up
totals, a published campaign with a confirmed contribution and matching
`ledger_entry` rows (CONTRIBUTION credit / RALLY26_PLATFORM_FEE debit /
ORGANIZATION_EARNING credit, at the real 5% default rate), and a second
organization entirely (Lakeside Sports Alliance) so platform-wide aggregates sum
across more than one organization and org-scoped search can be verified not to
leak across organizations. Verified by temporarily pointing a Testcontainers-backed
integration test at `classpath:db/migration,classpath:db/seed` and asserting the
migrations apply cleanly with the expected fixture counts, then removing that test
— it was a one-time verification, not a permanent addition, because the shared
Testcontainers singleton pattern every integration test in this suite uses would
otherwise let this seed data leak into unrelated tests' assertions. Deliberately
not extended to store/product/order or sponsorship fixtures — those chains have
enough schema surface of their own (product variants, Printify cost snapshots,
checkout sessions) to deserve their own follow-up pass rather than guessed values.

**10. Context-switching UI: built, then reverted, per an explicit founder
decision that this is not a gap to close.** Earlier in this same session, a
session-scoped context switcher was built end to end (a `DashboardSwitcherProvider`
React context, a functional dropdown replacing the shell's static context badge,
and `DashboardPage` routing logic that let a user holding multiple contexts
render a different dashboard than `/me/dashboard-context`'s fixed-priority
default). The founder then stated: a user has one role, that role determines
their dashboard, and the only way that changes is an organization owner
reassigning the role — so no switcher is needed. All of that code was reverted
(`DashboardPage.tsx`, `DashboardShell.tsx` back to their pre-switcher state;
`frontend/src/dashboard/DashboardSwitcher.tsx` deleted). This is now a permanent
product decision, not an unbuilt Design Target — §10.1/§4.2's "context-switching
UI" language is corrected accordingly. The founder separately confirmed the
interim answer for a user who holds more than one context simultaneously (e.g. a
parent who is also a coach) is today's existing fixed-priority routing, and asked
for whether/how to expose the *other* role(s) such a user holds to be scoped
fresh in a later phase — not assumed to mean "bring back the switcher."

**11. The full `MembershipService` → `AuthorizationService` migration remains
deferred, unchanged from ADR-020.** The founder asked for it, then reversed that
ask before any of it was implemented. ADR-020's dual-model state (the new
`AuthorizationService` covers only the four Phase 7 dashboards and the
team/tournament role-assignment grant endpoints; every other endpoint — 
organizations, teams, tournaments, households, participants, fees, fundraising,
media, payouts, store, orders, sponsorship — still calls `MembershipService`
directly) is the intended current state, not a gap this pass left behind. No new
ADR content was needed for this beyond noting the reversal here.

**12. New roadmap item: a platform-admin data-management console (Phase 13).**
The founder requested, separately, a console for Rally26 staff to view **and
edit** data for any user/organization/team/household platform-wide — broader
than decision 7's read-only aggregates, and explicitly placed after Phase 12
(Production Readiness Review / testing-QA), not before. Added to §14.1 as Phase
13. Flagged there as high-blast-radius (edit-any-org/user access) needing real
access-control and audit design when scoped, not just a CRUD UI bolted onto every
table — consistent with §7.2's existing platform-admin discipline ("platform
access is a separate permission, never inferred from email or frontend state").

## Consequences

**Phase 7 is now complete** against DESIGN-DOC.md's own definition of it
(§14.1's Phase 7 row) — every item ADR-020 listed as deferred is either built
(registries, admin UI, team selector, Documents, Activity Feed, Search, Platform
Admin financials) or is now a confirmed permanent decision rather than an open
gap (context-switching UI, the full authorization migration).

**The dual-authorization-model risk ADR-020 flagged is unchanged and still
real.** Nearly the entire API surface outside the four Phase 7 dashboards still
authorizes through `MembershipService`, not `AuthorizationService`. This was a
deliberate, twice-confirmed decision (requested, then explicitly reversed) in
this session, not an oversight — but it means the inconsistency ADR-020 already
named (e.g. any org manager can still create/update/archive any team regardless
of the new TEAM-context model) persists into whatever phase comes next.

**Fixed-priority dashboard routing is now a permanent constraint, not a
temporary one.** Any future feature that assumes a user might see more than one
dashboard's worth of context at once (without an org owner changing their role)
does not exist today and would need new product/engineering work — this is
explicitly tracked as a future-phase item (decision 10), not silently blocked.

**Global Search's asymmetry (Owner/Platform Admin get it, Parent/Coach/Athlete
don't) is intentional** and should not be "fixed" by a future engineer assuming
it was simply not finished — see decision 6's privacy reasoning before extending
it to those dashboards.

**Seed data coverage has a known gap**: store/product/order and sponsorship
flows still only have the original V9000/V9001 baseline (none), not the breadth
decision 9 added for fees/fundraising/ledger. A future QA pass wanting to verify
apparel-commerce or sponsorship calculations will need to extend `db/seed`
further.

## Alternatives Considered

**Keeping the context-switcher instead of reverting it**, since it was already
built and working. Rejected: the founder's stated reasoning (role determines
dashboard, changed only by an owner) is a real product simplification, and
shipping an interactive control at odds with that model would invite users to
expect a workflow the product doesn't actually support (switching roles
themselves). Deleting the dead code outright was preferred over leaving it
disabled/unreachable, per this codebase's general preference for not carrying
unused code paths.

**Going ahead with the full `MembershipService` migration anyway**, since it had
already been decided once. Rejected once the founder reversed the decision
explicitly and before implementation started — no sunk cost existed to weigh
against ADR-020's original, still-valid reasoning against a big-bang migration.

**Making Platform Admin's Orders/Payments/Payouts full list/detail views**
instead of aggregate stat tiles. Rejected for this pass: the existing Platform
Admin dashboard's established style (§10.2) is aggregate counts/sums in
`StatTile` grids, not data tables; a full list view is closer to decision 12's
console and better scoped there once its access-control model is designed,
rather than smuggled in here as a "just a table" feature.
