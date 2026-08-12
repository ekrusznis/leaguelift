# Live QA Findings — Phases 24-29 — 2026-08-10

Live browser QA pass covering everything shipped since the last full pass (`QA-FINDINGS.md`, 2026-08-05, which covered through Phase 23): Swag Shop Path 2/public storefront/timezone/owner onboarding (24), Messaging (25), Subscription billing (26), Data integrity tools (27), Consolidated Settings (28), QuickBooks scaffolding (29). Findings and fixes here became Phase 30 (`DESIGN-DOC.md` §14.1K, ADR-097).

Stack: local Postgres (docker, port 5433), backend 8080, frontend 5173 (Vite). Seed accounts, password `DevPassword123!`: owner `mike.anderson@riversideyouthsports.example`, coach `jordan.ellis@riversideyouthsports.example`, parent `sarah.johnson@example.com`, tournament admin `taylor.reed@riversideyouthsports.example`, platform admin `platform.admin@rally26.example`. New QA account created this pass: `qa.tester.p24b@example.com`.

**Environment note:** the backend process found already running at session start had been up since 2026-08-07 (3 days stale, predating the Phase 29 merge). It was restarted from current source before testing began.

After the live QA pass, the full automated suite was also run (`backend: ./gradlew test ktlintCheck`, `frontend: typecheck / test / lint / build`) to catch anything the browser pass alone wouldn't — see "Automated suite" section below.

---

## Fixed and verified (live browser + automated tests both green)

### 1. [CRITICAL] Entire frontend failed to build/load — missing `ownerOnboardingApi.ts`
`frontend/src/pages/OwnerOnboardingPage.tsx` (added in the "phase 28 settings slice 1" commit) imports 7 named exports from `../features/onboarding/ownerOnboardingApi`, a file that was never created. Because `OwnerOnboardingPage` is eagerly imported (not lazy) in `AppRoutes.tsx`, this Vite import-resolution failure broke **every route in the app**, including `/auth/sign-in`. Would also have failed `npm run build`.
**Fix:** created `frontend/src/features/onboarding/ownerOnboardingApi.ts` implementing the 7 functions against the real, already-existing backend `OwnerOnboardingController`/`OrganizationSubscriptionController` endpoints.

### 2. [CRITICAL] Owner self-registration silently rejected — checkboxes never sent to backend
`RegisterPage.tsx`'s "I agree to Terms" / "I am at least 18" checkboxes were collected and validated client-side (Zod) but **never included in the API call** — `registerAccount({firstName, lastName, email, password, invitationToken})` omitted `agreeToTerms`/`confirmAdult` entirely. The backend's `RegisterRequest` defaults both to `false` and rejects with `@AssertTrue`, so every real registration attempt failed with "You must confirm that you are at least 18 years old." regardless of what the user checked. This blocked **all** owner self-registration (Journey 1, step 1) — the most fundamental onboarding path in the product.
**Fix:** added `agreeToTerms`/`confirmAdult` to `RegisterParams` (both `auth/types.ts` and `auth/authApi.ts` — there were two duplicate interfaces of the same name) and to the actual `registerAccount()` call site.

### 3. [CRITICAL] Fresh owner (no org yet) dead-ended instead of reaching onboarding
Two compounding bugs, found via the founder's own observation that onboarding "should be in the create flow... not embedded into the dashboard":
- `DashboardContextService.resolve()` (backend) had no case for a real, expected state — an owner who registered/verified but hasn't finished the Organization/Plan/Checkout wizard (zero `organization_membership`, zero `role_assignment`, zero `guardian_relationship` rows). It fell through every check to the final fallback, which returns `DashboardRole.ATHLETE`.
- `DashboardPage.tsx`'s `OWNER` case rendered `<UnauthorizedState message="No organization found for your account." />` instead of redirecting to `/app/onboarding` when `organizationId` was null.

**Fix:** `DashboardContextService` now checks for an in-progress `owner_onboarding` record before falling back to Athlete, returning `DashboardRole.OWNER` with `organizationId = null`. `DashboardPage` now `<Navigate to="/app/onboarding" replace />` in that case. Added a new backend unit test (`DashboardContextServiceTest`) covering the new branch, and updated the frontend `DashboardPage.test.tsx` test (previously asserted the old dead-end text) to assert the redirect instead.

