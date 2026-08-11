# ADR-105 — Mobile Owner persona (all four personas now real)

**Status:** Accepted
**Date:** 2026-08-10

## Context

Fourth and last persona per the founder's stated build order (Coach → Parent → Athlete → Owner last, "as they have more to it"). Explicitly the largest slice: an Owner/Administrator/Viewer has org-wide surface area (teams, members, reports, payouts, communications) versus the other three personas' single-team/household/self scope. Research (an agent pass, then directly spot-verified against real Kotlin source before writing any code) surfaced several real constraints that shaped the build.

## Decision

**`role: 'OWNER'` is not one membership tier.** `DashboardContextService.resolve` maps `MembershipRole.OWNER`/`ADMINISTRATOR`/`VIEWER` all to the same `DashboardRole.OWNER` — an Owner-tier mobile screen can be reached by three different real permission levels. Rather than duplicate capability-gating client-side for a first slice, mutating actions (member role update/revoke) are shown to everyone who reaches the screen and rely on the backend's real `requireManagerRole` 403 (surfaced as a toast) — a deliberate first-slice simplification, not an oversight.

**`organizationId` can be null even for `role: 'OWNER'`** — a real fallback case (`DashboardContextService.kt`) for an owner who finished signup but hasn't finished the onboarding wizard. The Owner Dashboard screen handles this explicitly (an honest "finish onboarding on the web app" empty state) rather than showing a stuck spinner.

**Two dashboard cards are unconditionally fabricated**, a different situation than Athlete's per-field honesty (ADR-104): `getAttentionRequired` returns 4 hardcoded rows with literal fake numbers and no backing table at all; `getOnboardingProgress` always returns `isDemoData=true`. Both skipped entirely — there is no honest way to present either as real or even partially real.

**A real research correction caught before writing code:** an initial research pass claimed `FinancialOverviewResponse.isFundraisingDemoData` and `ReportsSnapshot.isDemoData` were both `false` (i.e., real), directly contradicting the DTO file's own doc comments (which say fundraising/reports-snapshot "stay demo"). Reading `OwnerDashboardService.kt` directly — the actual runtime code — confirmed the research report was right and the DTO's doc comments are simply stale, a backend code/comment drift, not a mobile-side bug. When a DTO's doc comment and independent research disagree, the service implementation is the tiebreaker.

**4 tabs, not 5, same reasoning discipline as Athlete**: Home/Teams/Members/More — no Calendar tab (the Dashboard's upcoming-events card covers it; a full org calendar was left for later) and no Payments tab (Reports and Payout status live under More instead, since Owner has too many secondary destinations — Reports, Payout, Announcements-manage, Broadcasts-manage, Settings — to give each its own tab the way Coach/Parent's one extra domain gets).

**Owner gained real compose/send capability the other three personas never had.** Coach/Parent/Athlete only ever *receive* announcements/broadcasts; Owner can create org-scoped announcement drafts (`POST /organizations/{id}/announcements`, explicit two-step draft→publish lifecycle) and broadcast threads (`POST .../message-threads`, thread create and first-message-send are two separate calls). Team/tournament-scoped compose (versus org-scoped) was deliberately deferred to keep the compose forms shipping as one working scope.

**New feature modules, deliberately not reusing Coach's `features/teams/`**: `features/organization-teams/` (`OrgTeamResponse`, the full org-management team shape) is distinct from Coach's `CoachTeamSummary` (a narrower dashboard-card shape) — different DTO, different endpoint, different purpose, so a new module avoided a naming collision and a leaky abstraction. Also new: `features/membership/`, `features/reporting/`, `features/payout/`.

**Verification:** typecheck/lint (including purity rules)/expo-doctor all clean, same bar as every prior mobile ADR.

## Consequences

- **Explicitly deferred, not attempted this slice** (all real backend endpoints, just not wired to mobile yet): `payout-account/transfer` (live money movement) and `/onboarding-link` (Stripe-hosted WebView handoff) — both held until the read-only payout view is validated against real orgs; org profile/credit-settings edit forms; team/tournament create/edit/archive/delete; team-scoped (not just org-scoped) announcements/broadcasts; CSV report export.
- **Confirmed out of scope for Owner entirely, not just deferred**: Swag Shop, QuickBooks/org integrations, sponsorships, Rally26's own subscription billing, and Data Integrity/duplicate-merge tools (Platform Admin capability, not reachable by any Owner-tier role regardless of mobile scope). A later parity gap analysis corrected an earlier claim that sponsorship had no backend package — it does (`com.rally26.sponsorship`, its own Stripe checkout client) — the gap is mobile-side only.
- **All four mobile personas (Coach, Parent, Athlete, Owner) are now real and backend-wired.** Next: a systematic mobile-vs-web parity gap analysis (see ADR-106) before deciding what to build next.
