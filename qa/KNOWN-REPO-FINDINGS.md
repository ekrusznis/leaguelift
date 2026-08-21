# Findings from source review before running Firebase AI QA

These are not Firebase results. They are source-level observations worth targeting with
the first run.

## 1. Owner subscription gating exists on current main

This is important because it changes the test baseline. Current source now includes a
global owner gate in both root routing and `/owner` routing, backed by owner-onboarding
state. It requires ACTIVE organization + COMPLETE onboarding + an accessible subscription
status before native Owner routes unlock.

Target tests:
- `owner-onboarding`
- every `subscription/*` state suite

## 2. Coach More currently appears to contain an Owner More hub

`mobile/src/app/(tabs)/more.tsx` is the Coach persona's More route, but the current file
exports `OwnerMoreScreen`, is commented "Owner More hub", and includes entries such as:

- owner Announcements management
- Broadcasts management
- Reports
- organization-wide Fees & Collections
- Payout Account
- Sponsorships

This is exactly the kind of role/UI leakage the AI suite should flag even if backend
authorization eventually returns 403.

Target:
`coach` → `[SMOKE] Coach - More menu authority boundary`

## 3. Platform Admin has no mobile persona

Root routing has built homes for Coach, Parent, Athlete, with Owner handled specially.
Platform Admin routes to `/role-not-available`.

That is a current product gap, not a reason to route a Platform Admin into a customer
workspace.

Target:
`platform-admin`

## 4. Role-not-available copy is stale

`mobile/src/app/role-not-available.tsx` still says the mobile app is "coach-only for now",
even though Parent, Athlete, and Owner experiences are now built.

Target:
`platform-admin` → boundary copy test.

## 5. Owner mobile route groups three organization membership levels together

Backend dashboard routing maps:

- OWNER
- ADMINISTRATOR
- VIEWER

to the same `OWNER` dashboard persona.

The UI therefore needs careful authority behavior inside the Owner experience. The
backend must remain authoritative, but a Viewer should not be led through normal-looking
destructive management flows just to receive a 403 at the end.

Targets:
- `authority/owner-viewer`
- `authority/owner-admin`

## 6. Coach resource tiers are materially different

Backend capability mapping distinguishes:

- `COACH_READ`
- `TEAM_EDITOR`
- `TEAM_MANAGER`

The app should not make all three look equally powerful.

Targets:
- `authority/coach-read`
- `authority/coach-editor`
- `authority/coach-manager`

## 7. Guardian/athlete household-invitation flow has no mobile entry point

A new `household_invitation` mechanism shipped this session (backend
`com.rally26.invitation.*`, web `HouseholdDetailPage.tsx`'s "Invite guardian"/"Invite
athlete login" controls, `auth/household-invitation` accept page) letting an
Owner/Administrator or a roster-managing coach invite a guardian for an athlete, and
letting an already-linked guardian invite that athlete (13+) to get their own login.
`mobile/src/app` has no screen, deep link, or WebView embed that reaches any part of
this flow — confirmed via full-source grep for "invitation"/"Invite", which only
matches `mobile/src/app/owner/(tabs)/members.tsx` (the pre-existing, unrelated org
staff membership-invitation feature). A guardian or athlete cannot get linked to a
household from the mobile app at all today; this only works from the web app.

This is a real mobile/web parity gap, not a QA-pack omission — no test case was added
for it because there is nothing on mobile to exercise yet.

Target (once built): a new `parent`/`coach` test case once a native screen or WebView
embed exists, likely mirroring the existing `parent-household-web`/`owner-management-web`
pattern of embedding an authenticated web surface inside the app.

## 8. Owner registration has no mobile QA coverage until this pass

`mobile/src/app/register.tsx` (owner sign-up, `POST /auth/register-owner`) existed with
no corresponding test case anywhere in the pack — `shared.yml` covered login validation
and Google sign-in but never registration. Added `shared-register-validation` and
`shared-register-success` this session; see `qa/matrices/COVERAGE-MATRIX.md`.
