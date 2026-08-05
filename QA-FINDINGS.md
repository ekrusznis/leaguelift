# Live QA Findings — 2026-08-05

**Status update (2026-08-05, branch `fix/qa-findings-critical-bugs`):** all 4 critical bugs (#1-#4) and the RSVP-name minor bug (#7, renumbered from earlier draft) are fixed and verified live in-browser. See each entry below for the fix description and root cause. The guardian-invite gap (#5) remains an open product-scope question for the founder, not yet built. The two cosmetic items (horizontal-scroll-on-load, ambiguous auto-title) are not yet addressed.

**Feature addition (2026-08-05, same branch):** built and verified the Platform Admin cross-org Support Case "Send email" feature — a one-way, ad-hoc email to a case's requester direct from the case list (subject pre-filled `Re: {subject}`, free-form body), independent of the existing status-change flow. Verified live end-to-end: outbox event `support.case.admin_email` reaches `PROCESSED`, audit event `support_case.admin_email_sent` is recorded, and the `LoggingEmailProvider` log confirms the correct recipient/subject. No reply-thread capture, matching the design doc's Phase 17 "no chat threads" boundary.

Live browser QA pass against the local dev stack (seeded data), driven by the 17 user journeys in DESIGN-DOC.md §15. Code-level/unit test coverage was already audited in Phase 13; this pass is the first time these flows have been exercised in a real browser end-to-end. Findings below are grouped by severity. Each entry has enough detail (file paths, repro steps, DB/API evidence) to hand directly to a dev.

Stack: local Postgres (docker, port 5433), backend on 8080, frontend (Vite) on 5173. Seed accounts (password `DevPassword123!` for all): owner `mike.anderson@riversideyouthsports.example`, coach `jordan.ellis@riversideyouthsports.example`, parent `sarah.johnson@example.com`, athlete `maya.johnson@example.com`.

---

## Critical (blocks a core journey for a whole persona)

### 1. New-user invitation acceptance is completely broken/orphaned
**Journeys affected:** 2 (owner invites staff), 3 (staff invites household/guardian)
**Where:** `frontend/src/pages/auth/RegisterPage.tsx` (or wherever `/auth/register` handles the `next` query param) vs `SignInPage.tsx` (which handles it correctly)

Repro:
1. As owner, send a staff invitation (Members tab) to an email with **no existing Rally26 account**.
2. Open the invitation link (`/auth/invitation?token=...`), click **Create Account**.
3. Lands on `/auth/register?next=%2Fauth%2Finvitation...` — but this is the plain **owner self-registration form** ("Create your owner account" / "Create Owner Account"), with no visible link to the invitation.
4. Complete registration → "Check your email... sign in to create **your organization**" (owner-onboarding copy, no mention of the invitation).

Verified via DB after completing registration:
- `app_user` row created (`PENDING_EMAIL_VERIFICATION`) — a real, standalone account.
- `organization_membership`: **zero rows** — never linked to the inviting org.
- `invitation` row: still `PENDING`, completely untouched.

**Control test (works correctly):** same flow with an email that already has an account, using **Sign In** instead of Create Account — correctly redirects through `/auth/sign-in?next=...` back to `/auth/invitation`, shows "Accept Invitation", and correctly validates (rejected with "already a member" when appropriate).

**Impact:** this is the realistic first-time path for onboarding any brand-new coach/administrator/staff member — most invitees won't already have an account. Right now it's fully broken: the invitee ends up with an orphaned, unverified personal account, the invitation is permanently stranded, and the UI copy actively misleads them into thinking they're creating their own organization.

---

### 2. Entire guardian portal is broken beyond the Overview page
**Journeys affected:** 8 (guardian reviews fees), 13 (guardian manages schedule/RSVP)
**Where:** `frontend/src/dashboard/roles/ParentDashboard.tsx` + `frontend/src/routes/appPaths.ts` (`householdEvents`/`household` paths) + `frontend/src/pages/HouseholdDetailPage.tsx`

The guardian's **Overview** page works correctly (real data via `/households/{id}/dashboard/parent/*` endpoints — schedule card, outstanding balance, fundraisers all render right). But every other guardian nav destination is broken and shows the same dead end ("Could not load this household." / Try Again, which does nothing):
- Sidebar "Family Schedule"
- Sidebar "My Athletes"
- Sidebar "Fees & Payments"
- Overview page's own "View Fees" button
- Overview page's own "View full schedule" link

**Root cause:** all of these routes resolve to `HouseholdDetailPage` — the **staff-only** household management page. Its `useHousehold()` hook calls `GET /organizations/{orgId}/households/{householdId}`, a staff-capability-gated endpoint. Verified via curl with the guardian's real JWT: **403**, even for her own household.

The correct backend endpoint already exists and works (verified via curl): `GET /households/{householdId}/events?organizationId=...` → 200 with correct data. The frontend just never built a guardian-facing page that calls it — it only reuses the admin page.

**Secondary bug found while isolating this:** `GET /households/{id}/events` with the required `organizationId` query param omitted throws an unhandled `MissingServletRequestParameterException` → raw 500 `INTERNAL_ERROR`, instead of a clean 400 (the app's own established convention elsewhere, `GlobalExceptionHandler`).

**Impact:** a guardian can see a one-screen summary but cannot click into any detail — can't view full schedule, can't pay/review a fee beyond the summary card, can't see an athlete's profile. Core guardian-facing value prop is unusable past the landing screen.

---

### 3. Public Pages "Create page" form never submits
**Journeys affected:** 1 (owner publishes public pages), 5 (team page creation), 6 (tournament page creation)
**Where:** `frontend/src/features/publicpage/PublicPagesPanel.tsx`

Organization Settings → Public Pages → "Create page". Tested with valid data (Organization type — no team/tournament select dependency, so this isn't about that dropdown): title, slug, summary all valid per `frontend/src/features/publicpage/schema.ts`.

Confirmed via **4 independent reproduction attempts** (2 coordinate clicks, 1 ref-based click directly on the `type="submit"` button, 1 native Enter-key submission in a text field): **zero POST requests ever fire** to `/organizations/{id}/pages`, and **zero console errors**. Code looks structurally correct on inspection (standard react-hook-form + zodResolver + `<form onSubmit>`), so root cause needs a dev with real browser devtools (breakpoint in `onSubmit`, or React DevTools to confirm the form's submit handler is actually bound at runtime).

**Impact:** an organization cannot create ANY public page (org/team/tournament) through the UI at all. This blocks the entire "professional public pages, QR codes, share links" pitch — core marketing/sharing functionality.

---

## High (breaks a specific real interaction, not just an edge case)

### 4. Full page reload/refresh wipes session with no redirect (dead end)
**Where:** `frontend/src/auth/AuthContext.tsx` / `apiClient.ts` / the `/app` route guard

Confirmed via 3 separate reproductions: (1) typing a deep-link URL, (2) a plain refresh of `/app`, (3) clicking the in-app **Sign out** button itself. All three land on a blank page with a static "Please sign in to continue" banner — no redirect to `/auth/sign-in`, no link out. `read_network_requests` showed **zero backend calls attempted** (no `/me`, no refresh) on the reload case — the app doesn't even try to recover the session, it just gives up.

Session/auth token appears to live only in in-memory React state, never persisted (localStorage/cookie) and never paired with a proper unauthenticated-route redirect guard.

**Impact:** every browser refresh, bookmarked link, link opened in a new tab, or (per Phase 11's own mobile-webview plan) app backgrounding/foregrounding logs the user out to a dead page. Reproduction #3 (Sign Out itself) means this isn't an edge case — it's the normal end of every session.

---

## Medium

### 5. No per-household guardian invite UI exists
**Journeys affected:** 3

Design doc journey 3 describes "send guardian invitation" as a normal one-off staff action after creating a household. Verified missing on **both layers**:
- Frontend: `HouseholdDetailPage.tsx`'s "Add adult" flow has zero invite code. `features/organizations/InvitationsPanel.tsx` (the "Members" tab) only supports staff roles (ADMINISTRATOR/TEAM_ADMINISTRATOR/TOURNAMENT_ADMINISTRATOR/VIEWER) — no GUARDIAN option.
- Backend: grepped `backend/src/main/kotlin` for a single-guardian invitation endpoint — none exists. The only guardian-invitation code path is inside the bulk CSV onboarding import (Phase 16).

Today, inviting one newly-created household's guardian isn't reachable through the product at all outside of building a one-row CSV and running it through bulk import. **Confirm with the founder whether this was an intentional scope cut (CSV-only) or a genuine miss.**

### 6. Inconsistent handling of "optional sub-resource absent" (503) across features
**Where:** e.g. `GET /organizations/{id}/fee-assignments/{id}/payment-plan`, `GET .../dashboard/coach/fundraising-progress`, `GET .../payout-account`

The backend consistently uses `503` to mean "this specific optional sub-resource doesn't exist for this entity" (no payment plan configured, no active fundraiser, Stripe not connected) rather than a real service outage. That convention itself is debatable but consistent. The **frontend handling of it is not**: for fundraising-progress, a 503 renders a friendly "No active fundraiser" empty state; for a fee assignment with no payment plan, the identical semantic renders an alarming red "Could not load the payment plan. Try again" error banner. Same meaning, inconsistent presentation. Low severity, but worth a pass for consistency.

### 7. Staff RSVP detail shows raw participant ID, not name
**Journeys affected:** 12

On the event detail page's RSVP panel, staff (with `event.rsvp.read_team`) see individual responses as **"Participant 00000000"** (a truncated UUID) instead of the athlete's name — confirmed live with Maya Johnson's ATTENDING response. The entire point of staff-visible individual RSVPs is knowing who is/isn't coming; an opaque ID defeats that. Likely a display-layer gap (participant name is available elsewhere in the app, e.g. roster summary) — not a data problem.

---

## Low / cosmetic

### 7. Sign-in page has unwanted horizontal scroll on first render
The `/auth/sign-in` page (and the invitation-accept page in one observed instance) sometimes renders with the whole layout shifted right, clipping the sign-in card off the visible viewport at standard desktop width, requiring a horizontal scroll to see the full card. Scrolling right reveals the card renders correctly once in view — layout itself isn't broken, just initial positioning/overflow.

### 8. Auto-generated event title is ambiguous for Practice/Meeting types
A Practice-type event created with no custom title displays as just the team name (e.g. "Varsity Soccer") as its page heading — indistinguishable at a glance from a team page or a game. The type ("PRACTICE") only shows as a small subtitle. Consider a generated title like "Varsity Soccer Practice" when no custom title is given, matching the "Competition titles" convention already spec'd in §14.1A for games.

---

## Confirmed working well (positive findings, for context)

- **Event creation → notification → dashboard visibility → RSVP → staff notification**, the full loop the founder specifically asked to verify, works correctly end-to-end: `event.created` fires to the correct team-roster guardian; the event shows correctly on the guardian's Overview card and the coach's dashboard; RSVP submission correctly resolves source (`GUARDIAN`) and updates aggregate + individual counts; `event.rsvp_changed` correctly notifies staff (not the family) with accurate before/after data.
- **Maps integration**: "Open directions" produces a real, correct, keyless Google Maps link from the event's public address — and correctly never leaks the private meeting-point/directions-notes fields.
- **ICS calendar export**: spec-correct RFC 5545 output (proper line-folding, escaping, UTC timestamps).
- **Organization Profile save** (Settings → Organization Profile) works correctly — confirms the Public Pages bug above is isolated, not systemic.
- **Household creation, adding a household adult** works correctly.
- **Staff invitation creation + existing-user acceptance** works correctly end-to-end, including correct duplicate-membership rejection.
- **Fee template creation, assignment to a household/participant (with auto-fill from template), and manual/offline payment recording** all work correctly, with correct balance math at both the fee and household level, correct status transitions (Open → Partially paid), and a proper Void action rather than editable history.
- **Fundraising campaign creation, publish, and public page** all work correctly (clean public page with progress bar and contribution form). Attempting a real contribution correctly reaches the backend and fails **gracefully** with "We couldn't start checkout. Please try again." given no Stripe key is configured locally — no crash, no stack trace leaked to the user.
- **Store creation, product creation, and the Printify variant form** work correctly and degrade gracefully: the print-provider/size dropdowns are correctly empty (rather than crashing) since they require a live Printify catalog call this environment has no credentials for.
- **Sponsorship package creation, publish, and the QR-code/share-link feature** all work correctly — a real QR code and working share URL are generated immediately on creation.
- **Organization Integrations page**: platform-managed providers correctly hidden from org admins; unverified partner connectors (QuickBooks, SportsEngine, GameChanger, MaxPreps) correctly show honest "Not configured"/"Partner access required" states, never fake availability. The two real connectors both work: **ICS feed connect** creates a real connection record; **CSV schedule import** (tested with a real file upload) correctly parsed and created an event ("Created 1, updated 0, unchanged 0."), landing in the DB with the correct `TENTATIVE` status and `(CSV_IMPORT, external_event_id)` dedup identity per spec.
- Owner dashboard (Organization Overview) shows real, correct, live data across every card (financials, team performance, upcoming events, recent activity feed, reports snapshot).
- **Help Center + support case submission**: role-filtered help articles render correctly; submitting a support case works end-to-end (real case ID, immediately visible in "My recent cases").
- **Platform Admin console** (logged in as the seeded platform admin): Overview shows real cross-org data (org count, user count, gross volume, a live, accurate audit feed of everything tested in this session down to the minute); **support case triage** (status/priority/assignment/resolution note) works correctly end-to-end and persists.

---

*All planned QA areas complete: onboarding/public pages, staff/guardian/athlete invitation, fees/payments, fundraising, store/sponsorships, events/RSVP/notifications/maps, integrations (CSV/ICS), and help/support/platform admin.*

## Summary for triage

**Fix first (blocks core flows for a whole persona):**
1. New-user invitation acceptance orphaned (#1) — blocks onboarding any brand-new staff member
2. Guardian portal broken beyond Overview (#2) — blocks the entire guardian-facing product past one screen
3. Public Pages "Create page" never submits (#3) — blocks all public page/QR/marketing functionality
4. Session lost on reload/sign-out with no redirect (#4) — affects every persona, every session

**Fix soon:**
5. No per-household guardian invite UI (#5) — needs a founder scoping decision first
6. RSVP shows raw participant ID instead of name (#6)

**Polish when convenient:**
7. Inconsistent empty-vs-error state for the 503-means-absent pattern
8. Sign-in page horizontal-scroll-on-first-render
9. Ambiguous auto-generated titles for Practice/Meeting events

The good news: everything downstream of these specific breakpoints — event/RSVP/notification wiring, maps, ICS, fee assignment and payment recording, fundraising, store/sponsorship creation, CSV/ICS integrations, help/support, and the platform admin console — is solid and matches the design doc's claims when reachable.