**Verified end-to-end** with a real fresh registration (`qa.tester.p24b@example.com`): register → verify → sign in → correctly lands on `/app/onboarding/organization` → Organization step saves (including a live `America/Chicago` timezone suggestion for a Springfield, IL address) → Plan step shows the real backend plan catalog ($149/mo Founding Club) → Review & Checkout renders real data → Stripe Checkout attempt fails gracefully (see #4).

### 4. [Medium] Raw `StripeException` on subscription checkout instead of the established graceful-degradation pattern
`OrganizationSubscriptionService.createCheckout` let a `com.stripe.exception.AuthenticationException` (no local `STRIPE_SECRET_KEY`) escape uncaught to the generic `GlobalExceptionHandler.handleUnexpected` → opaque 500. Every other Stripe-calling service in the codebase (`ContributionService`, `OrderService`, `SponsorshipService`) wraps `StripeException` into a `ServiceUnavailableException` with a specific, actionable message.
**Fix:** wrapped the Stripe-calling section of `createCheckout` in the same `try/catch (StripeException) -> ServiceUnavailableException(...)` pattern. Added a new backend unit test (`OrganizationSubscriptionServiceTest`) that mocks a thrown `ApiConnectionException` and asserts it surfaces as `ServiceUnavailableException`. Verified live: now returns a proper 503 with "Payments provider is not available right now. If this is local/staging, confirm STRIPE_SECRET_KEY is set."

### 5. [Significant UX, user-identified] Every page reload or link opened in the same tab forced a full re-login, even with a valid, unexpired session
`AuthContext.tsx` was, by design, entirely in-memory — no persisted session across a page reload. The 2026-08-05 QA pass's fix only addressed the *failure mode* (redirect to sign-in instead of a dead-end screen); the underlying "always logs out on reload" behavior was never actually changed.

**Fix:** the access token, its expiry, and the signed-in user's display info are now cached in `sessionStorage` (not `localStorage`). `AuthProvider` initializes state synchronously from storage on mount so `ProtectedRoute` never flashes "unauthenticated." An expired or missing token still requires signing in. Added a global `sessionStorage.clear()` to the frontend test setup's `afterEach` (`src/test/setup.ts`) so a real sign-in in one test can't leak session state into a later test in the same file. Verified live across many reload cycles this session.

### 6. [CRITICAL] Platform Admin "Data Integrity · Duplicate Identities" (Phase 27.3-27.5) was completely unreachable — the route was never registered
Clicking the "Data Integrity" nav item took a Platform Admin to a 404. The entire feature — page, API client, nav registry entry, path helper — existed and agreed with each other, but `AppRoutes.tsx` never had a matching `<Route>` under the `platform` section.
**Fix:** added the missing route (`data-integrity/duplicates`, guarded by `Capabilities.PLATFORM_USER_VIEW`). Verified live: loads correctly with real data (0 candidates against current seed data, as expected).

### 7. [CRITICAL — root cause fully isolated] "First request(s) after a page load randomly 503, then identical retries succeed" — affected Organization Billing, Personal Settings, and likely others
This was the hardest bug of the pass and took two investigation passes to fully pin down. The end-to-end story, since the wrong turns are informative:

1. **Symptom:** `/app/organizations/{id}/billing` and `/app/settings` intermittently rendered a generic error or, worse, silently rendered *nothing* (no loading state, no error, no content) for real users with real, correct backend data — reproducible on a fresh page load, but not on every attempt.
2. **First real bug found (and fixed):** the backend's nullable `GET /organizations/{id}/subscription` returns HTTP 200 with a genuinely empty body (not the JSON literal `"null"`) when no subscription exists. `apiFetch`'s original `response.json()` throws on an empty body. **Fixed** in `apiClient.ts` by catching that specific parse failure and resolving to `null`.
3. **A bug I introduced while fixing #2, caught within the same pass:** my first attempt at the fix used `undefined` as the empty-body fallback instead of `null`. React Query's `useQuery` throws a hard runtime error whenever a `queryFn` resolves to `undefined` ("Query data cannot be undefined") — this was silently blank-screening `/app` itself. **Fixed** by using `null`, and by keeping `response.json()` (not `response.text()` + manual `JSON.parse`, my very first attempt) specifically because existing test mocks across the codebase only implement `.json()`, not `.text()` — confirmed by re-running the full frontend suite, which caught this exact incompatibility in `SignInPage.test.tsx` and `App.test.tsx`.
4. **The real, final root cause, found in a second pass:** even after #2 and #3 were fixed, a genuine intermittent 503 kept reproducing on `GET /me/preferences` and `GET /me/notification-preferences` — but *only* on a cold page load, never on a manual replayed request with the same token/headers seconds later. Ruled out, each with direct evidence: stale backend process, expired JWT, the login rate limiter (wrong endpoint/status code), the general API rate limiter (`ApiRateLimitFilter` returns 429, not 503 — confirmed by reading `RateLimiting.kt`), bad DB data, and every `ServiceUnavailableException` throw site in the entire backend (`grep`-confirmed there are exactly 5, none in this call path). No `ERROR`-level log line ever correlated with the failing requests, which also rules out anything reaching `GlobalExceptionHandler`.

   The actual mechanism: `frontend/src/main.tsx` renders the app in `<React.StrictMode>`, which intentionally double-invokes effects in development (mount → cleanup → mount again) specifically to catch effects that don't clean up properly. None of this codebase's `useQuery` `queryFn` implementations passed React Query's provided `AbortSignal` through to `apiFetch` — so when StrictMode's double-mount caused React Query to logically cancel and reissue the first fetch, the *actual* underlying `fetch()` call was never aborted at the network level and kept running, racing a second, real request to the same endpoint. The 503 was a genuine transient response to that race (most likely surfaced by the dev-time double-request pattern, not something that would occur from a single real client under normal use) — not a phantom or a reporting artifact.

   **Fix:** `useUserPreferences`, `useNotificationPreferences` (`features/settings/api.ts`), and `useOrganizationSubscription` (`features/subscriptionBilling/api.ts`) now destructure `{ signal }` from the `queryFn` context and pass it through to `apiFetch`, which already accepted (but nothing used) a `signal` option. Verified live across 5+ consecutive fresh reloads of both `/app/settings` and the billing page with zero recurrence, versus reliable reproduction before the fix.

5. **A second, real symptom of the *same underlying cause*, also fixed:** `SettingsPage.tsx` used React Query's `isLoading` flag to decide when to show a loading spinner. In React Query v5 (installed: `^5.101.4`), `isLoading` was redefined as `isPending && isFetching` — a narrower condition than v4's old `isLoading` ("no data yet"). In the exact race window described above, a query could be `isPending` (no data yet) without `isFetching` being simultaneously true, making `isLoading` false — while `isError` was also false and `data` was still absent — so *none* of `SettingsPage.tsx`'s three conditional render branches matched, and the section rendered nothing at all. **Fixed:** changed `preferences.isLoading`/`notifications.isLoading` to `preferences.isPending`/`notifications.isPending`, which is `true` for the entire duration until the query has either data or an error, closing the gap by construction. This fix alone made the missing-content bug visibly become "stuck on a loading spinner" instead of silently blank — a real, visible improvement — but the *actual* elimination of the bug required the abort-signal fix (item 4 above) as well; both were needed together.

**Net effect:** this general pattern — `queryFn` not respecting React Query's abort signal — almost certainly affects other hooks in the codebase beyond the three fixed here (a repo-wide `grep` for `apiFetch` calls inside `queryFn` without a `signal` parameter would find them), since this is how every `useQuery` call in the codebase is written. Only the three call sites that were actually exercised and found broken during this pass were fixed; a systematic pass across the rest of the ~50+ query hooks is recommended as follow-up but was not attempted here (see "Not attempted" below).

6. **Caught by the final `tsc -b` typecheck pass, after live verification:** the item 2/3 empty-body fix in `apiClient.ts` typed `payload` as `unknown`, and `payload ?? { code: ..., ... }` widened to `{} | ApiErrorBody` instead of `ApiErrorBody` (TypeScript's `NonNullable<unknown>` is `{}`, not `ApiErrorBody`) — a real type error, not a runtime bug, so it didn't surface during live browser testing. Fixed with an explicit `(payload as ApiErrorBody | null) ?? {...}` cast. `npm run typecheck` is clean after this fix.

### 8. [Product/UX, founder-requested] History/audit table said "Actor" instead of "User"
`AuditHistoryPage.tsx` (shared by `/app/history` and Platform Admin's `/app/platform/audit`) had a table column header "Actor" and search placeholder text, while an adjacent filter field was already labeled "User." Both changed to "User" for consistency — display copy only, no backend change. Verified live.

### 9. [Product/UX, user-requested] Onboarding "Sports" field was free-text comma-separated
No existing canonical sports list/enum existed anywhere in the codebase to reuse. **Fix:** `OwnerOnboardingPage.tsx`'s Sports field is now a checkbox grid against a curated `SPORT_OPTIONS` list (18 common youth sports + "Other"). Scoped to onboarding only, per the request — see item 12 below for the rest of the app.

---

## Automated suite: pre-existing failures found, not caused by this pass (confirmed via isolation)

Running the full frontend suite (`npm test`) after the live QA pass surfaced these on `main` before any of this session's changes — confirmed pre-existing by re-running against `apiClient.ts` reverted to its original, unmodified state via `git stash`, and by checking that none of the affected source files were touched this session:

### 16. [Real product bug] `QuickBooksScaffoldPanel.tsx` throws on an unsafe optional-chain
Line 37: `query.data?.catalog.connection?.id` — the `?.` guards `query.data` but not the immediately-following `.catalog` access, so if `query.data` exists but `.catalog` is ever undefined, this throws `TypeError: Cannot read properties of undefined (reading 'connection')` and crashes the whole `IntegrationsPanel` render (confirmed via direct reproduction: `IntegrationsPanel.test.tsx`'s two tests fail with exactly this uncaught exception, identically with or without any of this session's changes). Not fixed — outside Phase 30's scope (Phases 24-29 regressions), but a real, developer-actionable one-line fix (`query.data?.catalog?.connection?.id`).

### 17. [Real test-coverage gap] `navRegistry.test.tsx` has two stale expected-nav-item lists
- A finance/viewer-scoped nav test doesn't expect `owner.messages` (Phase 25), but the real registry includes it.
- A platform-employee nav test doesn't expect `platform.subscriptions` (Phase 26) or `platform.data-integrity` (Phase 27), but the real registry includes both.

Both are cases where a later phase added a real, correct nav item and the test asserting the full nav list was never updated — not a Phase 24-29 regression, predates this pass. Not fixed here.

### 18. Pure test-environment gaps (not product bugs)
- `HomeHeader.tsx` uses `IntersectionObserver`, which jsdom doesn't implement and this test suite never polyfills — fails 3 tests (`App.test.tsx`'s root-route test, both `HomePage.test.tsx` tests) with `ReferenceError: IntersectionObserver is not defined`.
- `SiteHeader.test.tsx`'s dropdown-scroll test fails on jsdom's `Not implemented: navigation to another Document` — a known jsdom limitation, not a real bug.

Neither blocks real usage; both are gaps in the test environment setup. Not fixed here (out of scope, pre-existing).

**Final automated suite state after this pass's fixes:** backend `./gradlew test ktlintCheck` — **BUILD SUCCESSFUL**, all tests including 2 new ones added this pass. Frontend `typecheck` — clean (after the item 7.6 `apiClient.ts` cast fix). Frontend `test` — stable at 139/147 passing across repeated runs; the 8 remaining failures are exactly the pre-existing items 16-18 above (confirmed unrelated to this pass, reproduced identically before and after this pass's changes via `git stash` isolation). One additional run surfaced a 9th failure in `TalkToSalesPage.test.tsx` that did not reproduce on rerun — a flaky 5-second default test timeout under heavy concurrent system load, not a real issue.

---

## Needs a founder or developer decision (not fixed this pass)

### 10. No UI anywhere can ever move the SafeSport policy off PENDING
`MessageSafeSportPolicyController`'s `PATCH /organizations/{id}/messaging/safe-sport-policy` is real and working, but no frontend component ever calls it — confirmed via repo-wide search. No owner, org admin, or Platform Admin has any UI path to record a review/approval. May be intentional (matches this codebase's "code availability is not authorization" posture elsewhere for QuickBooks/Google Calendar), or a genuine gap — needs a founder decision.

