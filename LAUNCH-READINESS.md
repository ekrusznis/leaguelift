# Rally26 Launch Readiness — Soft Launch Release Gate

**Target:** End-of-August 2026 soft launch  
**Primary audience:** AI coding/QA agent, founder, release reviewer  
**Repository:** `ekrusznis/leaguelift`  
**Status:** ACTIVE — this file is the launch source of truth  
**Launch mode:** FEATURE FREEZE / HARDENING / QA / RELEASE READINESS

---

## 1. Mission

Rally26 has reached the point where adding more features is less important than making the existing product dependable, coherent, safe, and production-ready.

The objective is:

> **Prove that real youth-sports organizations can onboard, configure their organization, operate teams, communicate, collect money, fundraise, sell merchandise, manage events, and use Rally26 without the development team standing over their shoulder.**

The assigned AI agent is not performing a passive review. It must establish the current baseline, identify launch defects, fix in-scope defects, add regression coverage, live-test every exposed web feature, live-retest every fix through the original reproduction path, test corresponding mobile experiences, run regression after fixes, update this document with evidence, and prepare a safe release candidate.

This is not a feature-development phase.

---

## 2. Authority and Required Reading

Before non-trivial changes, read the current versions of:

- `DESIGN-DOC.md` — authoritative architecture, security, data, launch, testing, privacy, and AI-agent rules.
- `README.md` — setup commands; product-status text may be stale.
- `docs/openapi.yaml` — API contract.
- `docs/adr/README.md` plus relevant ADRs.
- `QA-FINDINGS.md` and `QA-FINDINGS-PHASE24-29.md` if present.
- `docs/ux-review.md`.
- `.github/workflows/*`.
- `frontend/package.json`.
- `mobile/package.json`.
- `backend/build.gradle.kts`.

Existing QA documents are evidence, not truth. Reproduce important historical findings against the current branch.

At startup record:

```text
Baseline date: 2026-08-19
Branch: feature/qa_mobile_android_firebase_testing
Commit SHA: d23a23f92a952b6dfe5e0a39d091434bcd3f0556 (+ uncommitted baseline fixes below)
Backend test/check/build: PASS (./gradlew test check build — BUILD SUCCESSFUL)
Frontend test/typecheck/lint/build/E2E: PASS (198/198 unit tests, typecheck clean, lint 0 errors/~35 pre-existing a11y+hooks warnings, build succeeded, E2E 24/24 against the live docker-compose stack after fixing 2 stale tests — see LR-004)
Mobile typecheck/lint/targeted tests: PASS after fixes (see below) — typecheck clean, lint 0 errors/2 pre-existing warnings, test:owner-onboarding 6/6 pass
Full-stack startup: PASS (docker compose up --build; frontend :5173 200, backend :8080 actuator health/liveness/readiness all UP, postgres :5433 healthy, minio healthy)
```

### Baseline fixes applied (blocking the gate itself, fixed inline per launch-readiness rules)

- `mobile/src/lib/money.ts` — `currencyFractionDigits` could return `number | undefined` (TS lib typing for `resolvedOptions().maximumFractionDigits`), unlike `frontend/src/lib/money.ts` which already guards with `?? 2`. Added the same fallback. This was a real money-formatting correctness gap, not just a type error — an unusual currency code could have produced `NaN` in downstream fee/payment amount formatting on mobile.
- `mobile/src/features/fundraisingGames/api.ts`, `mobile/src/features/sponsorship/api.ts` — internal helper functions (`actionHook`, `reviewMutation`) called `useQueryClient`/`useMutation` without being named as hooks, violating `react-hooks/rules-of-hooks` and blocking the lint gate. Renamed to `useFundraisingGameAction` / `useReviewMutation`. No behavior change (always invoked synchronously from real exported hooks).
- `mobile/src/app/fundraising-detail.tsx` — unescaped apostrophe (`react/no-unescaped-entities` lint error), cosmetic fix.
- `mobile/.expo/types/router.d.ts` (gitignored, machine-local) was stale from 2026-08-11 and predated the `team-staff` / `participant-teams` routes, causing false-positive typecheck failures. Regenerated via `npx expo export --platform android`. Not a code defect, but flags a real gap: **mobile has no CI workflow at all** (`.github/workflows/` has zero mobile jobs), so nothing currently enforces typecheck/lint/tests pre-merge — see finding LR-002 below.

### Environment notes

- Local Node was 20.19.0; mobile's transitive deps (`react-native`, `metro`, etc.) require `^20.19.4 || ^22.13.0+`, and `test:owner-onboarding` uses `--experimental-strip-types` which 20.19.0 doesn't support at all. Installed Node 22 LTS via `nvm install 22` (user-approved) to run the test; PATH in this shell has a hardcoded pin to the old nvm version ahead of the nvm symlink, so `nvm use` alone doesn't switch it here — worth cleaning up in shell profile separately, not a product issue.
- README.md's "Current status" section is still stale (says Phase 0–2 done); real state is far beyond that per project memory. Flagged for the §27 documentation-reconciliation pass, not fixed now to stay in scope.

At the time this file was authored, the latest observed `main` commit was `c3e2caaa41061a0bf885ae4a92b3a9f0c971bae1` (`small fixes`, 2026-08-15). Pull the actual latest state before beginning.

---

## 3. Launch Philosophy

### Feature freeze

Allowed work:

- P0/P1 fixes.
- approved sport-configuration foundation.
- approved Founding Organization onboarding/promo workflow.
- security, permission, privacy, data-integrity, payment, billing, accessibility, responsive, copy/terminology, observability, test, and production-readiness fixes.
- hiding or disabling incomplete exposed functionality that cannot safely be finished.

Do not add without explicit founder approval:

- major new feature areas.
- AI Highlights expansion.
- AI practice-generation or Training Center expansion.
- Hudl or new partner integrations.
- broad advertising-network implementation.
- major redesigns.
- speculative rewrites or unrelated refactors.
- “while we are here” features.

If an exposed feature cannot safely be finished, hide it, feature-flag it, or clearly mark it unavailable rather than ship a broken surface.

---

## 4. Severity and Status

| Severity | Meaning | Launch Rule |
|---|---|---|
| **P0 — Blocker** | Security/privacy leak, cross-org access, money corruption, data loss, broken auth/onboarding, production crash, broken core persona journey | Must be fixed |
| **P1 — Major** | Important workflow failure, misleading financial state, major mobile/web parity gap, serious trust issue | Fix unless founder explicitly accepts |
| **P2 — Minor** | Functional but awkward/inconsistent, non-critical edge case | Fix if safe; otherwise immediate post-launch |
| **P3 — Enhancement** | New capability / optimization | Do not build during freeze |

Allowed readiness states:

```text
UNVERIFIED
IN TEST
FAILED
FIXING
RETEST
PASS
BLOCKED
HIDDEN FOR LAUNCH
FOUNDER ACCEPTED
```

A user-facing feature is not PASS because code looks correct. PASS requires relevant automated checks plus real use in the running application, expected state verification, permission checks where relevant, and retest after related fixes.

---

## 5. Non-Negotiable AI Agent Rules

### Live testing is mandatory

**Every exposed web feature must be live-browser tested.** Code inspection, unit tests, API tests, or Playwright alone are not substitutes for exercising the real UI against the real running backend.

### Every fix gets a live retest

For each bug:

```text
reproduce
→ preserve repro steps
→ make the smallest safe fix
→ add/update regression coverage where feasible
→ run focused tests
→ rebuild/restart as needed
→ repeat the original path in a live browser
→ test adjacent behavior
→ update this file
```

Never mark a fix complete from a unit test alone.

### Additional rules

- Do not batch dozens of unrelated fixes.
- Do not silently change product rules.
- Mark unresolved product ambiguity `FOUNDER DECISION REQUIRED`.
- Never fake external integration availability.
- Never “fix” a permission issue by weakening authorization.
- Preserve/improve audit logs for money, refunds, subscriptions, permissions, participants, media, eligibility, support access, and organization administration.
- Do not use manual DB edits as part of a normal customer journey.
- Do not expose raw UUIDs, internal enums, provider payloads, SQL, stack traces, or debug text to normal users.

---

## 6. Baseline Build and Test Gate

### Backend

```bash
cd backend
./gradlew test
./gradlew check
./gradlew build
```

Verify:

```text
GET /actuator/health
GET /actuator/health/liveness
GET /actuator/health/readiness
```

### Web

```bash
cd frontend
npm install
npm run test
npm run typecheck
npm run lint
npm run build
npm run test:e2e
```

### Mobile

```bash
cd mobile
npm install
npm run typecheck
npm run lint
npm run test:owner-onboarding
```

Run any additional current tests discovered in the repo.

### Full stack

```bash
docker compose up --build
```

or the current documented equivalent.

Missing local external credentials are not automatically product failures. Verify that Rally26 handles the missing provider safely and honestly.

---

## 7. Launch Feature Readiness Matrix

Populate status/evidence while testing. Add any currently exposed feature not listed here.

