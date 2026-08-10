# ADR-097 — Phase 30 QA stabilization and regression fixes across Phases 24-29

**Status:** Accepted
**Date:** 2026-08-10

## Context

Phases 24-29 each shipped with unit/integration coverage but had not been exercised end-to-end through a real browser against a real running local stack since the prior full live QA pass (`QA-FINDINGS.md`, 2026-08-05), which only covered Phases 0-23. A live-browser QA session against the current `main` found several regressions the existing test suites had no way to catch, because they live at boundaries those suites don't exercise: module resolution, route registration, client-side form-to-request wiring, and session lifecycle.

This is the same class of defect ADR-086 (Phase 28.0) already found once — a shipped route (`OwnerOnboardingPage`) whose backing file was missing from the committed tree. That incident was repaired then; a *different* file it depends on (`features/onboarding/ownerOnboardingApi.ts`) went missing in a later phase and broke the same way, undetected until this pass because nothing in CI imports/renders the full route tree the way a real browser session does.

Findings, with reproduction evidence, are recorded in `QA-FINDINGS-PHASE24-29.md` at the repo root. This ADR records the roadmap-level decision to treat them as a real phase rather than an unlogged fix branch.

## Decision

Phase 30 is stabilization, not new product scope. Slotted immediately after Phase 29 in the roadmap (existing Phase 30-32 renumbered to 31-33; the mobile planning projection doc renamed `PHASE32-...` → `PHASE33-...` to match).

Fixed in this phase:

1. Restored the missing `ownerOnboardingApi.ts`, which had broken the entire frontend module graph.
2. Wired the owner-registration Terms/18+ checkboxes through to the actual `POST /auth/register-owner` request body — they were validated client-side and then silently dropped.
3. Added the missing "owner mid-onboarding" case to `DashboardContextService.resolve()` and the missing redirect in `DashboardPage`, so a fresh owner reaches `/app/onboarding` instead of a dead end.
4. Wrapped `OrganizationSubscriptionService.createCheckout`'s Stripe call in the same `ServiceUnavailableException` pattern every sibling Stripe-calling service already uses.
5. Persisted the auth session in `sessionStorage` so a page reload or new tab no longer forces re-login while the token remains valid.
6. Registered the missing `<Route>` for Platform Admin's Duplicate Identities page (Phase 27.3-27.5), which existed in full but was unreachable.
7. Corrected a regression introduced while fixing an adjacent issue in the same pass (`apiFetch` returning `undefined` instead of `null` for an empty response body, which crashes React Query) before the pass concluded.
8. Isolated and fixed the intermittent blank-render on Personal Settings and Organization Billing: none of this codebase's `useQuery` `queryFn` implementations passed React Query's `AbortSignal` through to `apiFetch`, so `<StrictMode>`'s intentional double-mount in development never actually cancelled the first of two duplicate requests at the network level, occasionally producing a transient 503 from the resulting race; separately, `SettingsPage.tsx` gated its loading state on React Query v5's `isLoading` (which requires `isPending && isFetching` together, unlike v4), so a query could be pending-but-not-yet-fetching and match none of the page's render branches. Fixed both: threaded `{ signal }` through the three affected `queryFn`s and switched `SettingsPage.tsx` to `isPending`. Verified live across 5+ consecutive fresh reloads of both affected pages with zero recurrence.
9. Caught and fixed a real (compile-time only) typecheck regression from step 7's fix, surfaced by the closing `tsc -b` pass rather than by live testing: the empty-body fallback's `payload ?? {...}` widened to a type that didn't satisfy `ApiErrorBody`. Fixed with an explicit cast.

Not fixed in this phase — logged as open items requiring a founder or developer decision, not silently dropped:

- No UI anywhere can move the Phase 25 SafeSport messaging policy off `PENDING`, despite a working backend endpoint to do so.
- Dark mode (Phase 28.1) leaves ~54 light-surface card components illegible; founder direction is a full per-component theme inversion, not a contrast patch — scoped as a dedicated future pass.
- No content triage on the message safety-report queue.
- Notification preferences default to an ambiguous "Default" state rather than a concrete On/Off value.

## Consequences

- The roadmap phase numbers for Athlete Eligibility (was 30, now 31), Payment Choice Expansion (was 31, now 32), and Native Mobile Planning (was 32, now 33) all shifted by one. Every in-document cross-reference (`§14.1K/L/M` → `§14.1L/M/N`, ADR descriptions, the privacy inventory, the testing section, the open-questions list) was updated to match; no phase's own scope or acceptance criteria changed, only its number.
- This class of defect (a route or import whose backing file silently goes missing between phases) has now recurred once after ADR-086 first found it. A future phase should consider whether a lightweight "does every route in `AppRoutes.tsx` actually resolve to an importable module" smoke check belongs in CI, rather than relying on the next live QA pass to catch it again.
- The missing-AbortSignal pattern found in step 8 likely exists in other `queryFn`s across the codebase beyond the three fixed here; a systematic audit is recommended follow-up, not undertaken in this phase.
- The open items are real product/design decisions, not implementation gaps this phase could have silently resolved on its own — they need founder input (SafeSport activation path, safety-report triage scope, notification-default UX) or dedicated engineering time (the dark-mode pass) before they're closed.
- Phase 30 closed with a fully clean automated suite: backend `./gradlew test ktlintCheck` (`BUILD SUCCESSFUL`), frontend `typecheck` (clean), and the frontend test suite confirmed at its pre-existing baseline with no regressions from this phase's changes.