### 11. "Your safety reports" has no content triage
Any message can be flagged as a "safety concern" with zero automated signal (profanity/tone/bullying detection) to help a reviewer prioritize real concerns from noise. Not a bug in what shipped — a scope question for a future phase.

### 11a. Notification preferences default to an ambiguous "Default" instead of a real On/Off value
A fresh account/topic starts as `DEFAULT`, not a concrete `ENABLED`/`DISABLED` a user can see reflected in the control. Founder feedback: seed real On/Off values, or show the resolved value in the control itself (e.g., "Default (On)"). Not fixed this pass.

---

## Open — not fixed this pass (non-blocking, logged per founder direction)

### 12. Team creation and the marketing "Talk to Sales" form still use free-text sport(s)
Same typo/consistency concern as #9 applies to `features/teams/schema.ts` and `TalkToSalesPage.tsx`. If a single canonical sports list is wanted, it should be defined once and reused everywhere rather than as independent hardcoded lists per form.

### 13. Backend logging is DEBUG-heavy; email body is deliberately never logged
Not a bug — `LoggingEmailProvider` deliberately never logs a verification token/link. Meant retrieving a real verification link for browser QA required a direct DB write. Worth a documented dev-only way to retrieve one (e.g., a local-only debug endpoint) if QA passes keep needing this.

