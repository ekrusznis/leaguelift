# ADR-103 — Mobile Parent/Guardian persona, real backend integration

**Status:** Accepted
**Date:** 2026-08-10

## Context

With the Coach persona fully real (ADR-102), the founder's stated sequence continued: Parent/Athlete next, Owner last (larger scope). Parent was picked first between the two — `docs/design/mobile_sample_design.png` has one Parent/Guardian reference screen to build from; Athlete has none yet.

A fresh research pass (not reused from the coach research) mapped the exact Parent-side backend contracts — `ParentDashboardService`, the household/participant/fee/credit/document domains — before writing any code, same discipline as ADR-102. One inaccuracy surfaced and was corrected mid-build: the research report's `DocumentListResponse` shape was described loosely; direct source verification (`document/web/DocumentDto.kt`) showed it's a flat `{items: DocumentResponse[]}` with acknowledgment state fetched via a *separate* per-assignment endpoint, not nested — the types were corrected before any code depended on the wrong shape.

## Decision

**Route architecture — a new problem this phase surfaced:** Coach's tab shell lives at `(tabs)` mapping to `/`. A second persona's tab shell can't also claim `/` — two Expo Router groups both resolving to the same path is a real conflict. Parent's tab shell was namespaced under an explicit `/parent` segment (`app/parent/(tabs)/...`) rather than fighting for root. Coach's existing routes are untouched. Screens generic across personas (Messages, Announcements, Settings, Event Details, RSVP) stay as shared root-level routes reachable from either tab shell — not duplicated.

**Role-routing rewrite — a real bug caught before it shipped:** the initial approach used a declarative `<Redirect href="/parent">` the same way the auth/onboarding gates already did. Declarative `<Redirect>` re-fires on every re-render of its parent, which is fine for auth/onboarding (there's only one valid place to be while signed out) but wrong for role-routing once a role has its own multi-screen tab shell: any background refetch of `/me/dashboard-context` would re-render `RootNavigator` and force a parent back to `/parent`'s Home tab even if they'd since navigated to Calendar or Payments. Replaced with a `useEffect` + `router.replace()` that fires exactly once per resolved role (tracked via a ref, reset on logout so a later login — even as the same role — re-routes correctly). `ROLE_HOME` is now a lookup table (`{COACH: '/', PARENT: '/parent'}`) so adding Athlete/Owner later is a one-line addition, not a rewrite.

**`event-details.tsx` and `EventCard` made persona-agnostic.** They previously read `organizationId` from `useCoach()` (coach-only context), which would throw for a parent. Now reads directly from `/me/dashboard-context` — works for any role. Guardian RSVP submission was added here too: if the signed-in user has linked athletes (`householdId` present), each linked athlete gets an Attending/Maybe/Can't Go control. **Known simplification:** RSVP controls show for every linked athlete regardless of whether that specific athlete is actually on the event's team — the household-events endpoint already scopes *which events appear* to the household's real team memberships, so a parent only reaches this screen for genuinely relevant events, but the app doesn't cross-reference team membership per-athlete before rendering the picker. Submitting for an unrelated athlete would be caught (if at all) by the backend's own authorization, not the mobile UI.

**RSVP `source` is fully backend-resolved** — confirmed via `EventRsvpService.resolveSource`: the client never sends who's RSVPing beyond `participantId`; the backend infers SELF/GUARDIAN/ADMIN from the caller's real relationship to that participant. Nothing to build client-side here beyond calling the same endpoint the coach persona already uses.

**Real API layers added**, matching backend DTOs field-for-field (verified against Kotlin source): `household` (linked athletes, participants, household record), `fees` (outstanding balance summary + itemized fee-assignment list + per-fee payment history), `credits` (family credit balance — the richer `/credits/balance` shape was preferred over the narrower, `isDemoData`-flagged dashboard-card shape), `documents` (list + acknowledge, explicitly **not** Phase 31 eligibility/waivers — that domain still doesn't exist on the backend at all, confirmed again this pass).

**Screens built**: Parent Dashboard (linked athletes, upcoming family schedule, outstanding balance, announcements — mirrors Coach Dashboard's structure), Family Calendar (real combined `/households/{id}/events`, a genuine server-side union across every linked athlete's teams — no client-side merging needed), Payments (outstanding balance + itemized fees + family credit balance), Fee Details (per-fee payment history, pushed screen), Documents (list + acknowledge). Messages, Announcements, and Settings needed zero changes — they were already caller-scoped (`/me/...`), not coach-specific.

**A real code-quality catch mid-build**: an early draft of the Documents screen used an inline `require()` to sidestep an imagined circular-import concern that didn't actually exist (screens importing from `features/` is the established one-directional pattern everywhere else). Fixed to a normal top-level import once actually checked.

**Verification**: `npm run typecheck`, `npm run lint` (React Compiler purity rules included), and `npx expo-doctor` (18/18) all pass clean, same bar as every prior mobile ADR.

## Consequences

- Adding Athlete (next) or Owner (after) now has a proven pattern to follow: new `/athlete`, `/owner` route segments, a persona-scoped context provider, real API layers researched against actual backend source before writing types, generic screens reused rather than duplicated.
- The RSVP-picker-shows-all-linked-athletes simplification (not filtered to the event's actual team) is a real, acknowledged gap — worth tightening if it causes real confusion once tested, not urgent enough to block this pass.
- Family credit *application* to a fee (`POST .../credits/apply`) and P2P transfer (`POST .../credits/transfer`) are real, confirmed endpoints but weren't built this pass — Payments is read-only (balances + history), not yet an action surface. A natural next slice within Parent, not blocking Athlete from starting.
- No payment-collection flow (Stripe Checkout, applying a card) was built — Payments shows what's owed, it doesn't yet let a parent pay from the app. Real scope call for a future slice, not silently implied as done.