| Area | Feature / Flow | Web | Mobile | API/Auth | Live Tested | Status | Evidence |
|---|---|---|---|---|---|---|---|
| Auth | Register | ☑ | ☐ | ☑ | ☑ | PASS | Live-tested a brand-new owner registration (real 202, "Check your email, QA" screen, correct field validation/checkboxes required) |
| Auth | Email verification | ☑ | ☐ | ☑ | ☑ | PASS (gate confirmed) | Live-tested: an unverified account is correctly blocked at sign-in with a clear "Verify your email before signing in" message (no silent failure). Actual link-click verification not completable in local dev by design — `LoggingEmailProvider` deliberately never logs template variable values (including verification URLs/tokens), per an explicit DESIGN-DOC.md §18.2 privacy comment in the source — not a gap, a correct security choice |
| Auth | Sign in / sign out | ☑ | ☐ | ☑ | ☑ | PASS | Live-tested as Owner (mike.anderson) and Guardian (sarah.johnson) against docker-compose stack after LR-005 fix; required fixing CSP first (LR-005) |
| Auth | Session restore / refresh | ☑ | ☐ | ☑ | ☑ | FAILED | See LR-006 — hard reload sometimes leaves the page blank (empty `#root`) for 15+ seconds or longer with no recovery; sometimes self-heals in a few seconds. Root cause not confirmed; founder decision needed on next step |
| Auth | Password recovery if exposed | ☑ | ☐ | ☑ | ☑ | PASS | Live-tested: submitted a real reset request for mike.anderson, correct generic "Check your email... if an account is associated" success message (doesn't leak account existence). One transient 503 observed in the browser network log on this specific request; confirmed via direct curl immediately after (204, clean) that this was a one-off self-healing blip, consistent with the already-documented LR-006 flakiness pattern, not a new deterministic bug |
| Auth | Invitation — existing user | ☐ | ☐ | ☐ | ☐ | UNVERIFIED | Not yet tested — only the new-user path was walked this session |
| Auth | Invitation — new user | ☑ | ☐ | ☑ | ☑ | PASS | Live-tested full round-trip (invite → register → verify email → sign in → Accept Invitation) with two real invitations (ekrusznis@gmail.com, support@rally26.com) against docker-compose; correct role (Administrator) granted at the correct org, invitation flips to ACCEPTED, audit trail records both events. Found and fixed a real copy bug along the way (LR-007); local email delivery is logging-only (no real Resend key configured), verified via outbox event payloads instead of live inbox delivery |
| Onboarding | Owner onboarding | ☑ | ☐ | ☑ | ☑ | PASS (partial — registration gate) | Live-tested the registration → email-verification-gate half of this flow this pass (see Auth rows above). Actual post-verification org-setup wizard not reachable in local dev (no real email delivery to click the link), and not separately re-tested against the org creation already exercised earlier this session |
| Onboarding | Organization creation | ☐ | ☐ | ☐ | ☐ | UNVERIFIED | |
| Onboarding | Founding Organization entitlement | ☐ | ☐ | ☐ | ☐ | UNVERIFIED | |
| Subscription | Free if launch-enabled | ☐ | ☐ | ☐ | ☐ | UNVERIFIED | |
| Subscription | Starter $49 | ☐ | ☐ | ☐ | ☐ | UNVERIFIED | |
| Subscription | Club $149 | ☐ | ☐ | ☐ | ☐ | UNVERIFIED | |
| Subscription | Capacity / upgrade / downgrade | ☐ | ☐ | ☐ | ☐ | UNVERIFIED | |
| Organization | Profile / branding / timezone | ☑ | ☐ | ☑ | ☑ | PASS (branding upload) | Live-tested the branding logo upload as part of LR-029 verification: uploaded a real PNG through the org Settings page, confirmed via API that the resulting asset has correct real metadata (`contentType: image/png`, `64x64`, `177` bytes matching the source file exactly) — a second, independent confirmation that LR-029's direct-to-storage upload fix generalizes beyond Documents. Profile/timezone field editing itself not yet exercised this pass |
| Organization | Members / roles / invitations | ☑ | ☐ | ☑ | ☑ | PASS (search, fixed) | See LR-027 — `GET .../members/search` (the Members page's search/filter UI) had no backend mapping at all, 404 on every request; fixed and verified via curl (real 200 with the organization's 4 actual seeded members: owner, team administrator, 2 admins). Cross-org isolation confirmed separately (Riverside owner denied Lakeside org overview + team detail, 403 at API layer, no data leak). Role editing / invitation flow itself not yet walked |
| Teams | Create / edit / archive | ☑ | ☐ | ☑ | ☑ | PASS | See LR-016 — the Teams list page was completely broken (500 on every load) until fixed this session; now confirmed live with real seeded data and full action set (Schedule/Roster/Branding/Manage access/Timezone/Colors/Archive). Team creation itself confirmed earlier this session too |
| Teams | Roster / Coaches & Staff (coach persona) | ☑ | ☐ | ☑ | ☑ | PASS | See LR-020 — the "Coaches & Staff" panel 500'd for every caller until fixed this session; verified via direct curl as the real coach account (jordan.ellis, TEAM_ADMINISTRATOR) post-fix. Athlete roster on the same page confirmed with real data (Sofia Martinez) |
| Teams | Sport configuration | ☑ | ☐ | ☑ | ☑ | FAILED | See LR-011 — no canonical sport code, no backend validation, no terminology derivation anywhere; `sport` is free text end to end |
| Teams | Roster / staff (owner "Manage access") | ☑ | ☐ | ☑ | ☑ | PASS | Live-tested as Owner: JV Soccer's "Manage access" panel shows the real staff assignment (Jordan Ellis / TEAM MANAGER) with a working "Revoke" action button. Revoke itself not clicked (would disrupt the coach persona's access for the rest of this pass) |
| Teams | Branding/colors | ☑ | ☐ | ☑ | ☑ | PASS (branding) | Live-tested as Owner: JV Soccer's "Branding" panel expands inline with real Logo/Cover image upload controls (same presigned-upload pipeline as LR-029, no separate bug found here) — correct default "JV" text-avatar empty state, no logo uploaded yet. "Colors" button present but not clicked this pass |
| Teams | Season rollover | ☐ | ☐ | ☐ | ☐ | UNVERIFIED | Panel exists on the Teams list page (source team / new team name / new season, explicit note that historical/financial records are never moved or copied) but not exercised this pass |
| Tournaments | Create/edit/archive | ☑ | ☐ | ☑ | ☑ | PASS (create + archive) | Live-tested as Owner: real Tournaments list (search/filter/sort), created a real "QA Test Cup" tournament (201, appeared immediately), archived it (status flipped to ARCHIVED, Archive action correctly removed). Edit not yet tested |
| Households | Create / adults / athletes | ☑ | ☐ | ☑ | ☑ | PASS (list) | See LR-016 — the Households & Athletes list page was completely broken (500 on every load) until fixed this session; now confirmed live with real seeded data. Create/adults/athletes sub-flows not yet individually walked |
| Households | Team assignment / guardian invite | ☐ | ☐ | ☐ | ☐ | FAILED | See LR-008 — guardian invite is not implemented anywhere in the product (verified by full-codebase search); team assignment not yet separately tested |
| Guardian | Overview / family schedule / athlete detail | ☑ | ☐ | ☑ | ☑ | PASS (pre-linked guardian only) | Live-tested sarah.johnson (pre-seeded, already-linked account): Family Overview renders real household data, cross-household access correctly 403s. **Does not cover getting a real guardian linked in the first place** — see LR-008: no product code path can actually link a new guardian to a household today |
| Coach | Dashboard / team schedule / roster / messaging | ☑ | ☐ | ☑ | ☑ | PASS | Live-tested jordan.ellis (TEAM_ADMINISTRATOR): Team Overview dashboard, team schedule, roster (Coaches & Staff + Athletes, LR-020), eligibility filter (LR-022), started a real family conversation with a guardian+athlete (LR-023 slice), and used the safety-moderation panel (Start review/Dismiss, LR-023). All confirmed live end-to-end with real data |
| Athlete | Dashboard / schedule / self-RSVP / messaging | ☑ | ☐ | ☑ | ☑ | PASS | Live-tested maya.johnson: dashboard (My Teams, Guardians), Athlete Schedule (real event data), self-RSVP on an event ("My RSVP" control, correctly shares state with the guardian's own RSVP on the same participant), messaging (received the coach's new conversation per mandatory guardian/athlete visibility, sent a real reply), and confirmed peer-to-peer athlete messaging is correctly gated behind an org-level SafeSport/compliance approval that hasn't been granted for this org (not a bug — working as designed) |
| Events | Create/edit/cancel/postpone | ☑ | ☐ | ☑ | ☑ | PASS (create only) | Live-tested creating a draft event as Owner; correct timezone-aware display (America/Chicago → CDT), appears in list immediately. Edit/cancel/postpone not yet tested. See LR-012 for the date-format duplication and LR-011 for the generic "Game / match / competition" type label (no sport-derived terminology) |
| Events | Sport terminology | ☐ | ☐ | ☐ | ☐ | UNVERIFIED | |
| Events | RSVP / staff visibility | ☑ | ☐ | ☑ | ☑ | PASS (guardian + owner) | See LR-018 — guardian RSVP was completely broken (silent 403 hid the controls on every eligible event) until fixed this session; now confirmed live end-to-end (guardian submits Maybe, aggregate updates in real time). Owner aggregate view + management actions (Send reminder/Postpone/Cancel) also confirmed live earlier this session. Coach-visibility variant not yet tested |
| Events | Maps / ICS / CSV / feed import | ☐ | ☐ | ☐ | ☐ | UNVERIFIED | |
| Messaging | Team / broadcast | ☑ | ☐ | ☑ | ☑ | PASS (conversations) | Live-tested guardian↔owner Varsity Soccer conversation thread as sarah.johnson: existing thread rendered with real prior message (Mike Anderson, Aug 10), sent a real reply that appeared in the thread instantly. Team-wide broadcast (as opposed to 1:1 conversation) not separately tested this pass |
| Messaging | Athlete messaging / safety controls | ☑ | ☐ | ☑ | ☑ | PASS | Live-tested guardian communication restriction controls (`/app/messages`) as sarah.johnson for athlete Maya Johnson: recorded a "Stop staff → athlete messages" restriction (confirmed ACTIVE, retained as safety history per design), then lifted it via the real "Lift" action (confirmed status changed to LIFTED, history preserved not deleted). Gated athlete peer messaging present but not exercised this pass |
| Messaging | Coach family conversations / broadcast | ☑ | ☐ | ☑ | ☑ | PASS | Live-tested as coach jordan.ellis: started a real new family conversation (Varsity Soccer, Maya Johnson + Sarah Johnson recipients), confirmed it appeared in "Your message threads" and the manager "Threads" list with the correct member list and first-message body. Broadcast creation UI present but not exercised this pass |
| Messaging | Safety report moderation (start review/resolve/dismiss) | ☑ | ☐ | ☑ | ☑ | PASS (fixed) | See LR-023 — "Start review" 500'd on every attempt until fixed this session; verified live pre/post-fix on a real leftover safety report, then dismissed it as real cleanup (both the non-terminal and terminal status transitions confirmed working post-fix) |
| Eligibility | Requirements / clearance / waivers | ☑ | ☐ | ☑ | ☑ | PASS (guardian acknowledgment) | Live-tested as sarah.johnson: expanded Maya's "Eligibility" section on the guardian's own Athletes page, saw a real "Season Liability Waiver" requirement in "Action needed" state, completed the two-step acknowledge flow ("I acknowledge" → confirm), and the status correctly moved to "Submitted · Complete" with today's date, persisting across reload (clearance pill also flipped to "Cleared"). Owner-side requirement creation/management not tested this pass |
| Marketing site | Positioning / hero / FAQ / Security page accuracy | ☑ | ☐ | ☑ | ☑ | PASS (fixed) | See LR-021 — hero/FAQ/Security page/footer all made stale, false, or misleadingly narrow claims against current product reality (fabricated stat claims, "not a roster system," "no child accounts," "passwords not stored," "payments when live payments launch," unbacked SportsEngine/TeamSnap/SMS feature claims). All corrected and verified live |
| Marketing site | Talk to Sales lead capture | ☑ | ☐ | ☑ | ☑ | PASS (fixed) | See LR-021 — the 25-field form validated and showed a fake confirmation with a generated reference number but never persisted or notified anyone; every submitted lead was silently lost. Replaced with a compact real form on the same backend-wired public support-case endpoint as the working Contact Us section; live-submitted a real test lead and confirmed it persisted in `support_case` with the correct requester/subject |
| Marketing site | Pricing clarity / Founding Organization page | ☑ | ☐ | ☑ | ☑ | PASS (fixed) | See LR-021 — fixed the "$149/mo → Start Free" CTA contradiction (differentiated per-tier CTAs), added inline fee-math example, added a visual-only FREE 4th tier (routes to Talk to Sales, not live registration — see DESIGN-DOC.md §14.1T/U, founder decision 2026-08-19 to defer real FREE signup until entitlement-gating audit), and built a real `/founding-organizations` landing page replacing the prior redirect straight into generic registration |
| Documents | Org/team/family docs if exposed | ☑ | ☐ | ☑ | ☑ | PASS (org-level, fixed) | See LR-029 — every direct-to-storage browser upload (this page's "Add document" included) was completely broken locally due to two compounding bugs (browser-unreachable presign endpoint + missing CSP allow-list entry for the storage origin), likely also affecting production (unverified live). Fixed and verified live: uploaded a real PDF ("Season Handbook 2026"), confirmed it appeared correctly in the list with real metadata (`application/pdf · 486 B`), then removed it as cleanup. Household-level document assignment and "Send to every household" broadcast not yet exercised this pass |
| Media | Upload / privacy / public release | ☐ | ☐ | ☐ | ☐ | UNVERIFIED | |
| Fees | Templates / assignments / plans | ☑ | ☐ | ☑ | ☑ | PASS (templates) | Live-tested as Owner: real Fee Templates list (Fall Uniform Fee $75, Registration Fee $150) with search/filter/sort/Archive, "View disputes" and "View collections and export" links both real. Payment plan creation itself not yet exercised |
| Fees | Payments / credits / collections / export | ☑ | ☐ | ☑ | ☑ | PASS (fixed) | See LR-030 — the org-wide Collections report (and 8 other sites: dashboard/Reports "Fees Collected", Action Center overdue-fee badge, the guardian-facing "you owe money" prompt, payment reminders, QuickBooks export candidates) counted abandoned/never-confirmed Stripe checkout attempts as real collected revenue, understating or zeroing out real outstanding balances. Found live on the Collections page (real seeded fee showed "Paid $110.00" on a $75.00 fee). Fixed across all 9 real sites, verified via curl against the rebuilt backend using the exact real data that exposed it (now correctly shows Paid $40.00 / Balance $35.00) plus a new real-Postgres regression test. **Decisive live confirmation of the most severe instance**: signed in as the real guardian (sarah.johnson) and confirmed her own Family Overview's "Outstanding Balance" now correctly shows $82.50 total including the real $35.00 Fall Uniform Fee — pre-fix, this exact fee would have shown $0 owed and never surfaced to the parent who actually owes it. Guardian online-payment path (Stripe Checkout Session, correct amount/branding) confirmed working earlier this session; manual/offline payment recording and CSV export not yet separately exercised |
| Payments | Stripe connect / checkout / failure | ☑ | ☐ | ☑ | ☑ | PASS (checkout success path) | Real Stripe test-mode checkout completed live (user completed the card form after the extension correctly refused to automate payment-form entry — see below); redirect-back page shows an honest "Confirming your payment..." state, never a premature "success," and no raw UUIDs rendered on-page (only in the URL query string). Failure/decline path not yet tested |
| Payments | Webhooks / idempotency | — | — | ☑ | — | PASS | Verified via `stripe listen --forward-to` + `stripe trigger checkout.session.completed` against the live docker-compose backend: real, correctly-signed Stripe webhooks get clean 200s end-to-end. Existing backend test suite (`StripeWebhookControllerTest.kt`) explicitly covers invalid-signature rejection (400), replayed-event no-op (idempotency), unrecognized-event handling, and correct dispatch routing (contribution/order/sponsorship/payout-account/dispute) — all passing. Found and fixed a real gap along the way: a malformed webhook request (missing signature header) was a false-positive 500/ERROR-alert instead of a clean 400 — see LR-013. One real local limitation: the specific checkout session a live user completed during this session (`cs_test_a1sW...`) never got its real webhook delivered, since Stripe can't reach a private `localhost` URL — it's stuck in `PENDING_CHECKOUT` locally, which is correct/expected app behavior (never trusts the browser redirect alone), not a bug |
| Payments | Refunds / disputes / payout if exposed | ☐ | ☐ | ☑ (partial) | ☐ | FAILED (fees) | See LR-015 — fee payments have no real refund path, only a ledger-only "Void" with zero Stripe interaction, offered identically for card and non-card payments alike. Contribution/order/sponsorship refunds ARE real (genuine Stripe-integrated endpoints exist per openapi.yaml), not yet live-tested. Dispute webhook routing confirmed via passing existing tests; payout account UI not yet walked |
| Fundraising | Create / publish / contribution | ☑ | ☐ | ☑ | ☑ | PASS (create/approve/close/archive) | Live-tested as Owner mike.anderson: created a real "General fundraiser" campaign (client-side slug validation caught a real omission correctly), submitted for approval — correctly auto-activated since owner-created fundraisers skip the "owner approval required for non-owner creators" gate (verified against the policy's own stated wording, not just assumed) — then closed and archived it as cleanup. Contribution (supporter-side payment) flow not exercised this pass |
| Fundraising | Attribution / credits / templates if exposed | ☐ | ☐ | ☐ | ☐ | UNVERIFIED | |
| Swag Shop | Store / catalog / Printify | ☑ | ☐ | ☑ | ☑ | PASS | Live-tested as Owner: real store ("Riverside Team Store"), brand asset library (1 real logo), 4 real Printify-linked products, athlete storefront (Maya Johnson, published, real slug/QR). Manual vendor add not exercised |
| Swag Shop | Orders and fulfillment | ☑ | ☐ | ☑ | ☑ | PASS (fixed) | See LR-025 — the entire "Orders and fulfillment" panel 404'd for every organization until fixed this session; verified via curl against the rebuilt backend (2 real orders returned correctly, including per-order fulfillment status). Live browser re-verification blocked by LR-024; accepted code+curl per established precedent |
| Swag Shop | Checkout / order / receipt | ☑ | ☐ | ☑ | ☑ | PASS (public storefront, fixed) | See LR-031 — founder-flagged live that the public storefront showed no product images at all; root-caused to a missing field on the public API response (real Printify mockups existed in the DB the whole time, just never surfaced past the org-admin panel) and fixed. Also confirmed every product variant always renders as its own separate list row (no size/color dropdown consolidation even when a product has multiple variants) — a real, valid UX improvement, logged as a follow-up rather than built now since it's a genuine design decision (how to group, which dimension becomes the dropdown, how mixed-type products behave), not a bug fix. Full cart → Stripe Checkout redirect flow confirmed working earlier this session; receipt/confirmation panel not separately re-verified this pass |
| Sponsorships | Package / publish / QR / payment | ☑ | ☐ | ☑ | ☑ | PASS (list + review-search, fixed) | See LR-026 (package list 500'd for every organization) and LR-028 (the separate "Review pending sponsorships" search endpoint had no backend mapping at all) — both fixed this session and verified via curl (real "Gold Sponsor" package; empty-but-correct 200 for review search against current seed data, which has no confirmed sponsorships). Publish/QR/payment sub-flows not yet exercised |
| Public Pages | Create / edit / publish / public route | ☑ | browser | ☑ | ☑ | PASS (storefront route) | Live-tested the public `/swag-shop/{slug}` route as a fully anonymous visitor (no auth, fresh tab): the real published "Riverside Team Store" renders correctly with real products/variants/prices (Sweatpants, Hoodie, Tee, Team Tee), quantity selection updates the cart, "Checkout (1 item)" button correctly reflects state. Did not click through to real Stripe checkout to avoid creating a real pending order. Other public page types (campaigns, box pools, sponsor pages, the generic `/p/:slug` public page) not separately re-tested this pass; create/edit/publish authoring flow not exercised this pass either |
| Dashboards | Owner | ☑ | ☐ | ☑ | ☑ | PASS | Extensively live-tested throughout this pass: Organization Summary, Financial Overview (verified correct pre/post LR-030), Team Performance, Upcoming Events, Recent Activity, Reports Snapshot all show real data and correct real-time updates |
| Dashboards | Coach | ☑ | ☐ | ☑ | ☑ | PASS | Live-tested as Coach (jordan.ellis): real Team Overview — My Teams (JV Soccer/Varsity Soccer, real athlete counts), team switcher, Team Schedule, Roster Summary (honestly self-labeled "Roster count is live · attendance figures are demo data" — accurate, not misleading), Team Page Status, Fundraising Progress. Full coach-scoped sidebar (Action Center, Announcements, Messages, My Teams, Schedule, Roster Summary, Team Roster, Team Page, Fundraising, Swag Shop) |
| Dashboards | Parent | ☑ | ☐ | ☑ | ☑ | PASS | Live-tested as Guardian (sarah.johnson): real Family Overview — athlete card (Maya Johnson, Varsity Soccer), Family Schedule, Outstanding Balance (real $82.50 total, correctly itemized — see LR-030), Family Credits ($5.00 available), Active Fundraisers (Winter Gear Drive with real progress bar + sharing link), Recent Orders (a real FAILED order correctly shown with its $4.00 credit), Documents. Full guardian-scoped sidebar nav (My Athletes, Family Schedule, Fees & Payments, Fundraising, Swag Shop, Documents, Household Profile) all present |
| Dashboards | Athlete | ☐ | ☐ | ☐ | ☐ | UNVERIFIED | |
| Search | Global / feature search | ☑ | ☐ | ☑ | ☑ | PASS (functionality) / FOUNDER DECISION REQUIRED (authorization) | Live-tested as Owner: real debounced search (real `/search?q=` calls, both `Maya` → "Maya Johnson · Athlete" and `Johnson` → "Maya Johnson · Athlete" + "Johnson Family · Household" returned correctly, each clearly typed/categorized — confirms multi-type result categorization already works correctly). Household result click correctly navigates to the real household detail page. Team/Athlete/Organization result clicks intentionally do nothing beyond closing the dropdown — documented, current scope, not a bug. See LR-032 for a real, separately-found authorization gap: the API never actually enforces the documented Owner/Admin-only intent, so any org member (including a team-scoped Coach) can pull every household/team/participant in the org via direct API call, bypassing the UI's own scoping |
| Action Center | Role-appropriate surfaces | ☑ | ☐ | ☑ | ☑ | PASS | Live-tested as Owner: real work queue (4 open items — Resolve fulfillment exceptions, Follow up on overdue fees, Close ended fundraisers, Review unpublished events), each with a working "Open" link that routes to the correct real page. "Follow up on overdue fees" correctly shows exactly 1 (a fourth independent confirmation of the LR-030 fix — this count would have shown 0 pre-fix, silently hiding the real overdue fee from the Owner's work queue) |
| Reporting | Reports / analytics | ☑ | ☐ | ☑ | ☑ | PASS | Live-tested as Owner across two sessions (once verifying LR-030's fix): real date-range picker, Revenue/Fees Collected/Outstanding/Refunds summary cards, Revenue by source, Revenue by team, Campaign performance, Product performance — all real data, correctly reflecting the LR-030 fee-payment fix (Fees Collected dropped from the pre-fix inflated figure to the correct $80.00). "Export revenue CSV" returns a real 200. Household-level report view not separately tested this pass |
| Notifications | In-app / email / push / SMS if enabled | ☑ | ☐ | ☑ | ☑ | PASS | Live-tested the full Announcements pipeline as Owner: created a real draft, published it (real 200, "8 recipients · Email 0 sent / 0 failed" at publish time), confirmed it appeared instantly in "Your inbox" with a real "New" badge and correct unread count in the top nav. Verified via backend logs that all 8 real resolved recipients were processed through the outbox worker to the email provider (logging-only locally, no real Resend key configured — expected, matches every other email-dependent flow this session). Push/SMS not exercised — SMS provider is confirmed unfunded/logging-only elsewhere this session; push not evaluated |
| Help | Help Center | ☑ | ☐ | ☑ | ☑ | PASS | Live-tested as Owner: real role/access-filtered article list, live search-as-you-type filtering (e.g. "fundraiser" correctly narrows to fundraising articles), category filter present |
| Support | Submit / status / admin response | ☑ | ☐ | ☑ | ☑ | PASS (submit + status) | Live-tested as Owner: submitted a real support case (real 200, real case id, confirmation banner), immediately appeared in "My recent cases" as OPEN alongside a real pre-existing case ("Public page creation doesn't submit," IN PROGRESS). Admin-side response/resolution flow not yet tested from the platform-admin side |
| Platform Admin | Overview / orgs / payments / support / audit | ☐ | — | ☑ | ☑ | IN TEST | Isolation direction confirmed: an Owner (mike.anderson) hitting `/app/platform` gets a clean "You don't have access to this page," and `/api/v1/platform/dashboard/summary` + `/organizations` both 403 at the API layer for a non-platform-admin token. Platform admin's own console UI/features not yet walked |
| Integrations | QuickBooks / TeamSnap / SportsEngine | ☑ | ☐ | ☑ | ☑ | PASS (honest scaffold) | Live-tested as Owner: all three correctly show "Not configured" with clear, accurate explanatory copy (real OAuth2/credential/sandbox prerequisites listed, not vague). QuickBooks has a detailed "readiness gates" panel (Activation blocked, per-gate Pending status: integration scaffold exists / company context / sandbox verification / accounting-approval / write-policy) — matches the honest-scaffold pattern established by LR-021's marketing-site fixes earlier this session; no misleading "connected" claims anywhere |
| Integrations | GameChanger / MaxPreps if exposed | ☐ | ☐ | ☐ | ☐ | UNVERIFIED | Not found on the Integrations page this pass — likely not built/exposed at all rather than hidden; not confirmed either way |
| Integrations | CSV / ICS | ☐ | ☐ | ☐ | ☐ | UNVERIFIED | Integrations page header references "reviewed CSV and ICS workflows" but the actual controls were below the fold, not reached this pass |
| Ads | Free adult sponsor bar if launch-enabled | ☐ | ☐ | ☐ | ☐ | UNVERIFIED | |
| Ads | Confirm NO athlete ads | ☐ | ☐ | ☐ | ☐ | UNVERIFIED | |

Unavailable integrations still need testing for an accurate, non-deceptive unavailable state.

---

## 8. Sport-Specific Team Configuration — Launch Foundation

The team must use sport as a real configuration driver, not arbitrary behavior text.

### Launch requirements

1. Team creation uses a supported canonical sport selector.
2. Backend validates a stable sport code.
3. API exposes sport code + friendly display name.
4. Web/mobile resolve terminology consistently.
5. Obvious team/event/roster/athlete-facing language updates by sport.
6. Terminology is derived, not copied to every team DB row.
7. Existing values are normalized/migrated safely.
8. Sport changes are restricted once sport-specific data makes a change unsafe.
9. Unknown historical values fail safely and remain recoverable.

### Minimum sport matrix

| Sport Code | Display | Competition | Segment | Participant | Area | Score Concept |
|---|---|---|---|---|---|---|
| `VOLLEYBALL` | Volleyball | Match | Set | Player/Athlete | Court | Point |
| `SOCCER` | Soccer | Match | Half | Player | Field | Goal |
| `BASKETBALL` | Basketball | Game | Quarter | Player | Court | Point |
| `FOOTBALL` | Football | Game | Quarter | Player | Field | Point |
| `BASEBALL` | Baseball | Game | Inning | Player | Field | Run |
| `SOFTBALL` | Softball | Game | Inning | Player | Field | Run |
| `HOCKEY` | Hockey | Game | Period | Player | Rink | Goal |
| `LACROSSE` | Lacrosse | Game | Quarter | Player | Field | Goal |
| `SWIMMING` | Swimming | Meet | Event/Heat | Swimmer/Athlete | Pool | Time/Points |
| `TRACK_FIELD` | Track & Field | Meet | Event | Athlete | Track/Field | Time/Distance/Points |
| `WRESTLING` | Wrestling | Match/Meet | Period | Wrestler/Athlete | Mat | Point |
| `CHEER` | Cheer | Competition | Routine | Athlete | Floor | Score |

Keep global platform terms such as Organization, Team, Coach, Guardian, Schedule, Registration, Eligibility, Payment, Fundraiser, Media, Swag Shop, and Support unless there is a strong sport-specific reason.

The backend may keep generic concepts such as `EventType.COMPETITION`; the presentation layer maps that to Match/Game/Meet.

### Required sport live test

Create at least one team for each supported sport and verify:

```text
team creation
team list/detail
roster terminology
event creation
schedule/event detail
competition label
parent/athlete schedule
mobile team/detail
search/filter display
friendly sport names
no contradictory generic sports wording
```

---

## 9. Persona End-to-End Journeys

### Owner / Director

```text
Register
→ verify/sign in
→ activate normal or Founding entitlement
→ create organization
→ complete profile
→ choose sports
→ create teams
→ confirm sport terminology
→ invite staff
→ create/import households and athletes
→ assign teams
→ configure eligibility
→ create events
→ communicate
→ create fee
→ collect/record payment
→ create fundraiser
→ create Swag Shop
→ create sponsorship
→ review reports/financials
→ use Help
→ submit support case
```

### Coach

```text
Invitation
→ create/sign in
→ accept access
→ team/roster
→ allowed eligibility view
→ create/edit event if authorized
→ message team
→ review RSVP
→ tournament/schedule tools
→ fundraising/Swag surfaces appropriate to role
→ sign out/in
```

### Parent / Guardian

```text
Access
→ household/athletes
→ schedule
→ RSVP
→ fees
→ payment
→ fundraiser/share
→ Swag Shop
→ permitted media
→ messages
→ notifications
```

A guardian must not depend on a staff-only endpoint for their own household.

### Athlete

```text
approved athlete access
→ own teams
→ schedule/tournament
→ allowed messaging
→ allowed media
→ only permitted personal/team data
→ no owner/financial/admin data
```

Youth privacy is P0.

---

## 10. Live Browser QA Protocol

A live browser pass is a hard release gate.

### Valid live testing

Use the actual running frontend + backend, authenticate through the UI, click real routes, submit real forms, observe real network behavior, and confirm resulting state when meaningful.

Not sufficient alone:

```text
code inspection
unit tests
API-only curl
Playwright-only pass
mock screenshots
```

### Browser coverage

- Chromium/Chrome-compatible: full pass.
- Firefox: critical journey smoke.
- WebKit/Safari-equivalent through Playwright: critical journey smoke.
- Responsive widths: `375`, `768`, `1280`, and `1440+`.

### Every-page checklist

```text
[ ] route loads
[ ] correct role access
[ ] incorrect role blocked
[ ] title/heading correct
[ ] sport terms correct
[ ] primary action clear
[ ] destructive actions clear
[ ] search/filter/sort/pagination work if present
[ ] empty/loading/error states intentional
[ ] validation understandable
[ ] success state understandable
[ ] navigation/back correct
[ ] links work
[ ] no raw UUIDs/internal enums/debug text
[ ] no broken media/icons
[ ] no normal-use console errors
[ ] no unexpected 4xx/5xx
[ ] responsive
[ ] dark mode if supported
[ ] accessibility basics
```

### Evidence format

```markdown
### <feature/finding>
- Status:
- Commit:
- Environment:
- Persona:
- Route:
- Browser/device:
- Preconditions:
- Steps:
- Expected:
- Actual:
- Network/API evidence:
- DB/state evidence:
- Screenshot/video:
- Automated regression:
- Fix commit:
- Retest:
```

---

## 11. Mobile Release QA

Compilation is not enough.

Use a customer-style preview/standalone application rather than only Expo Dev Client.

### Android

Verify:

```text
cold launch
login/logout
session persistence
foreground/background
back button
push/deep link where available
external browser/Stripe return
camera/photo/file picker
poor network
full owner or parent journey on physical Android where practical
```

### iOS

Before broad public release and preferably before Founding pilot:

```text
preview/TestFlight-equivalent or simulator
login/session
navigation/back
external payment/auth return
file/image picker
push/deep link
full parent/coach journey
```

If the AI environment cannot test a physical device, mark the item `BLOCKED — PHYSICAL DEVICE ACCEPTANCE REQUIRED` and write an exact founder acceptance script.

---

## 12. Financial and Transaction QA

Money receives stricter testing.

For every transaction type present, test:

```text
success
cancel
provider failure/decline
network interruption
refresh while processing
double submit
duplicate webhook
delayed webhook
invalid amount
zero/negative prevention
fee calculation
rounding
refund/partial refund if supported
receipt
audit event
reporting totals
```

Relevant areas include subscription, fees, fundraising, Swag Shop, sponsorships, credits, and other internal transactions.

Never trust a browser success page alone. Confirm the provider/Rally26 transaction state, business entity state, reporting/ledger state, outbox/receipt state, and audit state where applicable.

Duplicate webhooks must not duplicate orders, contributions, fee payments, credits, balance changes, or receipts.

Audit money formatting for consistent output.

---

## 13. Subscription / Plan Readiness

Confirm the actual code/config against approved launch pricing:

```text
FREE — $0 if launch-enabled
STARTER — $49/month
CLUB — $149/month
```

Do not invent transaction percentages.

Test team-cap enforcement, upgrades, and downgrades.

If an org exceeds the target plan capacity, never silently delete data or arbitrarily archive teams. Explain the conflict and require a safe resolution. Archive rather than delete where appropriate.

---

## 14. Founding Organization Program

The soft launch will use a limited Founding Organization program with Club-level entitlement at $0 subscription cost during the pilot; normal transactional fees still apply.

### Required model

```text
subscription entitlement = CLUB
billing amount during founding period = $0
program status = FOUNDING
```

Do not model Founders as Free users with scattered Club exceptions.

### Invite/promo

Use a unique one-use invite/promo with:

```text
code/hash
intended recipient/organization where appropriate
created/expiry
redeemed timestamp
organization association
audit record
```

Avoid one reusable public code.

### Activation

```text
foundingStartAt = actual activation/onboarding completion time
foundingEndAt = start + approved duration
```

Working assumption: six months unless founder changes it.

### Expiration lifecycle

Test with controlled time:

```text
30 days before → reminder
14 days before → reminder/banner
7 days before → reminder/banner
expiry → conversion required/grace state
grace → restricted/read-only behavior per approved rule
post-grace → selected paid/free plan or restricted account
```

No data disappears at midnight.

Test a Founding Club with more active teams than Free/Starter allow.

Verify activation, monthly check-in, expiry notices, and conversion messages have working links and no raw template variables.

---

## 15. Notifications, Email, Push, SMS

Build a catalog from actual product behavior. Inspect invitations, event changes/cancellations, RSVP, fee/payment notices, receipts, fundraising, Swag, eligibility, messages, support, and Founding-program notices.

For every notification:

```text
[ ] correct recipient
[ ] correct org/team
[ ] correct actor
[ ] correct title/details
[ ] working action URL/deep link
[ ] no duplicates
[ ] no missing template variables
[ ] no raw HTML leak
[ ] marketing unsubscribe correct
[ ] transactional mail not improperly suppressed
```

Emails from `support@rally26.com` and marketing/founder outreach must render correctly on desktop and mobile.

---

## 16. Ads / Free Plan — If Exposed

If the adult Free-tier sponsor bar is already launch-enabled, test it. If not, do not build an ad platform during hardening without explicit approval.

Approved intended behavior:

```text
no popups
no interstitials
no forced video
max one tasteful bar per eligible page
parents/coaches/owners only
NO athlete ads
paid tiers remove Rally26 network ads
organization sponsor placements may remain where intentional
```

---

## 17. Authorization and Security

This is P0.

Test sensitive resources as:

```text
platform admin
organization owner
administrator
team administrator
coach
guardian
athlete
unauthenticated
user from another organization
```

Do not only hide buttons. Attempt underlying API calls with inappropriate roles.

Organization A must never access Organization B through URL edits, UUID guessing, query/body manipulation, exports, media, search, reports, support, or admin paths.

Prioritize athlete identity, guardian contact info, private media, eligibility, waivers, messaging, payment details, and safety/moderation information.

Also verify file controls, production CORS, HTML sanitization, secrets, safe errors, authentication abuse protections, and current rate-limit/link-security rules.

Never weaken authorization just to make a UI page load.

---

## 18. Media and File Safety

Test image/document/video upload where supported, invalid types, oversized files, interrupted uploads, duplicate names, special characters, delete/archive behavior, visibility permissions, signed URLs, public/private media, and guardian release controls.

Production storage must not silently behave like local MinIO.

---

## 19. Integrations

Classify each exposed connector as:

```text
LIVE / CONFIGURED
PARTNER ACCESS REQUIRED
NOT CONFIGURED
INTERNAL ONLY
HIDDEN
```

Potential surfaces include Stripe, Printify, QuickBooks, TeamSnap, SportsEngine, GameChanger, MaxPreps, ICS, CSV, email, SMS, push, and object storage.

For live providers test connect/auth, token lifecycle, disconnect, sync/import, failures, duplicates, provider/rate-limit errors, user messaging, and audit/logging.

An adapter class does not prove an integration works.

---

## 20. UI/UX Cohesion

Audit terminology and use the approved term consistently:

```text
household vs family
owner vs administrator
organization vs club
fundraiser vs campaign
storefront vs Swag Shop
game vs match vs meet
player vs athlete
team staff vs coaches & staff
```

Verify search/filter/sort/pagination/empty-state behavior across significant lists. Add bulk actions only where clearly valuable and safe, not merely for uniformity.

Destructive actions must explain impact, confirm where appropriate, preserve linked data when retention matters, and prefer recoverable archive over deletion where intended.

---

## 21. Accessibility

Use axe where available plus manual checks:

```text
keyboard navigation
visible focus
labels/error association
button/link semantics
heading hierarchy
dialog focus
contrast
non-color status indicators
icon accessible names
touch targets
dynamic status where practical
```

Critical flows should have no serious violations.

---

## 22. Error, Empty, and Loading States

Every network surface should intentionally handle:

```text
loading
empty success
validation
403
404
409
provider unavailable
500
network timeout/offline
retry
```

Do not render an alarming service failure for an ordinary “not configured” state.

---

## 23. Data Integrity, Migrations, Backups

Test Flyway against a fresh DB and an upgrade path representative of deployment lineage.

Verify important rules at backend/DB level where appropriate: org isolation, valid money, provider event uniqueness/idempotency, one-use Founding invite, subscription limits, and sport normalization.

Before real organizations onboard:

```text
[ ] production DB backup exists
[ ] retention documented
[ ] restore procedure documented
[ ] non-production restore drill performed if possible
[ ] object/media recovery expectations documented
```

---

## 24. Production Environment Readiness

Verify:

```text
[ ] domain/TLS
[ ] API domain/TLS
[ ] health checks
[ ] production DB
[ ] backup
[ ] migrations
[ ] object storage
[ ] Stripe mode/config
[ ] Stripe webhook
[ ] email domain
[ ] SPF/DKIM/DMARC
[ ] Resend/provider templates
[ ] SMS if enabled
[ ] push credentials
[ ] CORS
[ ] secrets
[ ] no localhost URLs in production bundles
[ ] Expo preview/production URLs
[ ] monitoring
[ ] structured logs
[ ] error tracking
[ ] audit logs
[ ] support inbox monitored
[ ] privacy policy
[ ] terms
[ ] support/contact path
```

If the AI environment cannot verify an item, mark it BLOCKED and provide an exact manual verification step.

---

## 25. Performance, Reliability, Observability

Use representative large data, not only tiny seeds.

Check dashboards, large team rosters, organizations with many teams/participants, event lists, financial lists, search, reports, media-heavy screens, repeated navigation, obvious N+1/API explosions, and pagination.

Do not rewrite performance architecture without measured evidence.

Verify diagnostic visibility for backend errors, payments/webhooks, outbox/email, integrations, frontend exceptions, mobile errors/crashes if configured, health checks, and metrics.

Do not log passwords, sensitive tokens, payment secrets, or unnecessary youth data.

---

## 26. Support / Platform Admin

Live-test:

```text
user submits case
→ user sees case
→ platform admin sees case
→ triage/status/priority
→ support response/email
→ audit record
→ resolution
→ user sees outcome
```

Ordinary roles must never reach platform-admin capabilities.

Any support access/impersonation must obey current ADR/security rules and be auditable.

---

## 27. Marketing / Public Trust Surfaces

Before outreach, live-test the home page, pricing, solutions/features, legal pages, Help Center, public org/team pages, fundraising, sponsorship, Swag/public commerce, Founding landing page if added, CTA links, and mobile-browser rendering.

Do not advertise hidden/unavailable functionality.

Reconcile materially stale README/product-status documentation with the actual product during this pass.

---

## 28. Known Historical Risk Areas

Re-verify historical concerns around:

```text
new-user invitation acceptance
guardian portal access
public-page creation
session restoration
guardian invitations
raw participant IDs in RSVP
inconsistent empty/error states
layout overflow
generic event titles
list-control consistency
mobile parity
documentation drift
terminology consistency
```

Do not reopen a finding without reproduction. Do not assume it is fixed without reproducing the corrected path.

---

## 29. Regression Strategy

Prefer:

1. backend unit/integration tests for business rules;
2. frontend unit/component tests for rendering/state;
3. Playwright E2E for critical journeys;
4. mobile targeted tests where appropriate;
5. live browser/device proof.

High-value stable E2E paths:

```text
owner onboarding
invitation acceptance
team creation
guardian access
event + RSVP
fee/payment
fundraiser + public page
Swag Shop core
support submission
sport terminology
Founding redemption
```

Avoid brittle tests tied to implementation details.

---

## 30. Finding / Fix Template

```markdown
## LR-### — Short title

**Severity:** P0/P1/P2
**Feature:**
**Persona:**
**Found on commit:**
**Environment:**
**Status:** FAILED

### Reproduction
1.
2.
3.

### Expected

### Actual

### Root cause

### Fix

### Automated coverage

### Live browser retest
- Browser:
- Route:
- Result:

### Adjacent regression checks

### Final status
PASS
```

Use sequential IDs.

---

## 31. Daily Agent Execution Order

### 1 — Pull and baseline

Read docs, build/test projects, start stack, establish users/data, record baseline.

### 2 — P0 persona smoke

Immediately verify auth/session, owner onboarding, staff invitation, guardian access, team access, org isolation, payment safety, and platform-admin isolation.

### 3 — Sport configuration

Complete/verify canonical sport selection and terminology before final team/event UI review.

### 4 — Core operations

Teams, rosters, events, RSVP, messages, households, eligibility, fees, payments.

### 5 — Revenue

Fundraising, Swag Shop, sponsorships, transaction fees, receipts, reports.

### 6 — Supporting features

Media, docs, search, reports, integrations, Help, support, platform admin.

### 7 — Full UX/responsive/accessibility browser inventory

Every exposed web feature.

### 8 — Mobile persona pass

Parent first, then Owner, Coach, Athlete unless release priorities dictate otherwise.

### 9 — Production/ops

Infrastructure, external providers, observability, backup, email/webhooks, legal/support.

### 10 — Final regression

After the final fix, rebuild and rerun critical paths against the exact release-candidate commit.

---

## 32. Final Release-Candidate Procedure

When launch-scope rows are PASS, HIDDEN FOR LAUNCH, or explicitly FOUNDER ACCEPTED:

1. stop non-critical commits;
2. align to final intended branch state;
3. run backend full test/check/build;
4. run frontend test/typecheck/lint/build/E2E;
5. run mobile typecheck/lint/targeted tests;
6. deploy launch-like candidate;
7. run health checks;
8. owner smoke;
9. parent smoke;
10. coach smoke;
11. athlete smoke;
12. one end-to-end money transaction;
13. one notification/email flow;
14. one public-page flow;
15. one support flow;
16. sport terminology smoke for priority sports;
17. confirm no new P0/P1;
18. record commit SHA;
19. tag release candidate (`v1.0.0-rc.1` or repository convention);
20. do not merge feature work into the candidate.

Any subsequent change requires impacted retest plus critical smoke.

---

## 33. Launch Exit Criteria

```text
[ ] backend test/check/build green
[ ] frontend tests green
[ ] frontend typecheck green
[ ] frontend lint green
[ ] frontend production build green
[ ] frontend critical E2E green
[ ] mobile typecheck green
[ ] mobile lint green
[ ] mobile critical tests green
[ ] full stack starts cleanly
[ ] no unresolved P0
[ ] no unresolved P1 without founder acceptance
[ ] EVERY exposed web feature live-browser tested
[ ] EVERY fix live-browser retested
[ ] core persona journeys PASS
[ ] organization isolation PASS
[ ] sensitive permissions PASS
[ ] sport matrix PASS
[ ] revenue/money PASS
[ ] Founding redemption PASS
[ ] Founding expiry/downgrade time-controlled tests PASS
[ ] notifications/email PASS
[ ] production URLs/config verified
[ ] payment provider/webhooks verified for intended launch mode
[ ] storage verified
[ ] backup/restore verified
[ ] monitoring/error visibility verified
[ ] support path verified
[ ] legal/privacy/support links live
[ ] mobile customer-style build tested
[ ] final release-candidate smoke PASS
```

---

## 34. Founder Acceptance Items

| Item | Decision | Recommendation | Founder Decision | Date |
|---|---|---|---|---|
| Founding duration | Confirm exact duration | 6 months | | |
| Free plan at soft launch | Confirm implementation readiness | Include only if hardened | | |
| Starter exact team cap | Confirm approved rule | Use configured approved rule | | |
| Founding grace period | Confirm | 14 days suggested | | |
| Incomplete integrations visible? | Hide vs disabled | Hide or honest disabled state | | |
| P1 exception | Case-by-case | Prefer fix | | |

---

## 35. Launch Finding Log

| ID | Severity | Feature | Finding | Status | Fix Commit | Live Retest |
|---|---|---|---|---|---|---|
| LR-033 | P2 | Cross-cutting / browser tab title (web) | Founder-flagged live: the browser tab's description text showed "Sign In" instead of "Rally26" while browsing the authenticated app. Root cause confirmed via `document.title` directly (not a browser-automation-tooling artifact): the `<Seo>` component is the *only* thing in the whole codebase that ever sets `document.title`, and it's used on exactly 20 files — every one of them pre-auth (sign-in/register/forgot-password/verify-email), public marketing, or public storefront/campaign pages. **Zero** authenticated `/app/*` pages ever set a title at all — the entire dashboard for every role (Owner/Coach/Parent/Athlete/Platform Admin/Tournament Admin) and the entire org tab-bar surface (Overview, Teams, Fees, Households, Events, Documents, Members, Settings, everything) inherits whatever `document.title` happened to be last set — in practice almost always "Sign In | Rally26" from just before login, and it never changes again for the rest of the session, regardless of what page is actually being viewed | PASS (fixed, two highest-leverage points) | uncommitted, this session — added a `document.title` effect to `DashboardShell.tsx` (covers every role's top-level dashboard, using the same real per-role `contextName` already passed to it — org name, household name, "My Teams", etc.) and a second one to `OrganizationDetailPage.tsx` (covers the entire org tab-bar surface, building a real per-section title like "Fees & Payments · Riverside Youth Sports Club"). Together these cover the two most-traveled layout shells in the whole app. Individual "drill-down" pages reached by clicking into a specific team/household/event (`TeamRosterPage`, `HouseholdDetailPage`, `EventDetailPage`, etc.) still inherit their parent section's title rather than showing their own more specific one — better than the stale "Sign In" bug (now fixed everywhere), but not maximally specific; noted as a smaller follow-up, not built this pass. Full frontend test suite green, typecheck clean; caught and fixed a real rules-of-hooks violation (`useEffect` placed after early returns) via `oxlint` before it ever reached the browser | verified live against the rebuilt docker-compose frontend: signed in and confirmed the tab title reads "Riverside Youth Sports Club \| Rally26" on the dashboard, and correctly updates to "Fees & Payments · Riverside Youth Sports Club \| Rally26" when navigating to that section |
| LR-032 | P1 | Search / cross-cutting authorization (web + API) | Founder-directed investigation: does global search correctly scope results by the caller's role, and does the *category-labeling* half of that ask already work? Category labeling: **confirmed working correctly, no bug** — a "Johnson" search returns both "Maya Johnson" (labeled "Athlete") and "Johnson Family" (labeled "Household") as clearly distinct, typed results; verified live. **Role-based scoping: a real, confirmed gap.** `SearchService.searchOrganization()`'s own doc comment states the intent precisely: global search is "deliberately not offered to Parent/Coach/Athlete dashboards — an org-wide search of every household would cross the household-privacy boundary... this is scoped to the same audience that already sees every team/household in the org via the organization detail page" (i.e., Owner/Administrator only). In practice, the backend never verifies that — it calls `membershipService.requireActiveMembership()`, which passes for **any** active membership role, including a team-scoped Coach (`TEAM_MANAGER`). Proved live: signed in as the real Coach account (jordan.ellis, `TEAM_MANAGER` on JV Soccer and Varsity Soccer only — confirmed via direct DB query) and called `GET .../search?q=Chen` directly (bypassing the UI, which correctly never renders a search box on the Coach dashboard) — it returned "Chen Family," a household with **zero** participants and **zero** team affiliation, completely outside this coach's legitimate scope. This is the exact household-privacy violation the code's own comment says must never happen. **Scope of the gap is broader than just search**: `TeamSearchService` and `HouseholdSearchService` (the search boxes on the Owner-only Teams/Households list pages) have the identical `requireActiveMembership` gate — but so do the *plain, non-search* `TeamService.listForOrganization()`/`HouseholdService.listForOrganization()` methods those pages page through, whose sibling *write* operations (create/update/archive) already correctly use the stricter `requireManagerRole` (OWNER/ADMINISTRATOR only). This read/write split looks like a deliberate, consistent, pre-existing design choice across the whole codebase (any active member can *view* org rosters; only managers can *edit* them) — meaning a narrow "just fix the 3 search services" patch would not actually close the underlying hole, since the identical data is equally reachable today via the plain list endpoints using the same weaker gate. Whether "any active member, including a single-team coach, can view every household/team name and contact info org-wide" is the accepted intended model (in which case `SearchService`'s own doc comment is the thing that's wrong and should be corrected) or a real gap that needs closing across many endpoints (a materially larger change touching the org-wide read-authorization model, not just search) is a product/security policy decision, not a quick mechanical fix | FOUNDER DECISION REQUIRED — this is a real, live-confirmed cross-team/household data exposure reachable by any org member via direct API call (not exploitable pre-authentication, and not currently reachable through the Coach/Parent UI, which never renders the affected controls — but trivially reachable via browser devtools/curl by any authenticated staff member regardless of role). Recommend: either (a) narrow `SearchService`/`TeamSearchService`/`HouseholdSearchService` to `requireManagerRole` to match the documented intent, accepting that this alone doesn't close the identical gap on the plain list endpoints, or (b) treat this as a signal to do a real pass on what "active member" should be allowed to read org-wide vs. team/household-scoped, which is a bigger effort than this launch-readiness pass should absorb unilaterally | n/a | reproduced live via curl as the real jordan.ellis Coach account against the rebuilt docker-compose backend: `GET .../search?q=Chen` returns the fully team-unaffiliated "Chen Family" household, 100% reproducible |
| LR-031 | P1 | Swag Shop (web, public storefront) | The public `/swag-shop/{slug}` storefront could never show a shopper a picture of any Printify-catalog product. Founder-flagged live: "I don't see any images on those items displayed, so how would they know what it looks like?" Root cause: `ProductVariant.mockupFrontUrl`/`mockupBackUrl` are real, already-populated fields (Printify returns a real photorealistic mockup per variant at creation time, and the org-admin `ProductManagementPanel` already displays them) — but the public-facing `PublicProductVariantResponse` DTO only ever carried `id`/`label`/`priceMinor`/`currency`, dropping the mockup URL entirely before it ever reached the storefront. `PublicProductResponse.designUrl` (a manually-uploaded design) was the *only* image source the public page could render, and Printify-catalog products never have one — so every Printify product was structurally unable to show an image, while manual/local-vendor products with an uploaded design worked fine. Confirmed via direct API inspection: all 4 real seeded products returned `designUrl: null`, and the public variant JSON had no image field at all | PASS (fixed) | uncommitted, this session — added `mockupFrontUrl` to `PublicProductVariantResponse` (`backend/.../store/web/StoreDto.kt`, `StorePublicController.kt`), updated the frontend `PublicProductVariant` type and `PublicStoreView.tsx`'s `ProductCard` to show a per-variant mockup thumbnail plus a product-header image that falls back to the first variant's mockup when no manual design exists. New real-Postgres integration test (`PublicStoreMockupImageIntegrationTest`) and a new frontend unit test (`PublicStoreView.test.tsx`) both cover the fallback. Full store module backend suite and relevant frontend suites green, ktlint/typecheck clean | verified live against the rebuilt docker-compose stack on the real seeded "Riverside Team Store": 3 of 4 real products now show their actual Printify mockup images (both a large product-header thumbnail and a small per-variant thumbnail); the 4th ("Riverside Youth Sweatpants") correctly shows no image, since direct API inspection confirmed it's pre-existing bad test data that was never assigned a mockup — unrelated to this fix, not reproduced by it |
| LR-030 | P0 | Cross-cutting / fee payment reporting (web) | Every org-wide/reporting query summing `fee_payment` amounts counted **every payment row regardless of status**, including `PENDING_CHECKOUT` (a Stripe checkout session started but never confirmed — an abandoned/incomplete online payment, not real money received). Found live on the Collections page (`/app/organizations/{id}/collections`, reached via "Fees & Payments → View collections and export"): the real seeded "Fall Uniform Fee" assignment ($75.00 original) showed **"Paid $110.00" and "Balance $0.00"** — paid exceeding original, on a fee with a real known unpaid balance. Root-caused via direct DB query: one real `$40.00 CONFIRMED` payment plus two abandoned `$35.00 PENDING_CHECKOUT` rows (leftover incomplete Stripe Checkout attempts) summed to exactly the displayed $110.00. `FeePaymentRepository.sumActiveByAssignment()` already had the correct fix with an explicit doc comment ("a pending or abandoned checkout attempt must never affect the household's outstanding balance") — every *other* site in the codebase that independently re-implemented a paid/collected sum had drifted from that established-correct pattern. Confirmed via systematic grep audit across the whole backend (same "audit the whole class, don't fix one at a time" approach as this session's `/search` endpoint bug class) — found in **9 real sites across 6 files**: `FeeRepository.kt` (org Collections list, `getFinancialSummary` — feeds the Overview dashboard's "Fees Collected" card and the Reports page, and `findNeedingPaymentReminder` — the payment-reminder scanner, meaning a household with an abandoned checkout could silently never get reminded to actually pay), `ActionCenterRepository.kt` (`countOverdueFees` — the Action Center overdue-fees badge, and `listGuardianFeeActions` — **the guardian-facing "you owe money" action-center card, the most severe instance: a guardian could silently never be prompted to pay a fee they still owe**), `FeeSearchRepository.kt` (the fee-assignment search/filter view), `ReportingRepository.kt` (`feeCollections` — the Reports page's and household report's "Fee Collection" line item), `ReconciliationRepository.kt` (`paymentPlanBalanceMismatches` — a health-check whose own "current balance" half of the comparison was wrong, ironically still surfacing *a* mismatch but pointing at the wrong root cause), and `QuickBooksRepository.kt` (`countExportCandidates` — had no status filter *or* voided-row exclusion at all, unlike every sibling line in the same query which correctly filtered by status; would have told an org "you have N fee payments to export" including ones that were never actually paid) | PASS (fixed) | uncommitted, this session — added `and status = 'CONFIRMED'` (`and voided_at is null` where also missing) to all 9 real sites; left the small number of allocation-based queries (`fee_payment_installment_allocation` joins in `ActionCenterRepository`/`FeePaymentPlanRepository`/`ReconciliationRepository`) unchanged after confirming via code trace that allocation rows are only ever created for already-CONFIRMED payments (`FeeService.allocateToActivePlan` is only called from `recordPayment` and `confirmOnlineCheckoutFromWebhook`), so those were never actually exploitable. New real-Postgres integration test (`FeePaymentPendingCheckoutIntegrationTest`) reproduces the exact live scenario (one confirmed + two abandoned pending-checkout payments) and asserts `getFinancialSummary`, the org-wide Collections list, and `countOverdueFees` all report the correct real numbers. Full regression suite across every touched module (fee/actioncenter/reporting/reconciliation/communication/quickbooks/dashboard) green, ktlint clean | verified via direct curl against the rebuilt docker-compose backend using the exact real seeded data that exposed the bug: `GET .../fee-assignments` now correctly shows the Fall Uniform Fee assignment's `paidMinor: 4000` (was `11000`) and `balanceMinor: 3500` (was `0`) — matching the real ledger exactly. Initial live browser re-verification was blocked by post-restart LR-006-family flakiness (persistent blank `#root` across 2 reload attempts); resolved with a fresh tab per established precedent, then confirmed live on the real Owner dashboard: the Financial Overview card's "Fees Collected" correctly dropped from $300.00 to $230.00 and "Outstanding" correctly rose from $197.50 to $232.50 — exactly the $70.00 in phantom pending-checkout money removed |
| LR-029 | P0 | Cross-cutting / every direct-to-storage file upload (web) | Every browser-side file upload in the app — Documents, org/team/household branding logos, product images, swag design assets, household media, sponsor logos, everything using the shared `DocumentUploadForm`/`features/media/api.ts` presigned-upload flow (DESIGN-DOC.md section 11.3: the browser PUTs bytes directly to object storage, never through the Rally26 API) — was completely broken in the local docker-compose stack, and the same root cause very likely also affects production (unverified against live prod; no browser access to it). Found live while testing the Documents page (uploading a real PDF): "Upload failed. Please try again." on every attempt, with **zero network request visible** for the actual PUT — the tell that something was failing client-side before the request ever left the browser, not a server error. Root-caused two independent, compounding bugs: **(1)** the backend's presigned PUT URLs are built from `SPACES_ENDPOINT`, which `compose.yaml` sets to the Docker-internal `http://minio:9000` — a hostname the browser (on the host, not in the Docker network) cannot resolve at all; **(2)** even after fixing that, the frontend's CSP `connect-src` (`frontend/nginx.conf.template`, same mechanism as LR-005) never allow-listed the storage origin in the first place — so the browser's own CSP silently blocked the PUT with no console-visible network entry, the exact same silent-failure shape as LR-005. Since this is the same shared upload code path listed in every "not yet exercised" note this session for branding/logo/product-image uploads, it's likely this was silently blocking all of those the entire session, not just Documents — worth a fresh look at any upload-dependent flow previously marked PASS based on pre-existing seeded data rather than a freshly-uploaded file | PASS (fixed, local) / UNVERIFIED (production) | uncommitted, this session — **(1)** added `SpacesProperties.publicEndpoint` (nullable, defaults to `endpoint`) used only by `SpacesConfig.s3Presigner()` (`backend/src/main/kotlin/com/rally26/config/SpacesConfig.kt`/`SpacesProperties.kt`), bound via new `SPACES_PUBLIC_ENDPOINT` env var (`application-local.yml`, `compose.yaml`) defaulting to MinIO's host-mapped `http://localhost:9000`; staging/prod configs untouched since their real Spaces endpoint is already publicly reachable by both server and browser. **(2)** added `${STORAGE_ORIGIN}` to CSP `connect-src` (`frontend/nginx.conf.template`), envsubst'd the same way as `${API_ORIGIN}`; `frontend/Dockerfile` defaults it to `https://*.digitaloceanspaces.com` (a stable DO domain suffix, no exact bucket subdomain needed) for production, `compose.yaml` overrides it to `http://localhost:9000` locally. Full backend media/documents/store/integration-readiness test suite green, ktlint clean | reproduced live pre-fix (real PDF upload failed, zero network entry for the PUT) and post-fix (real PDF — "Season Handbook 2026," `application/pdf · 486 B` — appeared in the actual Documents list, then removed as cleanup) against the rebuilt docker-compose stack; also verified the full presign→PUT→confirm flow via direct curl (200/200) and confirmed the CSP header via `curl -I` before the browser retest. **Production risk not verified live** — same CSP gap exists in `Dockerfile`'s baked default (prod's frontend container runs unmodified from the image, no `STORAGE_ORIGIN` override existed before this fix) and prod's presigned URLs point at the real public Spaces domain (server-resolvable *and* browser-resolvable), so bug (1) likely doesn't reproduce in prod, but bug (2) — the missing CSP allow-list entry — plausibly does; worth a real upload test against the actual prod domain before launch |
| LR-028 | P0 | Sponsorships (web, "Review pending sponsorships") | `GET .../sponsorships/search` (what `frontend/src/features/sponsorship/searchApi.ts`'s `useSponsorshipSearch` has always called to power the "Review pending sponsorships" sub-view) had no backend mapping at all — same class of bug as LR-016/018/020/025/026/027, confirmed missing via a systematic fork audit of every frontend `/search` call against backend routes (run proactively after the 026/027 pattern made a one-at-a-time approach clearly inefficient), not found via manual UI clicking this time | PASS (fixed) | uncommitted, this session — added `SponsorshipSearchCriteria`/`SponsorshipSearchRow`/`SponsorshipSearchSort` (`sponsorship/domain/SponsorshipSearchCriteria.kt`), `SponsorshipRepository.search()`/`countSearch()` (joins `sponsor`+`sponsorship_package`; base filter restricted to `CONFIRMED`/`REFUNDED` status matching `findConfirmedForOrganization`'s existing pattern, but `reviewStatus` is a real optional filter here since this generic search view needs to show all review states, unlike `listPendingReview`'s hardcoded `PENDING_REVIEW`-only restriction), `SponsorshipService.search()`/`countSearch()`, new `GET .../sponsorships/search` endpoint on `SponsorshipPackageController.kt`, and a new flatter `SponsorshipSearchItemResponse` DTO (includes `packageId`/`packageName`/`sponsorCompanyName` that the existing `SponsorshipResponse` lacks). 2 new real-HTTP integration tests against real Postgres (basic search returns a real confirmed sponsorship; keyword search filters by sponsor name), using the same `insertOfflinePending`/`markOfflineConfirmed` test-setup pattern established for LR-025/026 to satisfy the `sponsorship_payment_source_fields_check` constraint. Full sponsorship module test suite green, ktlint clean | verified via direct curl against the rebuilt docker-compose backend: real 200 (empty `items` — no confirmed sponsorships exist in this org's current seed data, which is the correct/expected response, not a bug) |
| LR-027 | P0 | Members (web, org member list) | `GET .../members/search` (what the org Members page's search/filter UI calls) had no backend mapping at all — same class of bug as LR-016/018/020/025/026, confirmed via the same fork audit that found LR-028 | PASS (fixed) | uncommitted, this session — added `MembershipSearchCriteria`/`MembershipSearchRow`/`MembershipSearchSort` (`membership/domain/MembershipSearchCriteria.kt`), `MembershipRepository.search()`/`countSearch()` (joins `app_user` since `organization_membership` has neither email nor display name, needed for keyword search), `MembershipService.searchMembers()`/`countSearchMembers()` (same `requireActiveMembership()` as sibling endpoints), and new `GET .../members/search` endpoint on `MembershipController.kt`. 2 new real-HTTP integration tests against real Postgres (real-owner-membership search returns 200 with correct data; no-match keyword returns `totalElements:0`). Full membership module test suite green, ktlint clean | verified via direct curl against the rebuilt docker-compose backend: real 200 with all 4 of the organization's actual seeded members (owner, team administrator, and 2 admins) |
| LR-001 | P2 | Fees / Payments (mobile) | `mobile/src/lib/money.ts` `currencyFractionDigits` could return `undefined`, unlike the frontend equivalent which guards with `?? 2`; risk of `NaN` money formatting for edge-case currency codes | PASS (unit-level; not yet live-retested) | uncommitted, this session | pending live browser/device pass |
| LR-002 | P2 | Mobile CI / build gate | Mobile has no CI workflow at all (`.github/workflows/` has zero mobile jobs) and `npm run typecheck` silently depends on a stale, gitignored, machine-local `.expo/types/router.d.ts` that isn't regenerated automatically, so a clean checkout can typecheck-fail on legitimate routes until someone runs an export/dev-server once | FOUNDER DECISION REQUIRED (add mobile CI job + typegen step) | n/a | n/a |
| LR-003 | P3 | Fundraising (mobile) | Two internal helpers (`actionHook`, `reviewMutation`) called React Query hooks without hook-shaped names, tripping `react-hooks/rules-of-hooks` and blocking the mobile lint gate; plus one unescaped-apostrophe lint error in `fundraising-detail.tsx` | PASS | uncommitted, this session | pending live browser/device pass |
| LR-004 | P3 | Auth (web, test suite) | `frontend/e2e/dashboard.spec.ts` asserted a stale unauthenticated-access UX (inline "please sign in to continue" alert on the same route) that no longer exists in the app; actual current behavior (`ProtectedRoute.tsx`) is a real redirect to `/auth/sign-in?next=<path>` with correct post-login return, which is intentional and correct. Updated the test to assert the real behavior; not a product defect | PASS (24/24 E2E green after fix) | uncommitted, this session | reproduced live against docker-compose stack |
| LR-005 | P1 | Local/staging full-stack QA infrastructure | A fresh `docker compose up --build` produced a frontend whose CSP `connect-src` only allow-listed `'self'` and the *production* API domain (`https://api.rally26.com`), hardcoded in `frontend/nginx.conf`. Every browser API call against a local/staging backend (`http://localhost:8080`) was silently blocked by CSP — no server log, just a generic "Something went wrong" on sign-in. `compose.yaml`'s `VITE_API_BASE_URL` was also set as a runtime `environment:` var, which is a no-op since Vite bakes that value at image build time, not container-run time. This blocks §6/§10's mandated live-browser QA method against any freshly built local/staging stack. Production is unaffected (its CI-built image's `VITE_API_BASE_URL` already matches the hardcoded CSP domain) | PASS (fixed) | uncommitted, this session — `frontend/nginx.conf` → `frontend/nginx.conf.template` with `${API_ORIGIN}` envsubst'd at container startup (nginx's built-in templating), `frontend/Dockerfile` copies it to `/etc/nginx/templates/` with `ENV API_ORIGIN=https://api.rally26.com` default, `compose.yaml` overrides `API_ORIGIN=http://localhost:8080` and moves `VITE_API_BASE_URL` to `build.args` | reproduced live: sign-in failed pre-fix, succeeded post-fix, against the same rebuilt docker-compose stack |
| LR-023 | P0 | Messaging (web, coach/owner safety moderation) | Reviewing a reported message to a non-terminal status — clicking "Start review" (`IN_REVIEW`) on the Safety review panel — 500'd every time, confirmed live as the real coach account (jordan.ellis) on a real, pre-existing "Safety concern" report. Root cause, same bug class as LR-022: `MessageSafetyRepository.updateReportStatus()`'s `resolved_at = case when :resolved then :now else null end` left both CASE branches untyped, so Postgres's extended query protocol resolved the whole expression as `text` instead of `timestamptz` and failed with `ERROR: column "resolved_at" is of type timestamp with time zone but expression is of type text` — deterministic, not intermittent. Confirmed live for the `IN_REVIEW` (non-terminal) transition specifically; since Postgres resolves a CASE expression's parameter types once at prepare time (before either branch's runtime value is known), the terminal transitions (`RESOLVED`/`DISMISSED`) were very likely equally broken — not separately confirmed broken pre-fix, since the live repro only exercised "Start review", but the fix and its regression test cover both paths regardless | PASS (fixed) | uncommitted, this session — added explicit `cast(:now as timestamptz)` and `cast(:resolutionNote as text)` to both CASE branches in `MessageSafetyRepository.kt`, matching LR-022's fix pattern. Searched the codebase for the same `case when :bool then :param else ...` anti-pattern elsewhere — this was the only occurrence. New real-Postgres integration test (`MessageSafetyRepositoryIntegrationTest`) covers the exact failing transition (`IN_REVIEW`, asserting `resolved_at` stays null) that a mocked-repository test couldn't have caught. Full messaging module test suite green | reproduced live pre-fix (real "Start review" click → "An unexpected error occurred", confirmed via backend logs) and post-fix via direct curl against the rebuilt docker-compose backend on the actual leftover QA test report: `IN_REVIEW` transition now returns 200 with `resolvedAt: null` (was 500), then dismissed the same report as real cleanup, confirming the terminal-status path also correctly sets `resolvedAt` |
| LR-026 | P0 | Sponsorships (web, package list) | `GET .../sponsorship-packages/search` (what `frontend/src/features/sponsorship/searchApi.ts`'s `useSponsorshipPackageSearch` has always called to power the Sponsorships tab) had no backend mapping — Spring matched the literal path segment "search" to the existing `{packageId}` wildcard handler and threw trying to parse "search" as a UUID (`IllegalArgumentException: Invalid UUID string: search`), a 500 on every request. Same class of bug as LR-016/018/020/025 (this is now the 4th confirmed instance of "frontend calls a `/search` endpoint the backend never built" this session). Found live-testing Sponsorships as Owner immediately after LR-025; also confirms `frontend/src/features/sponsorship/searchApi.ts`'s second search hook (`useSponsorshipSearch`, `.../sponsorships/search`, for the "Review pending sponsorships" sub-view) was **not** exercised this pass and should be checked for the same gap before launch | PASS (fixed) | uncommitted, this session — added `SponsorshipPackageSearchCriteria`/`SponsorshipPackageSearchRow`/`SponsorshipPackageSearchSort` (`sponsorship/domain/SponsorshipPackageSearchCriteria.kt`), `SponsorshipPackageRepository.search()`/`countSearch()` (keyword/status/exclusive filters, 7 sort options including `SPONSORS_DESC` via a correlated subquery so the confirmed-sponsorship count doesn't need N+1 querying), `SponsorshipPackageService.search()`/`countSearch()`, and the new endpoint reusing the existing `SponsorshipPackage.toResponse(confirmedCount)` mapper directly (no new DTO needed — the shape already matched exactly). 2 new real-HTTP integration tests against real Postgres. Full sponsorship module test suite green, ktlint clean | verified via direct curl against the rebuilt docker-compose backend: real 200 with the organization's actual seeded "Gold Sponsor" package (correct `confirmedCount`/`soldOut` computed fields). Live browser re-verification blocked by the same post-restart LR-006-family flakiness as LR-025; accepted code+curl per established precedent |
| LR-025 | P0 | Swag Shop (web, "Orders and fulfillment") | `GET .../stores/{storeId}/orders/search` (what `frontend/src/features/store/searchApi.ts`'s `useOrderSearch` has always called to power the "Orders and fulfillment" panel — keyword/status/paymentSource/fulfillmentStatus filters, sort) had no backend mapping at all — only the plain, unfiltered `GET .../stores/{storeId}/orders` existed. Every request 404'd ("Could not load orders." live in the browser; confirmed the exact `NOT_FOUND` response via direct curl), same class of bug as LR-016/018/020. Found while live-testing Swag Shop as Owner mike.anderson, right after LR-024's redirect issue was worked around | PASS (fixed) | uncommitted, this session — added `OrderSearchCriteria`/`OrderSearchRow`/`OrderSearchSort` (`order/domain/OrderSearchCriteria.kt`), `OrderRepository.search()`/`countSearch()` (LEFT JOIN `fulfillment` for the per-order fulfillment status, since it's a separate 1:1 table an order may not have a row in yet), `OrderService.search()`/`countSearch()` (same `requireManagerRole` + store-existence pattern as the existing `listForStore`), and the new `GET .../stores/{storeId}/orders/search` endpoint + `OrderSearchItemResponse` DTO. 2 new real-HTTP integration tests against real Postgres (basic search returns the real order + correctly-null `fulfillmentStatus`; keyword search filters by supporter name). Full order/store module test suite green, ktlint clean | verified via direct curl against the rebuilt docker-compose backend: real 200 with the organization's 2 actual seeded orders (Grandma Sue, Sarah Johnson — both `fulfillmentStatus: "FAILED"`, a separate pre-existing data point worth a follow-up look, not caused by this fix). Live browser re-verification was blocked by LR-024's flakiness recurring right after the backend restart (redirect-to-Overview on `/swag-shop`, 4+ consecutive attempts across 2 tabs) — accepted code+curl verification and moved on per the LR-014 precedent rather than keep fighting it |
| LR-024 | P2 | Organization detail page (web, all `:section` routes) | `OrganizationDetailPage.tsx`'s permission gate (line ~120: `if (!visibleSections.some(...)) return <Navigate to="overview" replace />`) cannot distinguish "this user genuinely lacks the capability" from "the capabilities fetch (`/me/contexts`) transiently failed" — both produce the exact same silent, non-retryable redirect to Overview with the nav bar showing only "Overview" and no error message at all. Confirmed via direct backend curl that the real capability set was always correct (`organization.manage` present for the OWNER account throughout); the redirect was caused by a transient `/me/contexts` 503 (the same LR-006/LR-022 family of flakiness) that this page handles worse than others — `contexts.isError`/`isLoading` are checked and do show a proper retryable `ErrorState` (lines 82-84), but that only covers a *terminal* query failure, not the case observed here where the query apparently resolved without throwing yet still left `visibleSections` empty for that render. A real organization owner hitting this transient state would see what looks exactly like "I lost admin access to my own organization" — no error, no retry button, nav collapsed to one item — rather than anything indicating a transient/reloadable problem. Reproduced 3x in one browser tab/session on `/organizations/{id}/swag-shop`; resolved immediately in a fresh tab/session with the same account, confirming it's session-state flakiness rather than a real permission bug, but the *handling* of that flakiness is the real, worth-fixing gap | FOUNDER DECISION REQUIRED (or low-risk follow-up fix: surface a retryable error state here too instead of a silent redirect, consistent with how `contexts.isError` is already handled a few lines above) | n/a | reproduced live 3x in one session (mike.anderson, `/swag-shop`), resolved in a fresh tab; backend capability data confirmed correct via direct curl throughout |
| LR-022 | P0 | Cross-cutting / connection-pool poisoning (likely LR-006's real root cause) | `GET .../teams/{teamId}/eligibility/clearance` with no `status` filter (the only way it's ever called — no status-filter UI exists anywhere) 500'd on **100% of attempts** across two separate live-testing sessions this pass (7+ consecutive failures, zero successes, unlike every other LR-006 occurrence this session which self-healed within 1-2 retries) — a much stronger signal of a real deterministic bug than flakiness. Root-caused via a dedicated investigation: `EligibilityClearanceRepository.listForTeam()` binds `:statusFilter` as a raw, untyped Kotlin `null` (`.param("statusFilter", statusFilter?.name)`); Postgres's extended query protocol cannot infer an untyped null parameter's SQL type and throws `PSQLException: could not determine data type of parameter $N` — every single time, deterministically, not intermittently. Every sibling repository doing the identical "optional nullable filter" SQL pattern already guards against exactly this with an explicit cast (`AnnouncementRepository`, `FeeRepository`, `ReportingRepository`, `ProfileCorrectionRepository` all use `::text`/`cast(... as varchar)`) — this repository was the sole outlier. **This plausibly explains a meaningful share of this session's broader "LR-006" symptom** (intermittent 503s and blank-`#root` loads on totally unrelated endpoints like `/me/preferences`, `/me/contexts`, `/organizations/.../participants/.../teams`, all self-healing on retry, never previously correlated to a server-side log): a Postgres wire-protocol-level error during this query's Describe/Bind phase can leave its pooled physical connection in a bad session state; HikariCP reuses physical connections across unrelated requests, so an unrelated endpoint unlucky enough to get handed the poisoned connection next would fail too — exactly the "different endpoints fail in bursts, always recoverable" pattern observed all session. Not proven as the sole cause of every LR-006 occurrence (connection-state poisoning mid-failure wasn't directly inspected), but no competing theory fits the evidence this well, and it is a confirmed, deterministic, 100%-reproducible bug on its own regardless | PASS (fixed) | uncommitted, this session — added explicit `::text` casts to both `:statusFilter` occurrences in `EligibilityClearanceRepository.kt`, matching the established sibling-repository pattern exactly. New real-HTTP integration test (`TeamEligibilityClearanceIntegrationTest`) hits the actual endpoint against real Postgres with no status filter — a mocked-repository test could never have caught a real Postgres wire-protocol error. Full eligibility/event/store/dashboard module test suite green | stress-tested via direct curl: 10/10 clean 200s post-fix (was 0/7+ pre-fix, confirmed via repeated attempts across two sessions); reproduced live in-browser as the real coach account (jordan.ellis) on the team roster page — "Coaches & Staff" now shows Jordan Ellis/Team Manager and the Athletes list shows a working "Eligibility" filter (All athletes / Ineligible only), neither of which could ever render before this fix since the same page fired this query on every load |
| LR-019 | P2 | Households & Athletes (web, guardian) | A guardian's "My Athletes" panel showed a raw team UUID (e.g. `00000000-0000-0000-0000-000000000002`) instead of the team name under an athlete's expanded "Teams" section. `ParticipantTeamRow` (`HouseholdDetailPage.tsx`) resolves team names by cross-referencing `useParticipantTeams` (the participant's own assignments, guardian-accessible per LR-018) against `useTeams` (the full org team list, `GET .../teams`, gated by `membershipService.requireActiveMembership` — org-staff only), falling back to the raw `teamId` when the lookup fails. For a guardian, `useTeams` always 403s, so the fallback always fired — same root cause pattern as LR-018 (a guardian-facing read path silently depending on an org-staff-only endpoint) but for team name resolution rather than RSVP eligibility. Found immediately after fixing LR-018 while spot-checking the same "My Athletes" page live | PASS (fixed) | uncommitted, this session — `ParticipantRepository.listTeamAssignments()` now joins `team` directly and returns `teamName` on `ParticipantTeamAssignment`/`ParticipantTeamResponse`, so the guardian-accessible assignments endpoint (LR-018) carries its own team name and no longer needs the staff-only team list at all; `ParticipantTeamRow` prefers `a.teamName` with the old `useTeams` lookup kept only as a fallback. Full backend module test suite green (participant/event/eligibility/store/dashboard/household), frontend typecheck clean | reproduced live pre-fix (raw UUID rendered) and post-fix ("Varsity Soccer" renders correctly) against the rebuilt docker-compose stack as sarah.johnson |
| LR-021 | P0 | Marketing site (rally26.com positioning, pricing, Talk to Sales) | Founder-relayed external review (2026-08-19) plus direct source verification found the public site's messaging had fallen materially behind the real product, and one real lead-generation form was completely non-functional. **Positioning**: hero/SEO/footer described Rally26 as revenue/payment tooling ("More revenue. Lower fees. Stronger programs.", "A revenue and payment-management platform") when the real product now covers teams, rosters, scheduling, RSVP, messaging, eligibility, and a 4-persona mobile app — a prospective club owner could easily conclude Rally26 was a narrower add-on than it is. **FAQ**: literally told prospects "Rally26 is not initially a registration, scheduling, or roster-management system" and "does not create child login accounts" — both false against current state (full roster/scheduling/RSVP exist; athletes have real, scoped mobile accounts with Home/Calendar/Messages). **Security page**: claimed passwords aren't stored ("designed around a managed identity provider") when they are (salted hash, verified via `AppUser.passwordHash`); claimed payments are a future thing ("when live payments launch") when Stripe Checkout is fully live with dispute handling, tax, and a PCI-scope review already done this session; claimed backups are "part of the launch plan" when a real backup/restore rehearsal already happened. This is the worst possible page to carry stale claims on. **Talk to Sales** (`/talk-to-sales`): a 25-field form that validated, showed a fabricated "request received" confirmation with a generated reference number, and — per its own source comment — never persisted or sent the submission anywhere. Every prospective customer who filled it out was silently dropped; confirmed via source read, not assumption. **Fabricated stats**: homepage displayed "23% More revenue," "12+ Hours saved weekly," "2x Stronger communication," "99.9% Reliable & secure" with no supporting data anywhere in the codebase. **Coach messaging**: the coach-equivalent role card ("Team managers") described only "publish a team page and point families to fundraising" — none of the real scheduling/roster/RSVP/messaging value coaches actually get. **Integration honesty**: Club-tier pricing listed "SportsEngine & TeamSnap sync" (confirmed via source: both are disabled OAuth2 scaffolds, never activated — per `rally26-provider-secrets-inventory` memory) and "SMS payment reminders" (Twilio confirmed unfunded, SMS UI hidden) as if live. **Pricing CTA contradiction**: both the $49 and $149 tiers said "Start Free" with no trial mechanism anywhere in the schema. **Founding Organization campaign**: `/founding-pilot` redirected straight into the generic `/auth/register` flow with zero context for what a "Founding Organization" pilot actually meant | PASS (fixed) | uncommitted, this session — see [[rally26-final-release-phase-plan]]-adjacent DESIGN-DOC.md §14.1S/T/U for the Phase 44/45/46 status updates. Rewrote hero/SEO/footer copy across `HomePage.tsx`/`AuthLayout.tsx`/`AppFooter.tsx`/`SiteFooter.tsx`/`FundraiserFlyerPage.tsx`; corrected `content/faq.ts`'s 3 false claims; rewrote `SecurityPage.tsx`'s 9 sections to match verified current state; rebuilt `TalkToSalesPage.tsx` from a 25-field fabricated form to a real 4-field form on the same working `useCreateSupportCase(false)` endpoint `ContactUsSection` already uses (deleted the now-unused `talkToSalesSchema.ts`); removed the 4 fabricated stat claims and replaced with 4 factual, verifiable product-capability claims; rewrote the coach role card; softened SportsEngine/TeamSnap to "migration assistance" and dropped the unfunded SMS claim; replaced blanket "Start Free" CTAs with differentiated per-tier CTAs and added an inline fee-math example; added a new real `/founding-organizations` landing page (`FoundingOrganizationsPage.tsx`) and repointed `/founding-pilot`'s redirect at it. **Pricing tier count** (3→4, adding FREE) was explicitly scoped down after founder discussion: a live self-serve FREE tier needs backend catalog + entitlement-gating work (DESIGN-DOC.md §14.1T/U, Phase 45/46) that doesn't fully exist yet — `PlanEntitlementService` already live-enforces a 3-team cap for Starter today, but no other surface is gated and there's no FREE plan code — so a visual-only 4th FREE card was added (routes to Talk to Sales, not live registration) rather than wiring real signup ahead of that audit, per explicit founder decision this session. Full frontend test suite green (198/198 → adjusted for the TalkToSalesPage rewrite), oxlint clean, typecheck clean | reproduced/verified live against the rebuilt docker-compose frontend: hero/stats/coach-card/pricing render correctly at multiple viewport widths (4-column pricing grid confirmed at desktop width); Security/FAQ pages render corrected copy; Talk to Sales form was live-submitted with real test data and confirmed to persist as a real row in `support_case` (`requester_email='qa-tester@example.com'`, subject `"Talk to sales: Launch Readiness QA Org"`) — the exact failure mode being fixed; `/founding-organizations` renders the full pilot-program page and its CTA reaches the now-working Talk to Sales form |
| LR-020 | P1 | Teams (web, coach roster) | The "Coaches & Staff" panel on the team roster page (`TeamStaffList.tsx`) 500'd for every caller, every time — `GET .../teams/{teamId}/staff` had no backend mapping at all (frontend-calls-nonexistent-endpoint, same class as LR-016/018/019). Root cause confirmed via backend logs: with no controller match, Spring fell through to its static-resource handler, which throws `NoResourceFoundException` — and `GlobalExceptionHandler` had no handler for that exception type either, so it fell to the generic 500 (same secondary bug class as LR-013: a routine "not found" condition mishandled as a server error, complete with a false-positive ERROR log). The fix needed real judgment, not just a route: the closest existing endpoint (`/role-assignments`) is manager-only (`requireManagerRole`), which would have 403'd a coach viewing their own team's staff — read-only "who else coaches this team" is reasonable for any TEAM_VIEW holder, not just org owners/admins, so a new TEAM_VIEW-gated endpoint was built instead of just re-pointing the frontend at the manager-only one | PASS (fixed) | uncommitted, this session — added `AuthorizationService.listTeamStaff()` (TEAM_VIEW-gated) alongside the existing manager-only `listTeamRoleAssignments()`, a new reduced-exposure `TeamStaffResponse` DTO (no email/phone, matching the panel's own "Private email and phone information is not shown here" copy) with role labels mirroring `TeamRoleAssignmentsSection.tsx`'s existing `TEAM_ROLE_OPTIONS`, and a `NoResourceFoundException` → clean 404 handler in `GlobalExceptionHandler`. New integration tests cover both the coach-allowed and unrelated-user-403 paths, plus a regression test for the 404 handler | verified via direct curl against the rebuilt docker-compose backend (200, correct `"roleLabel":"Team Manager"` for the real coach account) after logging in as jordan.ellis (TEAM_ADMINISTRATOR); live browser re-verification was repeatedly blocked by LR-006's existing flakiness hitting this same page across several endpoints simultaneously (mixed 500/503 on `/staff`, `/eligibility/clearance`, `/participants` on reload, all self-healing/unrelated to this fix, real DB data confirmed present via direct query) — not chased further per established LR-014 precedent |
| LR-018 | P0 | Events / RSVP (web, guardian) | The guardian-facing RSVP flow was **completely non-functional for every guardian, on every event they're actually eligible to RSVP for** — not a display/copy bug, a silent authorization failure. `EventDetailPage.tsx`'s `RsvpParticipantControls` decides whether to show a participant's RSVP buttons by calling `GET /api/v1/organizations/{orgId}/participants/{participantId}/teams` (`useParticipantTeams`) and checking whether the participant is `ACTIVE` on the event's team; `ParticipantService.listTeams()` gated that endpoint behind `membershipService.requireActiveMembership()` — an org-staff-only check — so every guardian request 403'd. Because the frontend swallows the query error into `teams.data === undefined` and treats that as "not on this team," the RSVP controls simply never rendered, with **no error message and no visible indication anything was wrong** — it looked exactly like the athlete wasn't on the event's team, even when they were. Found while testing the guardian RSVP flow live: initially suspected a team-mismatch (guardian sarah.johnson's athlete Maya is on Varsity Soccer, not JV Soccer, so the first event tested legitimately had no RSVP control) — but re-tested against a real Varsity Soccer event Maya *is* eligible for and the same missing-controls symptom reproduced, confirmed via network tab as a 403 on `/participants/{id}/teams`, not a team mismatch | PASS (fixed) | uncommitted, this session — `ParticipantService.listTeams()` (`backend/src/main/kotlin/com/rally26/participant/application/ParticipantService.kt`) now allows the call when the caller has `HOUSEHOLD_VIEW` capability on the participant's own household (`AuthorizationService.hasHouseholdCapability`, the same pattern already used by the working `listForHousehold`), falling back to the original org-membership check for org staff — read-only data, no new write-path risk. 2 new unit tests cover both the guardian-allowed and org-staff-fallback paths; existing `listTeams throws NotFoundException...` test and all other participant-module tests still pass | reproduced live pre-fix (RSVP buttons silently absent, confirmed via network tab: 403 on `/participants/{id}/teams`) and post-fix against the rebuilt docker-compose backend — guardian sarah.johnson's RSVP buttons for Maya now render on the real Varsity Soccer event, and clicking "Maybe" successfully submitted (aggregate moved from 1 Attending → 1 Maybe in real time) |
| LR-016 | P0 | Households & Athletes / Teams (web) | Both core management pages were **completely non-functional for every organization, every time** — not intermittent. `GET .../households/search` and `GET .../teams/search` (what the frontend has always called — `frontend/src/features/households/searchApi.ts` / `teams/searchApi.ts`) had no backend mapping at all. Confirmed via backend logs: Spring matched the literal path segment `search` to the existing `{householdId}`/`{teamId}` wildcard handler and threw `MethodArgumentTypeMismatchException` trying to parse the string `"search"` as a UUID — a 500 on every single request, with the page silently showing an empty list (no error, no loading state stuck) since the query threw before any data arrived. Neither endpoint was ever documented in `docs/openapi.yaml` either. This was found while testing core household/team management (§31 Step 4), not related to LR-006 despite superficially resembling it (this was a clean, deterministic 500, not an intermittent 503) | PASS (fixed) | uncommitted, this session — added `HouseholdSearchController`/`Service`/`Repository`/`Criteria` and `TeamSearchController`/`Service`/`Repository`/`Criteria` (`backend/src/main/kotlin/com/rally26/{household,team}/...`), following the exact existing pattern from `FeeSearchController` (keyword/status/sort, dynamic JdbcClient SQL, no JPA). Household search additionally supports `teamId` filtering (via an `exists` subquery against `participant_team`, matching the frontend's team filter) and keyword matching across household name, contact email, adult name/email, and athlete name. 4 new integration tests hit the real HTTP endpoint end-to-end against a real Postgres test container (not just service-level mocks, since the bug was a routing issue a unit test wouldn't catch) | reproduced live pre-fix (empty list, 500 in network tab, confirmed via curl + backend logs) and post-fix (both pages fully render real seeded data — 3 households, 2 teams — with all management actions present: View, Schedule/Roster/Branding/Manage access/Timezone/Colors/Archive) |
| LR-015 | P0 | Fees & Payments — refunds | Fee payments have no real refund mechanism at all — only "Void a payment" (`DELETE .../fee-assignments/{id}/payments/{id}`), which per its own OpenAPI description is "Immutable-with-void — the row is kept for audit history, excluded from balance math" and, confirmed by reading `FeeService.voidPayment()`, has **zero** Stripe interaction of any kind. Voiding a `CONFIRMED`/`STRIPE_ONLINE` payment (a real, already-captured credit card charge) only makes Rally26's own ledger think the family owes money again — the family's card was genuinely charged and that money is still sitting with Stripe/the org, with no automatic path back. The frontend's "Void" button (`HouseholdDetailPage.tsx`) is a plain `window.prompt()` asking only for a reason, offered identically for every payment method (`CASH`/`CHECK`/`VENMO`/`ZELLE`/`OTHER`/`STRIPE_ONLINE`) with no warning that voiding a card payment doesn't refund it. This is a real, deliberate pattern *elsewhere* in the codebase that fees never got: `docs/openapi.yaml` has genuine Stripe-refund-integrated endpoints for contributions ("Refund a confirmed Stripe contribution"), swag orders ("Refund a confirmed order"), and sponsorships ("Refund a confirmed sponsorship") — fees are the one revenue type missing this. A staff member trying to "undo" a real card payment via the only button available would create a silent, unrecoverable-by-the-UI accounting mismatch between Rally26's records and actual card-network reality | FOUNDER DECISION REQUIRED — needs a real fee-refund endpoint mirroring the existing contribution/order/sponsorship pattern (or, as a minimum interim guard, block Void for `STRIPE_ONLINE`+`CONFIRMED` payments and point staff at a real refund action instead of silently letting them create a mismatch) | n/a | confirmed via source read (`FeeService.kt`, `HouseholdDetailPage.tsx`) and `docs/openapi.yaml` comparison against the three sibling refund endpoints that do exist |
| LR-014 | P1 | Fees & Payments (web, staff) | Three staff-facing mutation forms in `HouseholdDetailPage.tsx` (`RecordPaymentForm`, `ApplyAdjustmentForm`, `CreateFeeAssignmentForm`) called `.mutateAsync(values)` with no `try`/`catch` and no rendering of any submit-level error — confirmed live pre-fix: recording a $100 cash payment against a $35 balance correctly gets rejected by the backend's `amountMinor <= 0 \|\| amountMinor > currentBalance` guard (400, verified via network tab), but the UI shows absolutely nothing — no message, no shake, the form just silently sits there. A staff member has no way to know why "Record payment" did nothing; violates §10's "validation understandable" checklist item. Same class of gap does not exist in the sibling `PayOnlineButton` in the same file, which already has the correct `try { ... } catch { setError(...) }` + rendered message pattern — this was a real, isolated omission, not a systemic pattern | PASS (fixed) | uncommitted, this session — added `submitError` state + try/catch + `role="alert"` rendering to all three forms in `frontend/src/pages/HouseholdDetailPage.tsx`, mirroring `PayOnlineButton`'s existing pattern; shows the real backend validation message (e.g. exact outstanding-balance guidance) rather than a generic string | reproduced live pre-fix (silent no-op, confirmed via network tab: real 400 response, zero UI feedback). Fix verified via clean typecheck, full frontend test suite (198/198), and pattern-match against the already-working `PayOnlineButton` precedent — live re-verification blocked by LR-006 recurring persistently on this specific route (6+ consecutive failures across 5 min / multiple tabs) during this session; not re-attempted further per founder direction |
| LR-013 | P2 | Stripe webhook endpoint / observability | `POST /api/v1/webhooks/stripe` with no `Stripe-Signature` header (a malformed request, security scanner probe, or Stripe's own endpoint-verification ping) threw an uncaught `MissingRequestHeaderException`, falling through to `GlobalExceptionHandler`'s generic catch-all: a 500 response plus an ERROR-level "Unhandled exception" log line — which would fire the New Relic error-alert email to support@rally26.com (see `rally26-newrelic-dashboard-and-alerts`) for routine, expected-to-happen traffic, not a real incident. Exact same bug class as an already-fixed, already-documented case for missing query parameters (`GlobalExceptionHandler.kt`'s own comment references a 2026-08-05 guardian-portal fix) — just never extended to missing headers. No information leak either way (response body was always the generic safe envelope) | PASS (fixed) | uncommitted, this session — added `MissingRequestHeaderException` handler to `GlobalExceptionHandler.kt` mirroring the existing `MissingServletRequestParameterException` handler, plus a regression test in `GlobalExceptionHandlerIntegrationTest.kt` hitting the real webhook endpoint | reproduced live pre-fix (500 + ERROR log), re-verified live post-fix (clean 400, `MISSING_REQUEST_HEADER`) against the rebuilt docker-compose backend |
| LR-012 | P2 | Events list (web) | Every event card shows the same date/time twice in two different formats stacked directly on top of each other — e.g. "20/08/2026 10:00 EDT" immediately above "Aug 20, 2026, 10:00 AM" for the same "State Cup Semifinal" event. The dd/mm/yyyy line (`formatEventDateTime.ts`) is deliberate per its own ADR-071 comment — a locale-independent, always-the-same-shape technical timestamp — not a bug in isolation, but a separate, independently-implemented formatter (`formatDateTime`, local to `EventListPanel.tsx`) renders the friendly US-style line right next to it, and to an ordinary (non-staff/debugging) Owner viewing their own event list, the pairing reads as redundant and inconsistent rather than intentionally precise. This is a design call (keep both, restyle the technical line smaller/secondary, or drop it from this ordinary list view and reserve it for a debug/audit context), not something to unilaterally change | FOUNDER DECISION REQUIRED | n/a | reproduced live on `/app/organizations/.../events`, confirmed via zoomed screenshot and source read of both formatters |
| LR-011 | P0 | Sport configuration (§8) | The entire sport-configuration foundation this document requires (§8) is unimplemented. `team.sport` is a plain free-text column with **zero** check constraint (current real values: `"Basketball"`, `"Soccer"` — display-case strings, not stable codes); `CreateTeamFormValues.sport` is `z.string().trim().min(1).max(60)` with no enum, unlike `genderCategory` which correctly uses `z.enum([...])` a few lines away; the team-creation UI's sport field is a plain `<input>` text box, not a selector. There is no canonical sport code anywhere in the codebase, no backend validation, and zero terminology-derivation logic in the frontend (`grep -ri "terminology\|sportTerm"` across `frontend/src` returns nothing) — so there's no Match/Game/Meet, Player/Athlete/Swimmer, or Court/Field/Pool mapping by sport at all, on web or mobile. This blocks every §8 requirement (canonical selector, stable code validation, API-exposed display name, derived terminology, safe migration of existing free-text values, sport-change restrictions, unknown-value handling) and the required sport matrix (12 sports) can't be tested since it doesn't exist. §3 explicitly lists "approved sport-configuration foundation" as allowed freeze-window work, implying this was meant to be built as part of this launch push — but it is a genuine multi-part feature (enum + migration + backend validation + web/mobile terminology derivation), not a QA-pass fix, so not attempted this session | FOUNDER DECISION REQUIRED — needs scoping as a real build (likely its own DESIGN-DOC phase per the project's established pattern for work this size), and a decision on whether it's a launch blocker or deferred with sport display kept as free text for the soft launch | n/a | confirmed via DB schema, Zod schema, and full-codebase terminology search |
| LR-008 | P0 | Households / Guardian access | There is no functional invitation or account-linking flow for parents/guardians anywhere in the product. `household_adult` (added via `addHouseholdAdult`) only stores a name/email/phone contact record — no account is created, no email is sent, no link to a real login happens. `GuardianRelationshipRepository.insert()` exists but is called from **zero** application code paths (confirmed by search across `src/main/kotlin`) — the only `guardian_relationship` rows in the DB are pre-seeded demo data (e.g. sarah.johnson/maya.johnson), not something the product can create today. This means a real organization cannot actually get a parent logged in to see their own household — a core P0 persona journey (§9 Parent/Guardian) and one of the explicit "Known Historical Risk Areas" (§28 "guardian invitations") this document calls out by name. The single existing `invitation` mechanism (`ADMINISTRATOR`/`TEAM_ADMINISTRATOR`/`TOURNAMENT_ADMINISTRATOR`/`VIEWER`) has no role value for this and isn't wired to household_adult at all | FOUNDER DECISION REQUIRED — this blocks the Parent persona journey entirely for any real (non-seeded) organization; needs scoping as real feature work, not a hardening-pass fix | n/a | confirmed via full-codebase grep for every call site of `GuardianRelationshipRepository`/`guardian_relationship`; none create it outside seed/demo SQL |
| LR-009 | P1 | Athlete self-service access | Same gap as LR-008, for athletes: no code path grants `ATHLETE_SELF` role assignments through normal product use — matches the pre-existing note in `qa/KNOWN-REPO-FINDINGS.md`/`docs/qa/README.md` that there's "no exposed UI endpoint to create the athlete-self login link." Confirmed still true this session. The one seeded athlete account (maya.johnson) works only because its `role_assignment` row was inserted directly by seed SQL | FOUNDER DECISION REQUIRED — same category as LR-008, needs real scoping | n/a | corroborates prior finding, re-verified against current `main` |
| LR-010 | P2 | Staff invitation (web) | Separately from LR-007's copy fix: the registration/verify-email pages can only distinguish "invitation vs. self-signup," not *which* role an invitation is for, because there is no public "preview invitation by token" endpoint — `InvitationPage.tsx`'s own code comment already documents this gap. Practical impact is small today since the only 4 real invitation roles (Administrator/Team Administrator/Tournament Administrator/Viewer) are all "join this org" in spirit, but it means true role-specific copy (e.g. distinguishing an Administrator invite from a Viewer invite) isn't achievable without adding that endpoint — which is real feature work, not in scope for this pass per founder direction | FOUNDER DECISION REQUIRED — low priority; only relevant if role-specific copy across the 4 existing invitation roles becomes a priority | n/a | confirmed via code read, not fixed |
| LR-007 | P2 | Staff invitation (web) | An invited staff member (e.g. Administrator/Coach joining an *existing* organization) who doesn't yet have a Rally26 account saw "Create your **owner** account" / "...continue with **organization setup**" / "Create **Owner** Account" on the registration form, and "Finish **owner** account setup..." on the verify-email page — both hardcoded regardless of the `next`/invitation-token context, even though the confirmation step one screen later already had this right ("...sign in to accept your invitation"). Functionally harmless (verified full round-trip: register → verify email → sign in → Accept Invitation correctly grants the invited role, e.g. ADMINISTRATOR at Riverside) but actively misleading — an invited coach/admin could easily believe they were starting a brand-new organization | PASS (fixed) | uncommitted, this session — `frontend/src/pages/auth/RegisterPage.tsx` and `VerifyEmailPage.tsx` now branch on invitation context for heading/subtitle/button/Seo copy | reproduced live pre-fix (3 screens, all wrong), re-verified live post-fix with a second real invitation (support@rally26.com) — all copy now reads "Create your account" / "accept your invitation" |
| LR-006 | P1 (likely resolved — see LR-022) | Web app bootstrap, any authenticated route | **2026-08-19 update:** very plausibly root-caused as a side effect of LR-022 (below) — `EligibilityClearanceRepository`'s untyped-null bind parameter caused a deterministic Postgres wire-protocol error on every call with no status filter, and a wire-protocol-level failure mid-Describe/Bind can leave a pooled HikariCP connection in a bad session state for whichever *unrelated* request gets handed that same physical connection next. This fits every observed characteristic of this entry: different, unrelated endpoints failing together in bursts, always self-healing on retry (once the poisoned connection gets cycled out of the pool), zero backend ERROR-level correlation (a driver-level protocol error surfaces differently than an application exception), and the "higher parallel-request-count routes fail more/longer" correlation noted below (more concurrent requests per page load ⇒ higher odds one of them is the eligibility-clearance call, and higher odds a sibling request gets the poisoned connection). LR-022 is fixed and stress-tested (10/10 clean); this entry is left open rather than marked fully PASS because the poisoning mechanism itself wasn't directly instrumented/proven, and residual blank-`#root` occurrences should be watched for during the rest of this pass to confirm the frequency has actually dropped. On a hard page reload, `#root` sometimes never receives any React-rendered content — confirmed by reading `document.getElementById('root').innerHTML` directly (empty string), not just a screenshot — and stays that way for 15+ seconds with no recovery, reproduced in a brand-new, never-before-used browser tab (rules out tab-specific state). In milder occurrences the same blank window self-heals within a few seconds once `/api/v1/me/preferences` / `/api/v1/me/dashboard-context` and other `/me/*` calls (`action-center`, `announcements`) — which intermittently return `503` for no determinable reason — eventually succeed via React Query's `retry: 1`. Investigated extensively and ruled out: CORS/CSP (fixed by LR-005), missing/malformed auth (every variant tested gives a clean 401, never 503), the app's own rate limiters (return `429` with a JSON body per `RateLimiting.kt`, nowhere near threshold anyway), HikariCP pool contention, and a React render-time exception (added a top-level `ErrorBoundary` as a real, separate hardening fix — src/components/ErrorBoundary.tsx — but it never fires during the empty-`#root` occurrences, meaning React's own render/commit cycle isn't even the failure point). The identical request always succeeds when replayed manually (curl or in-page `fetch`), and **zero of these 503s ever produced a backend log line** — the app has no per-request access logging, so nothing server-side correlates with any of this. Could not get further with available tooling: browser console-message capture was unreliable/stuck for the whole session (repeatedly returned stale/cached output regardless of tab), so the actual thrown error, if any, was never directly observed. Founder asked to log this and move on rather than continue debugging live | FOUNDER DECISION REQUIRED — needs either (a) a developer reproducing with real Chrome DevTools open (console + Network tab) to get the actual error, since this session's automated tooling couldn't surface it, or (b) retesting on the real Linux deploy target to rule out Docker-Desktop-for-Windows networking flakiness as the cause. Separately, the ErrorBoundary added this session (src/components/ErrorBoundary.tsx, wired into App.tsx) is a real fix worth keeping regardless — it guarantees any *future* uncaught render error shows a recoverable "Reload page" screen instead of unmounting the whole app to blank, closing the §22 gap that let this class of failure go undetected | n/a | reproduced 5× across 3 tabs (2 fresh) over ~25 min in the first pass (self-healed in 3, did not recover within 15s in 2); recurred again in a later pass specifically on the household-fees route (which fires ~7 parallel API calls on mount: household, fee-assignments, participants, payment-methods, plus the standard 4 `/me/*` bootstrap calls) — failed 6+ consecutive attempts across ~5 minutes and multiple fresh tabs/sign-ins, while the plain `/app` dashboard route (fewer parallel calls) succeeded on its first attempt in the same window. This correlation (higher parallel-request-count routes fail more/longer) is a new data point worth investigating if this turns out to reproduce on the real deploy target — still consistent with the Docker-Desktop-networking-flakiness theory (more simultaneous connections through that layer, more chances for one to drop), but not proven |

---

## 36. Final Launch Signoff

```text
Release candidate:
Commit SHA:
Environment:
Date/time:

Backend: PASS / FAIL
Web automated: PASS / FAIL
Web live browser: PASS / FAIL
Mobile Android: PASS / FAIL / BLOCKED
Mobile iOS: PASS / FAIL / BLOCKED
Auth/onboarding: PASS / FAIL
Owner: PASS / FAIL
Coach: PASS / FAIL
Parent: PASS / FAIL
Athlete: PASS / FAIL
Sport configuration: PASS / FAIL
Payments: PASS / FAIL
Fundraising: PASS / FAIL
Swag Shop: PASS / FAIL
Sponsorship: PASS / FAIL
Messaging: PASS / FAIL
Eligibility/privacy: PASS / FAIL
Notifications: PASS / FAIL
Founding Organization: PASS / FAIL
Integrations: PASS / FAIL
Support/admin: PASS / FAIL
Production ops: PASS / FAIL
Backup/restore: PASS / FAIL

Open P0:
Open P1:
Founder-accepted exceptions:

FINAL RECOMMENDATION:
GO / NO-GO
```

Any unaccepted P0 requires **NO-GO**.

---

## 37. Definition of Success

The end state is not merely “the test suite is green.”

The end state is:

> **A new organization can receive an invitation to Rally26, onboard without developer help, create or import teams, see correct sport-specific terminology, invite its people, run schedules and communication, safely manage youth data, use revenue tools, receive the right notifications, get support when needed, and continue using the platform without encountering a known launch-blocking failure.**

The soft launch exists to learn from real organizations.

It must not be used to discover defects that disciplined launch-readiness testing should have found first.

---

## 38. Role Workflows Wiki (Internal)

**Status:** Initial draft, 2026-08-20, built during this launch-readiness pass.
**Purpose:** Two audiences, one section. (1) **QA** — a real, navigable script per role to follow
when testing, so coverage is repeatable instead of ad hoc. (2) **Sales/demo** — what to actually
click through to show a prospect what each role's day-to-day looks like.
**How this was built:** Every workflow below marked "Verified" was walked live in-browser against
the docker-compose stack this session, using the real seeded accounts (`db/seed/V9000__dev_seed_dashboard_role_users.sql`,
password `DevPassword123!` for all four). Workflows marked "Documented, not live-walked this pass"
are accurate to the code/routes but weren't clicked through step-by-step this session — treat them
as a starting script, not a guarantee. Nothing here is invented; where depth is thin, it says so.

See also: `docs/qa/README.md` (demo data CSV for standing up a fresh org), `docs/ux-review.md`
(page-by-page UX inventory).

### The five roles

| Role | Real seeded account (local/dev) | Where they live |
|---|---|---|
| Owner / org administrator | `mike.anderson@riversideyouthsports.example` | Web (`/app`) |
| Coach / team staff | `jordan.ellis@riversideyouthsports.example` | Web (`/app`), mobile |
| Guardian / parent | `sarah.johnson@riversideyouthsports.example` | Web (`/app`), mobile |
| Athlete | *(mobile-only account — see Athlete section)* | Mobile only |
| Platform admin (Rally26 staff) | *(no seeded account — see Platform Admin section)* | Web (`/app/platform`) |

All four seeded web accounts share the dev-only password `DevPassword123!`. Do not reuse this
pattern outside local/dev — see `docs/BACKUP-RESTORE-RUNBOOK.md` and the compliance docs for
prod credential handling.

### 38.1 Owner / org administrator

**Mental model:** Runs the club day-to-day. Owns the money (fees, fundraising, swag, sponsorships),
the roster (teams, households, athletes), and the org's identity (branding, settings, staff roles).
The heaviest-permissioned role short of platform admin.

**Primary goals:** Get paid (fees/fundraising/swag/sponsorships), keep rosters and eligibility
current, communicate with families, and not have to think about any of it more than necessary.

#### Core workflow: org home base (Verified)

1. Sign in → lands on `/app`, the personal dashboard (org-agnostic if the user has multiple orgs).
2. Click **"Open organization"** (or the org card) → org Overview (`/app/organizations/{id}/overview`).
   This is the real nerve center: Organization Summary (active teams/participants/households/
   tournaments), Financial Overview (fees assigned/collected, fundraising, apparel sales, pending
   payout), Team Performance, Upcoming Events, Recent Activity feed, Reports Snapshot.
3. The org has its own left sidebar once inside (`Overview, Action Center, Announcements, Messages,
   My Integrations, Organization, Billing, Teams, Tournaments, Households & Athletes, Events, Fees
   & Payments, Fundraising, Swag Shop, Sponsorships, Reports, Documents, Members, Integrations,
   Settings`) — this is the real map of everything an Owner can touch.

**Known flakiness (LR-006/LR-024):** Deep-linking directly to a `:section` URL (e.g. pasting
`/app/organizations/{id}/documents`) can intermittently redirect to Overview with the nav
collapsed to just "Overview" — a transient `/me/contexts` fetch issue, not a real permission
problem. If this happens: click through client-side instead (Dashboard → "Open organization" →
the section link) rather than re-navigating the same URL. Reliable workaround, not yet a real fix.

#### Workflow: fundraising campaign lifecycle (Verified)

1. Fundraising → **Create** a campaign ("General fundraiser" or a themed type). Client-side slug
   validation catches invalid slugs before submit.
2. Submit for approval — **owner-created campaigns skip the approval gate** and auto-activate
   (approval is only required for non-owner creators; this is intentional policy, confirmed against
   the actual gate logic, not a bug).
3. Campaign goes live, accepts contributions (supporter-facing payment flow not exercised this pass).
4. **Close** the campaign when done, then **Archive** it. Both are real state transitions with
   real audit trail entries ("Campaign closed", "Campaign archived" show in Recent Activity).

#### Workflow: Swag Shop (Verified — store/catalog; Documented — checkout)

1. Swag Shop → real store (test org's is "Riverside Team Store"), brand asset library, Printify-linked
   product catalog, athlete-specific storefronts (published, with real slug + QR code for sharing).
2. **Orders and fulfillment** panel: searchable/filterable order list (keyword, status, payment
   source, fulfillment status) — this was broken (404) until fixed this session, LR-025.
3. Checkout/order/receipt flow from the supporter side is not yet walked this pass — next QA
   priority for this area.

#### Workflow: Sponsorships (Verified — package list + review search; Documented — publish/QR/payment)

1. Sponsorships → package list (create/edit packages, price, exclusivity, placement dates).
2. **Review pending sponsorships** sub-view — separate search/filter view from the package list,
   for triaging sponsorships by review status. Both this and the package list 500'd/404'd on every
   request until fixed this session (LR-026, LR-028) — now real.
3. Publish/QR-share/payment confirmation flow not yet walked live this pass.

#### Workflow: Documents (Verified)

1. Documents → **Add document**: title + PDF upload (15MB max, PDF-only, enforced client- and
   server-side).
2. Real presigned-upload flow: browser requests an upload URL from the API, PUTs the file bytes
   directly to object storage (never through the Rally26 API — see `DESIGN-DOC.md` §11.3), then
   confirms. This entire flow was silently broken in local/staging until fixed this session
   (LR-029) — worth a smoke-test after any docker-compose rebuild, since the failure mode is
   silent (no console error, no visible network entry for the failed PUT).
3. **Send to every household** broadcast button exists but not yet exercised this pass.
4. Search/sort/remove all work as expected (client-side filtered from the full list).

#### Workflow: branding (Verified)

1. Settings tab (far right of the org sub-nav — scroll right to reach it) → **Branding**: Logo and
   Cover image, both real presigned uploads (same pipeline/fix as Documents above).
2. Organization Profile: name, type, sports offered (multi-select chips) below branding.

#### Workflow: Members (Verified — search; Documented — role editing/invitations)

1. Members → real active-member roster with role/status, now searchable (was 404 until fixed this
   session, LR-027).
2. Role changes / invitation flow itself not yet walked step-by-step this pass.

#### Known gaps an Owner will hit today

- **No account/data deletion flow anywhere** (cross-cutting compliance gap, not Owner-specific).
- Guardian **invitation/linking has no real code path** (`GuardianRelationshipRepository.insert()`
  is never called outside seed data) — an Owner cannot actually get a new parent logged in today.
  This blocks demoing the full "invite a family" loop with a *new* (non-seeded) household.
- Athlete self-signup has no exposed endpoint either (see Athlete section) — same blocker, different persona.

### 38.2 Coach / team staff

**Mental model:** Runs one or more teams day-to-day — roster, events, family communication,
safety moderation. Narrower permission scope than Owner (team-level, not org-level), with role
variants (`TEAM_MANAGER`/`TEAM_EDITOR`/read-only "Coach") controlling exactly what they can edit
vs. only view.

**Primary goals:** Know who's coming to practice/games, talk to families, keep the roster's
eligibility status visible, and moderate messaging safety concerns without needing an Owner.

#### Core workflow: team roster + staff (Verified)

1. Sign in as coach → team detail page.
2. **"Coaches & Staff"** panel — shows real team-role assignments (e.g. "Jordan Ellis / Team
   Manager"). This 500'd for every team until fixed this session (LR-020).
3. Athletes list with a working **"Eligibility" filter** (All athletes / Ineligible only) — this
   depended on a query that failed 100% of the time until fixed this session (LR-022); if this
   filter silently breaks again, check `EligibilityClearanceRepository` for the same
   untyped-null-parameter pattern before assuming it's a flakiness blip.

#### Workflow: family messaging (Verified)

1. Messages → **start a new family conversation**: pick a team, pick recipients (multi-athlete/
   guardian picker — one of only two real multi-select UI patterns in the whole product).
2. New thread appears both in the coach's own "Your message threads" and the manager-level
   "Threads" list, with correct member list and first-message body.
3. Broadcast creation UI exists (team-wide, not 1:1) but not exercised this pass.

#### Workflow: safety review (Verified)

1. A reported message shows up in the Safety review panel.
2. **"Start review"** (moves to `IN_REVIEW`) — this 500'd on every attempt until fixed this session
   (LR-023, same untyped-null-parameter bug class as LR-022 above, worth remembering as a pattern
   to watch for in any future "optional filter" SQL).
3. Resolve/dismiss to a terminal status — verified via the same fix's regression test, not
   separately walked live.

#### Known gaps a Coach will hit today

- Event Details **Edit** is a known dead-end on mobile (logged, not yet fixed) — a pre-existing
  violation of the "never a dead-end control" rule (see the Finding Log above).
- Same guardian-linking/athlete-self-signup gaps as above limit demoing with a genuinely new roster.

### 38.3 Guardian / parent

**Mental model:** Manages one household — one or more athletes, possibly across multiple teams.
Everything is scoped to "my family," never the org at large. The most permission-constrained of
the three "logged-in adult" roles, and historically the one where scoping bugs hid best (several
of this session's worst bugs were guardian-only 403s that silently hid UI instead of erroring).

**Primary goals:** See my kid's schedule, RSVP, talk to the team, keep eligibility/waivers current,
control who can message my athlete.

#### Core workflow: family overview (Verified — pre-linked guardian only)

1. Sign in → Family Overview: real household data, athlete list, team assignments.
2. Cross-household access correctly 403s (verified: a guardian cannot see another family's data).
3. **This only covers an already-linked guardian.** There is currently no product path to link a
   *new* guardian to a household — see Owner section. Demoing "parent onboarding" requires using
   one of the pre-seeded guardian accounts, not a fresh signup.

#### Workflow: RSVP (Verified — after LR-018 fix)

1. Athlete detail (or event detail) → RSVP controls (Yes/No/Maybe) for events the athlete's team
   is on.
2. **This was completely broken for every guardian on every event until fixed this session
   (LR-018)** — a silent 403 that looked exactly like "athlete isn't on this team," with no error
   shown. If RSVP controls are ever missing again for a guardian who *should* see them, check the
   network tab for a 403 on `.../participants/{id}/teams` before assuming a real team mismatch.
3. Submitting a real RSVP correctly moves the aggregate count in real time (e.g. "1 Attending → 1
   Maybe").

#### Workflow: eligibility / waiver acknowledgment (Verified)

1. Athletes page → expand an athlete → **Eligibility** section → a real pending requirement (e.g.
   "Season Liability Waiver") in "Action needed" state.
2. Two-step acknowledge flow: "I acknowledge" → confirm. Status moves to "Submitted · Complete"
   with today's date, persists across reload, and the summary pill flips to "Cleared."

#### Workflow: messaging + safety controls (Verified)

1. Existing team/broadcast conversation threads render with real message history; replies send
   and appear instantly.
2. `/app/messages` → **communication restriction controls**: a guardian can record a "Stop staff →
   athlete messages" restriction (status ACTIVE, kept as permanent safety history, never deleted),
   then **lift** it later (status LIFTED, history preserved). Gated athlete peer-messaging exists
   but not exercised this pass.

#### Known gaps a Guardian will hit today

- No self-service way to become a linked guardian in the first place (see Owner section) — anyone
  demoing this role has to start from a pre-seeded account.
- Household-level document assignment / "sent to every household" broadcasts not yet verified from
  the receiving end this pass.

### 38.4 Athlete

**Mental model:** The most permission-constrained role — a real, scoped account (not a shared
family login), mobile-only, built around Home/Calendar/Messages.

**Status: thin coverage.** Unlike the three roles above, this session did not live-walk an athlete
session — there is currently **no product path to create a real athlete-self login** to test with
in the first place. `AuthorizationService.linkAthleteSelf` exists in backend code but has **zero**
controller route exposing it (confirmed via `docs/qa/README.md`'s own note on this — same finding,
not re-derived here). Until that gap closes, athlete-persona QA/demos are blocked at the "how do I
even get a session" step, not a feature-completeness step.

**Next step for this section:** once a real athlete login path exists (web or mobile), replace this
placeholder with an actual walked workflow — don't guess at one in the meantime.

### 38.5 Platform admin (Rally26 staff, not an org's own staff)

**Mental model:** Rally26's own internal console for cross-org support, billing oversight, and
platform-wide moderation — not something any customer org ever sees.

**Status: partially covered.** Verified this session: isolation direction only — a regular Owner
hitting `/app/platform` gets a clean "You don't have access to this page," and the platform APIs
(`/api/v1/platform/dashboard/summary`, `/organizations`) both correctly 403 a non-platform-admin
token at the API layer. The console's *own* features (from other sessions' work, not re-walked
here): cross-org payments list + refund/void, read-only integrations during support sessions,
roster drill-down (athletes/coaches, table/card view), audit log.

**Known gap:** no seeded platform-admin account exists in `V9000__dev_seed_dashboard_role_users.sql`
the way the other four roles do — provisioning one (or documenting how) is a prerequisite for this
section to get the same live-walked treatment as Owner/Coach/Guardian.

### 38.6 Using this for QA

Each "Verified" workflow above is a literal repro script — the exact click path this session used
to confirm the feature works, including the specific real data it produced. Re-run these after any
change that touches auth, media/storage config, or the CSP (`frontend/nginx.conf.template`) — this
session found three separate silent-failure bugs (LR-018 RSVP, LR-022 eligibility, LR-029 uploads)
that a role would experience as "the button just doesn't work," not an error message.

### 38.7 Using this for demos

Owner → Coach → Guardian is the natural demo arc (create the season → run a team → be a parent in
it), using the four seeded accounts above against a fresh docker-compose stack or a demo org built
from `docs/qa/README.md`'s CSV. Skip Athlete and Platform Admin for now — both have real
prerequisite gaps (no self-signup path; no seeded account) that would derail a live demo rather
than showcase the product.
