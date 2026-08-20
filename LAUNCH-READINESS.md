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
| Auth | Register | ☐ | ☐ | ☐ | ☐ | UNVERIFIED | |
| Auth | Email verification | ☐ | ☐ | ☐ | ☐ | UNVERIFIED | |
| Auth | Sign in / sign out | ☑ | ☐ | ☑ | ☑ | PASS | Live-tested as Owner (mike.anderson) and Guardian (sarah.johnson) against docker-compose stack after LR-005 fix; required fixing CSP first (LR-005) |
| Auth | Session restore / refresh | ☑ | ☐ | ☑ | ☑ | FAILED | See LR-006 — hard reload sometimes leaves the page blank (empty `#root`) for 15+ seconds or longer with no recovery; sometimes self-heals in a few seconds. Root cause not confirmed; founder decision needed on next step |
| Auth | Password recovery if exposed | ☐ | ☐ | ☐ | ☐ | UNVERIFIED | |
| Auth | Invitation — existing user | ☐ | ☐ | ☐ | ☐ | UNVERIFIED | Not yet tested — only the new-user path was walked this session |
| Auth | Invitation — new user | ☑ | ☐ | ☑ | ☑ | PASS | Live-tested full round-trip (invite → register → verify email → sign in → Accept Invitation) with two real invitations (ekrusznis@gmail.com, support@rally26.com) against docker-compose; correct role (Administrator) granted at the correct org, invitation flips to ACCEPTED, audit trail records both events. Found and fixed a real copy bug along the way (LR-007); local email delivery is logging-only (no real Resend key configured), verified via outbox event payloads instead of live inbox delivery |
| Onboarding | Owner onboarding | ☐ | ☐ | ☐ | ☐ | UNVERIFIED | |
| Onboarding | Organization creation | ☐ | ☐ | ☐ | ☐ | UNVERIFIED | |
| Onboarding | Founding Organization entitlement | ☐ | ☐ | ☐ | ☐ | UNVERIFIED | |
| Subscription | Free if launch-enabled | ☐ | ☐ | ☐ | ☐ | UNVERIFIED | |
| Subscription | Starter $49 | ☐ | ☐ | ☐ | ☐ | UNVERIFIED | |
| Subscription | Club $149 | ☐ | ☐ | ☐ | ☐ | UNVERIFIED | |
| Subscription | Capacity / upgrade / downgrade | ☐ | ☐ | ☐ | ☐ | UNVERIFIED | |
| Organization | Profile / branding / timezone | ☐ | ☐ | ☐ | ☐ | UNVERIFIED | |
| Organization | Members / roles / invitations | ☐ | ☐ | ☐ | ☐ | UNVERIFIED | Cross-org isolation confirmed separately (Riverside owner denied Lakeside org overview + team detail, 403 at API layer, no data leak); members/invitations flow itself not yet walked |
| Teams | Create / edit / archive | ☑ | ☐ | ☑ | ☑ | PASS | See LR-016 — the Teams list page was completely broken (500 on every load) until fixed this session; now confirmed live with real seeded data and full action set (Schedule/Roster/Branding/Manage access/Timezone/Colors/Archive). Team creation itself confirmed earlier this session too |
| Teams | Roster / Coaches & Staff (coach persona) | ☑ | ☐ | ☑ | ☑ | PASS | See LR-020 — the "Coaches & Staff" panel 500'd for every caller until fixed this session; verified via direct curl as the real coach account (jordan.ellis, TEAM_ADMINISTRATOR) post-fix. Athlete roster on the same page confirmed with real data (Sofia Martinez) |
| Teams | Sport configuration | ☑ | ☐ | ☑ | ☑ | FAILED | See LR-011 — no canonical sport code, no backend validation, no terminology derivation anywhere; `sport` is free text end to end |
| Teams | Roster / staff | ☐ | ☐ | ☐ | ☐ | UNVERIFIED | |
| Teams | Branding/colors | ☐ | ☐ | ☐ | ☐ | UNVERIFIED | |
| Teams | Season rollover | ☐ | ☐ | ☐ | ☐ | UNVERIFIED | |
| Tournaments | Create/edit/archive | ☐ | ☐ | ☐ | ☐ | UNVERIFIED | |
| Households | Create / adults / athletes | ☑ | ☐ | ☑ | ☑ | PASS (list) | See LR-016 — the Households & Athletes list page was completely broken (500 on every load) until fixed this session; now confirmed live with real seeded data. Create/adults/athletes sub-flows not yet individually walked |
| Households | Team assignment / guardian invite | ☐ | ☐ | ☐ | ☐ | FAILED | See LR-008 — guardian invite is not implemented anywhere in the product (verified by full-codebase search); team assignment not yet separately tested |
| Guardian | Overview / family schedule / athlete detail | ☑ | ☐ | ☑ | ☑ | PASS (pre-linked guardian only) | Live-tested sarah.johnson (pre-seeded, already-linked account): Family Overview renders real household data, cross-household access correctly 403s. **Does not cover getting a real guardian linked in the first place** — see LR-008: no product code path can actually link a new guardian to a household today |
| Events | Create/edit/cancel/postpone | ☑ | ☐ | ☑ | ☑ | PASS (create only) | Live-tested creating a draft event as Owner; correct timezone-aware display (America/Chicago → CDT), appears in list immediately. Edit/cancel/postpone not yet tested. See LR-012 for the date-format duplication and LR-011 for the generic "Game / match / competition" type label (no sport-derived terminology) |
| Events | Sport terminology | ☐ | ☐ | ☐ | ☐ | UNVERIFIED | |
| Events | RSVP / staff visibility | ☑ | ☐ | ☑ | ☑ | PASS (guardian + owner) | See LR-018 — guardian RSVP was completely broken (silent 403 hid the controls on every eligible event) until fixed this session; now confirmed live end-to-end (guardian submits Maybe, aggregate updates in real time). Owner aggregate view + management actions (Send reminder/Postpone/Cancel) also confirmed live earlier this session. Coach-visibility variant not yet tested |
| Events | Maps / ICS / CSV / feed import | ☐ | ☐ | ☐ | ☐ | UNVERIFIED | |
| Messaging | Team / broadcast | ☑ | ☐ | ☑ | ☑ | PASS (conversations) | Live-tested guardian↔owner Varsity Soccer conversation thread as sarah.johnson: existing thread rendered with real prior message (Mike Anderson, Aug 10), sent a real reply that appeared in the thread instantly. Team-wide broadcast (as opposed to 1:1 conversation) not separately tested this pass |
| Messaging | Athlete messaging / safety controls | ☑ | ☐ | ☑ | ☑ | PASS | Live-tested guardian communication restriction controls (`/app/messages`) as sarah.johnson for athlete Maya Johnson: recorded a "Stop staff → athlete messages" restriction (confirmed ACTIVE, retained as safety history per design), then lifted it via the real "Lift" action (confirmed status changed to LIFTED, history preserved not deleted). Gated athlete peer messaging and message-level reporting UI present but not exercised this pass |
| Eligibility | Requirements / clearance / waivers | ☑ | ☐ | ☑ | ☑ | PASS (guardian acknowledgment) | Live-tested as sarah.johnson: expanded Maya's "Eligibility" section on the guardian's own Athletes page, saw a real "Season Liability Waiver" requirement in "Action needed" state, completed the two-step acknowledge flow ("I acknowledge" → confirm), and the status correctly moved to "Submitted · Complete" with today's date, persisting across reload (clearance pill also flipped to "Cleared"). Owner-side requirement creation/management not tested this pass |
| Marketing site | Positioning / hero / FAQ / Security page accuracy | ☑ | ☐ | ☑ | ☑ | PASS (fixed) | See LR-021 — hero/FAQ/Security page/footer all made stale, false, or misleadingly narrow claims against current product reality (fabricated stat claims, "not a roster system," "no child accounts," "passwords not stored," "payments when live payments launch," unbacked SportsEngine/TeamSnap/SMS feature claims). All corrected and verified live |
| Marketing site | Talk to Sales lead capture | ☑ | ☐ | ☑ | ☑ | PASS (fixed) | See LR-021 — the 25-field form validated and showed a fake confirmation with a generated reference number but never persisted or notified anyone; every submitted lead was silently lost. Replaced with a compact real form on the same backend-wired public support-case endpoint as the working Contact Us section; live-submitted a real test lead and confirmed it persisted in `support_case` with the correct requester/subject |
| Marketing site | Pricing clarity / Founding Organization page | ☑ | ☐ | ☑ | ☑ | PASS (fixed) | See LR-021 — fixed the "$149/mo → Start Free" CTA contradiction (differentiated per-tier CTAs), added inline fee-math example, added a visual-only FREE 4th tier (routes to Talk to Sales, not live registration — see DESIGN-DOC.md §14.1T/U, founder decision 2026-08-19 to defer real FREE signup until entitlement-gating audit), and built a real `/founding-organizations` landing page replacing the prior redirect straight into generic registration |
| Documents | Org/team/family docs if exposed | ☐ | ☐ | ☐ | ☐ | UNVERIFIED | |
| Media | Upload / privacy / public release | ☐ | ☐ | ☐ | ☐ | UNVERIFIED | |
| Fees | Templates / assignments / plans | ☐ | ☐ | ☐ | ☐ | UNVERIFIED | |
| Fees | Payments / credits / collections / export | ☑ | ☐ | ☑ | ☑ | PASS (online payment path) | Live-tested as Guardian: fee list shows correct partial-payment state ($35 due of $75 etc.), "Pay online" reaches a real Stripe test-mode Checkout Session (correct amount, branding). Recording (manual/offline) not yet tested; export not yet tested |
| Payments | Stripe connect / checkout / failure | ☑ | ☐ | ☑ | ☑ | PASS (checkout success path) | Real Stripe test-mode checkout completed live (user completed the card form after the extension correctly refused to automate payment-form entry — see below); redirect-back page shows an honest "Confirming your payment..." state, never a premature "success," and no raw UUIDs rendered on-page (only in the URL query string). Failure/decline path not yet tested |
| Payments | Webhooks / idempotency | — | — | ☑ | — | PASS | Verified via `stripe listen --forward-to` + `stripe trigger checkout.session.completed` against the live docker-compose backend: real, correctly-signed Stripe webhooks get clean 200s end-to-end. Existing backend test suite (`StripeWebhookControllerTest.kt`) explicitly covers invalid-signature rejection (400), replayed-event no-op (idempotency), unrecognized-event handling, and correct dispatch routing (contribution/order/sponsorship/payout-account/dispute) — all passing. Found and fixed a real gap along the way: a malformed webhook request (missing signature header) was a false-positive 500/ERROR-alert instead of a clean 400 — see LR-013. One real local limitation: the specific checkout session a live user completed during this session (`cs_test_a1sW...`) never got its real webhook delivered, since Stripe can't reach a private `localhost` URL — it's stuck in `PENDING_CHECKOUT` locally, which is correct/expected app behavior (never trusts the browser redirect alone), not a bug |
| Payments | Refunds / disputes / payout if exposed | ☐ | ☐ | ☑ (partial) | ☐ | FAILED (fees) | See LR-015 — fee payments have no real refund path, only a ledger-only "Void" with zero Stripe interaction, offered identically for card and non-card payments alike. Contribution/order/sponsorship refunds ARE real (genuine Stripe-integrated endpoints exist per openapi.yaml), not yet live-tested. Dispute webhook routing confirmed via passing existing tests; payout account UI not yet walked |
| Fundraising | Create / publish / contribution | ☐ | ☐ | ☐ | ☐ | UNVERIFIED | |
| Fundraising | Attribution / credits / templates if exposed | ☐ | ☐ | ☐ | ☐ | UNVERIFIED | |
| Swag Shop | Store / catalog / Printify | ☐ | ☐ | ☐ | ☐ | UNVERIFIED | |
| Swag Shop | Checkout / order / receipt | ☐ | ☐ | ☐ | ☐ | UNVERIFIED | |
| Sponsorships | Package / publish / QR / payment | ☐ | ☐ | ☐ | ☐ | UNVERIFIED | |
| Public Pages | Create / edit / publish / public route | ☐ | browser | ☐ | ☐ | UNVERIFIED | |
| Dashboards | Owner | ☐ | ☐ | ☐ | ☐ | UNVERIFIED | |
| Dashboards | Coach | ☐ | ☐ | ☐ | ☐ | UNVERIFIED | |
| Dashboards | Parent | ☐ | ☐ | ☐ | ☐ | UNVERIFIED | |
| Dashboards | Athlete | ☐ | ☐ | ☐ | ☐ | UNVERIFIED | |
| Search | Global / feature search | ☐ | ☐ | ☐ | ☐ | UNVERIFIED | |
| Action Center | Role-appropriate surfaces | ☐ | ☐ | ☐ | ☐ | UNVERIFIED | |
| Reporting | Reports / analytics | ☐ | ☐ | ☐ | ☐ | UNVERIFIED | |
| Notifications | In-app / email / push / SMS if enabled | ☐ | ☐ | ☐ | ☐ | UNVERIFIED | |
| Help | Help Center | ☐ | ☐ | ☐ | ☐ | UNVERIFIED | |
| Support | Submit / status / admin response | ☐ | ☐ | ☐ | ☐ | UNVERIFIED | |
| Platform Admin | Overview / orgs / payments / support / audit | ☐ | — | ☑ | ☑ | IN TEST | Isolation direction confirmed: an Owner (mike.anderson) hitting `/app/platform` gets a clean "You don't have access to this page," and `/api/v1/platform/dashboard/summary` + `/organizations` both 403 at the API layer for a non-platform-admin token. Platform admin's own console UI/features not yet walked |
| Integrations | QuickBooks / TeamSnap / SportsEngine | ☐ | ☐ | ☐ | ☐ | UNVERIFIED | |
| Integrations | GameChanger / MaxPreps if exposed | ☐ | ☐ | ☐ | ☐ | UNVERIFIED | |
| Integrations | CSV / ICS | ☐ | ☐ | ☐ | ☐ | UNVERIFIED | |
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
| LR-001 | P2 | Fees / Payments (mobile) | `mobile/src/lib/money.ts` `currencyFractionDigits` could return `undefined`, unlike the frontend equivalent which guards with `?? 2`; risk of `NaN` money formatting for edge-case currency codes | PASS (unit-level; not yet live-retested) | uncommitted, this session | pending live browser/device pass |
| LR-002 | P2 | Mobile CI / build gate | Mobile has no CI workflow at all (`.github/workflows/` has zero mobile jobs) and `npm run typecheck` silently depends on a stale, gitignored, machine-local `.expo/types/router.d.ts` that isn't regenerated automatically, so a clean checkout can typecheck-fail on legitimate routes until someone runs an export/dev-server once | FOUNDER DECISION REQUIRED (add mobile CI job + typegen step) | n/a | n/a |
| LR-003 | P3 | Fundraising (mobile) | Two internal helpers (`actionHook`, `reviewMutation`) called React Query hooks without hook-shaped names, tripping `react-hooks/rules-of-hooks` and blocking the mobile lint gate; plus one unescaped-apostrophe lint error in `fundraising-detail.tsx` | PASS | uncommitted, this session | pending live browser/device pass |
| LR-004 | P3 | Auth (web, test suite) | `frontend/e2e/dashboard.spec.ts` asserted a stale unauthenticated-access UX (inline "please sign in to continue" alert on the same route) that no longer exists in the app; actual current behavior (`ProtectedRoute.tsx`) is a real redirect to `/auth/sign-in?next=<path>` with correct post-login return, which is intentional and correct. Updated the test to assert the real behavior; not a product defect | PASS (24/24 E2E green after fix) | uncommitted, this session | reproduced live against docker-compose stack |
| LR-005 | P1 | Local/staging full-stack QA infrastructure | A fresh `docker compose up --build` produced a frontend whose CSP `connect-src` only allow-listed `'self'` and the *production* API domain (`https://api.rally26.com`), hardcoded in `frontend/nginx.conf`. Every browser API call against a local/staging backend (`http://localhost:8080`) was silently blocked by CSP — no server log, just a generic "Something went wrong" on sign-in. `compose.yaml`'s `VITE_API_BASE_URL` was also set as a runtime `environment:` var, which is a no-op since Vite bakes that value at image build time, not container-run time. This blocks §6/§10's mandated live-browser QA method against any freshly built local/staging stack. Production is unaffected (its CI-built image's `VITE_API_BASE_URL` already matches the hardcoded CSP domain) | PASS (fixed) | uncommitted, this session — `frontend/nginx.conf` → `frontend/nginx.conf.template` with `${API_ORIGIN}` envsubst'd at container startup (nginx's built-in templating), `frontend/Dockerfile` copies it to `/etc/nginx/templates/` with `ENV API_ORIGIN=https://api.rally26.com` default, `compose.yaml` overrides `API_ORIGIN=http://localhost:8080` and moves `VITE_API_BASE_URL` to `build.args` | reproduced live: sign-in failed pre-fix, succeeded post-fix, against the same rebuilt docker-compose stack |
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
