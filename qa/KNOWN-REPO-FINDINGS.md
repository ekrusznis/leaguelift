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