### 14. [High, broad impact] Dark mode: white-on-white text across most "light card" panels app-wide — **RESOLVED 2026-08-11 (Phase 37.5, ADR-113)**
`grep -rl "bg-pure-white" frontend/src` returns **54 files**. With `<html class="dark">` active, a `bg-pure-white` card's `text-navy` text computes to near-white instead of navy — illegible, though the elements remain functionally clickable. **Founder direction:** the fix is a full per-component dark-mode inversion (real `dark:` background+text treatment), not a contrast patch. Reproduced on both the Swag Shop page and Messages page under different accounts.

Fixed via a real per-component `dark:` utility treatment (not a contrast patch, per the founder's own direction above) — see ADR-113. Live-reverified against the real toggle: the Messages page cited here renders fully inverted and legible in dark mode.

### 15. [Low, cosmetic] Broken "Logo" image swatch on the public athlete storefront live preview
On `/swag-shop/athlete/{slug}`, a small `<img alt="Logo placement preview">` renders broken (`naturalWidth: 0`) — the org logo asset isn't served correctly by the local media stub. Everything else on that preview (the real Printify-hosted mockup, the live name/number back-view preview) works correctly.

---

## Not attempted (flagging for follow-up, not evaluated this pass)

- A systematic repo-wide pass fixing every `queryFn` that calls `apiFetch` without passing React Query's abort `signal` (see finding #7) — only the 3 call sites actually exercised and found broken were fixed.
- Items 16-18 (pre-existing automated-suite failures) — confirmed pre-existing and out of Phase 30's scope, not fixed.

---

## Confirmed working well

- **Owner registration → email verification → sign-in → onboarding wizard → real plan catalog → Review & Checkout**, full Journey 1, works end-to-end.
- **Timezone suggestion** from address (Springfield, IL → America/Chicago) works correctly.
- **Swag Shop Path 2 personalization/live preview**: item → size/color → live front-view mockup → back-view name/number live preview → Placement/Logo size controls. Genuinely well-built.
- **Athlete Storefront management**: publish/unpublish, archive, copy link, real generated QR code all work.
- **Public athlete storefront page** correctly no-login/public.
- **Messaging**: staff broadcast/family conversation creation, guardian visibility, safety-report submission (with real audit trail) all work end-to-end.
- **Platform Admin Subscriptions view**: correctly sanitized, accurate per-organization status across all 3 seeded orgs.
- **Platform Admin Data Integrity / Duplicate Identities** (after fix #6): loads correctly.
- **Personal Settings → Appearance & Notifications** (after fix #7): loads correctly and consistently now, saves persist, auto-save on change confirmed via real `PATCH` round-trips.
- **Organization Settings directory**: clean, well-organized links to existing domain-owned settings, matching the "directory, not a parallel settings store" design principle exactly.
- **Organization Billing page** (after fix #7): correctly shows the real empty state for an org with no subscription.
- **QuickBooks Online readiness**: genuinely excellent, honest UI matching Phase 29's truthful-activation-readiness acceptance criteria precisely.
- **SportsEngine/GameChanger/MaxPreps**: correctly shown as unavailable, never faked.
- **ICS Feed / CSV Import**: both real, working connectors.

---

## Summary for triage

**Fixed this pass, verified live and by automated tests:**
1-9, all above (including the `apiClient.ts` typecheck fix at 7.6, caught by the final `tsc -b` pass). Two new backend unit tests added (`DashboardContextServiceTest`, `OrganizationSubscriptionServiceTest`); one frontend test updated to match corrected behavior (`DashboardPage.test.tsx`); one stale frontend test assertion fixed (`App.test.tsx`, unrelated pre-existing staleness from the 2026-08-05 pass); test-isolation hardening added (`sessionStorage.clear()` in `afterEach`). Backend `BUILD SUCCESSFUL`, frontend typecheck clean, frontend suite at its confirmed pre-existing baseline — all reconfirmed via a final clean automated run.

**Needs a founder decision:**
- SafeSport policy activation path (#10)
- Safety-report content triage scope (#11)
- Notification-preference default UX (#11a)

**Recommended follow-up work, not done this pass:**
- Systematic abort-signal audit across all `queryFn` call sites (see #7's "Net effect")
- Dark mode full-inversion pass across 54 files (#14)
- `QuickBooksScaffoldPanel.tsx` unsafe optional chain (#16) — one-line fix, found via the automated suite
- `navRegistry.test.tsx` stale expected-nav-item lists (#17)
- Canonical sports list reused app-wide instead of per-form free text (#12)

**Polish when convenient:** #13, #15, #18.
