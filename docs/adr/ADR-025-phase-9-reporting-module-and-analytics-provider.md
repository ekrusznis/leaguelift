# ADR-025: Phase 9 — Reporting Module and AnalyticsProvider

## Status
Accepted

## Context

DESIGN-DOC.md section 13's Reporting catalog names org reports (revenue by
source/team/tournament/campaign, fee collections, outstanding fees, credits,
product performance, refunds, platform fees, earnings, payouts), household
reports (fees, payments, balance, credits, orders, contributions), and
platform reports (customers, active orgs, subscription revenue, GTV,
refund/dispute rates, feature adoption, retention, integration health) —
all "targeted for Phase 9." Section 17 also reserves an `AnalyticsProvider`
seam for usage insights, with no vendor chosen.

Before scoping this, existing dashboard code was audited to avoid rebuilding
what already exists: `OwnerDashboardService.getFinancialOverview`/
`getReportsSnapshot` and `PlatformAdminDashboardService`'s Orders/Payments/
Payouts summaries already compute several of these metrics as real,
point-in-time snapshots (Phase 7 completion pass, ADR-021). None of them
support a date range, a team/campaign breakdown, or export. That gap — not
the underlying data — is what a "dedicated reporting module" actually needs
to add.

The audit also surfaced two things that aren't reporting gaps at all:
`ParentDashboardService.getFamilyCredits`/`getRecentOrders` are still
hardcoded demo data because family credit rules were never built (DESIGN-DOC.md
section 19.3 open questions #6/#16/#17 remain unresolved) and orders/
contributions carry only a free-text supporter name/email, no household or
participant foreign key — there is no way to attribute an apparel purchase
or a contribution to a specific household today. A "household orders/
contributions/credits report" would require solving that attribution
problem first, which is a separate, older, still-open product decision, not
something this reporting slice can quietly solve as a side effect.

Two decisions were confirmed directly with the founder before implementation:

## Decision

**1. `AnalyticsProvider` ships as a logging-only stub this phase, no vendor
chosen.** Mirrors `EmailProvider`'s own history — `LoggingEmailProvider` was
the only implementation for two phases before Resend was picked (ADR-022).
`notification/AnalyticsProvider.kt` defines `track(AnalyticsEvent)`;
`notification/LoggingAnalyticsProvider.kt` is the only implementation, always
active (no `@ConditionalOnProperty` toggle — unlike `EmailProvider`/
`SmsProvider`, there's no second implementation to switch to yet, so a
toggle would be dead configuration). One real call site proves the seam
works end to end: `OrganizationService.create` tracks an
`organization_created` event (org creation is the most fundamental usage
signal available) — deliberately not a broad instrumentation sweep across
every service, since deciding *which* events matter for "usage insights" is
itself a product decision for whoever picks the eventual vendor, not
something to guess broadly now. `LoggingAnalyticsProvider` never logs
`properties` values wholesale, only the event name/IDs/property count — the
same "don't log potentially sensitive data by default" discipline
DESIGN-DOC.md section 18.2 already requires elsewhere.

**2. Household reports are scoped to fees/payments/balance only — credits,
orders, and contributions stay out.** Per Context: credits don't exist as a
real feature yet, and orders/contributions have no household attribution
FK. `ReportingService.getHouseholdFeeReport` (org-member-gated, matching
`FeeService.listForHousehold`'s existing bar — this is an org-facing report
on a specific household, not the household's own self-service Parent
dashboard, which stays untouched) returns fee payments in a date range plus
the household's current outstanding balance, computed the same way
`ParentDashboardService.getOutstandingBalance` already does (iterate
`OPEN`/`PARTIALLY_PAID` assignments, sum each one's `computeFeeBalance`) —
no new SQL aggregate needed, since a household's own assignment count is
always small.

**3. Platform reports are scoped to what real data actually supports —
subscription revenue and dispute rate stay out.** Subscription revenue has
no backing data (DESIGN-DOC.md section 19.3 open question #12: "Subscription-
plan tiers, pricing page currently shows one placeholder tier" — there is no
billing/subscription model to sum). Dispute rate has no backing data either
(disputes/chargebacks are explicitly a Live Payments launch-gate item,
section 14.4, never built). `PlatformReport` instead computes: new
organizations and new customers (`app_user` signups) in range — a growth
metric distinct from the existing all-time counts on `PlatformAdminDashboardService.getSummary`;
gross transaction volume (GTV) and refunded amount in range, platform-wide
(no org filter, mirroring `getPaymentsSummary`'s existing all-time
equivalent but date-ranged); a computed refund-rate percentage; and
integration health, reusing the existing webhook/outbox status counts
verbatim rather than re-deriving them. Feature adoption and retention are
also left out — both are naturally what a real `AnalyticsProvider` vendor
would compute from tracked events, not something worth hand-rolling in SQL
ahead of that choice.

**4. Org reports (revenue, campaigns, products, refunds, fee collections)
are the one fully-buildable slice, live in a new `reporting/` module.**
`ReportingRepository` (read-only, deliberately cross-cutting — reaches into
`ledger_entry`, `contribution`, `campaign`, `"order"`, `store`, `order_item`,
`product`, `fee_payment`, `household`, `team`, `organization`, `app_user`,
the same way `PlatformAdminDashboardService` already reads across modules
for its own summary cards) backs five manager-role-gated endpoints under
`/organizations/{id}/reports/*`: `revenue` (by source type and by team, plus
a CSV export), `campaigns` (contribution totals per campaign), `products`
(quantity/revenue per product from confirmed orders), `refunds`, and
`fee-collections` (payments actually recorded in range, plus the existing
point-in-time outstanding total). `from`/`to` default to the trailing 30
days when omitted — the first date-range convention this codebase has
needed; every prior date-range-shaped query (sponsorship/fee reminders) used
a forward-looking "due within N days" window instead of an arbitrary
caller-supplied range.

**5. Revenue-by-team is real; revenue-by-tournament is not buildable at
all.** `campaign.team_id`/`store.team_id` exist, so contributions and orders
can be attributed to a team (sponsorships cannot — `sponsorship_package` has
no `team_id`, so sponsorship revenue always falls into the null/
"organization-wide" bucket for this breakdown). No revenue-generating entity
(`campaign`, `store`, `sponsorship_package`) has a `tournament_id` anywhere
in the schema, so "revenue by tournament" — named in the DESIGN-DOC.md
catalog — genuinely cannot be built without a schema change, which is its
own decision, not a reporting-query gap.

**6. `CsvUtil` extracted as a shared utility.** `FeeService`'s private
`csvEscape`/`formatMinor` (Phase 2 remainder) are now `common/util/CsvUtil.kt`,
used by both the pre-existing fee-collections export and this phase's new
revenue-report export — the second real caller is what crossed the
duplication-vs-extraction threshold; `FeeService` was updated to call the
shared version with no behavior change.

## Consequences

- The reporting module is genuinely additive — no existing dashboard
  behavior changed, no migration was needed (every report reads tables that
  already exist).
- `getHouseholdFeeReport` intentionally duplicates
  `ParentDashboardService.getOutstandingBalance`'s balance-computation shape
  rather than extracting a shared helper — the two call sites differ in
  authorization (org-member vs. guardian-relationship) and it wasn't
  obviously worth a shared abstraction for one loop over a small, capped
  list. Revisit if a third call site appears.
- `PlatformReport.refundRatePercent` is nullable, not defaulted to zero, when
  there was no GTV in the period — an empty period isn't a 0% refund rate,
  it's undefined; callers (eventually a frontend report page) must handle
  the null case explicitly rather than seeing a misleading "0%."
- Household credits/orders/contributions, campaign-launch-adjacent
  subscriber questions (already deferred, ADR-023), subscription revenue,
  dispute rate, feature adoption, and retention all remain explicitly
  unbuilt after this phase — each is blocked on an older, separate product
  decision (credit rules, order/contribution attribution, a billing model,
  dispute tracking, or an `AnalyticsProvider` vendor choice), not on
  anything this reporting slice itself could resolve.
- `AnalyticsProvider` has exactly one real caller (`organization_created`).
  Every other "usage insight" the catalog implies (feature adoption,
  retention, funnel analysis) will need either more call sites wired to the
  logging stub now, or to wait until a real vendor is chosen and the event
  taxonomy is designed deliberately — this ADR does not attempt to guess
  that taxonomy.
- Every new endpoint follows the existing `@RequestParam` `LocalDate`
  pattern (`@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)`) — the first use
  of a caller-supplied date range in this codebase's API surface; future
  date-range endpoints should follow the same convention rather than
  inventing a new one.

## Alternatives Considered

- **Picking a real `AnalyticsProvider` vendor this phase (PostHog was
  discussed)**: rejected by the founder — deferred to a logging-only stub,
  same reasoning ADR-019 used for `EmailProvider`'s first two phases (no
  point standing up real vendor credentials/integration tests for a need
  that isn't concrete yet).
- **Attempting a household orders/contributions/credits report by adding a
  best-effort email-matching attribution (mirroring the interim
  `findActiveAdultByEmail` heuristic `HouseholdRepository` already uses
  elsewhere)**: rejected — the founder's own guidance was explicit that this
  should wait for a real attribution decision rather than another
  string-matching stand-in, especially for anything feeding a financial
  report.
- **Inventing a subscription-revenue proxy (e.g. treating each org as a flat
  monthly fee) to populate the platform report's subscription-revenue
  field**: rejected — there is no real subscription-plan/billing model
  (section 19.3 #12 remains genuinely open); a fabricated number in a
  report is worse than an absent field.
- **A generic `tournament_id` added to `campaign`/`store`/`sponsorship_package`
  now, just to make "revenue by tournament" reportable**: rejected as
  scope creep — that's a real schema/product decision (do tournaments
  actually own revenue-generating campaigns/stores/sponsorships, or only
  read-only aggregate from participating teams?) that belongs with whoever
  eventually builds real tournament-team relationships, not smuggled in via
  a reporting slice.
- **A shared `computeHouseholdOutstandingBalance` helper extracted between
  `ReportingService` and `ParentDashboardService`**: rejected for now — see
  Consequences; two call sites with different authorization models didn't
  clear the bar for an extraction that would itself need its own
  authorization-agnostic design.
