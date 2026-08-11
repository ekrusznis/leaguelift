# ADR-102 — Mobile real backend integration (coach persona)

**Status:** Accepted
**Date:** 2026-08-10

## Context

ADR-101's coach screen set ran entirely on static mock data, deliberately, since no mobile auth existed. The founder then asked to do this "properly": real login for any role, real DTOs/contracts matching the actual backend, and full coach-persona polish (pages, settings, modals, dialogs, toasts) before moving to Parent/Athlete personas, with Owner last given its larger scope.

Before writing any mobile code, a research pass mapped the exact backend contracts (paths, request/response DTOs) rather than guessing from the earlier mock data's shape. Two real backend gaps surfaced and were resolved as founder decisions, not silently worked around:

1. `CoachDashboardService`'s `.../coach/announcements` and `.../coach/required-actions` cards are **100% hardcoded demo data** server-side, not backed by any table — despite a real, separate Communication/Announcement domain existing (`/me/announcements`). **Decision: use the real domain, skip the fake dashboard cards.**
2. The backend `Participant` model has **no "position"/jersey-number field at all** — that was invented for `docs/design/mobile_sample_design.png`'s mockup. **Decision: drop it entirely, don't fake a client-only field.**
3. There is **no refresh-token endpoint anywhere in the backend** — sessions are pure access-token-expires-in-~60-minutes-then-force-relogin, same limitation the web app already has and works around the same way. **Decision: match the web app exactly, no new backend work.**

## Decision

**Auth** (`src/features/auth/`): `POST /auth/login` → `{accessToken, tokenType, expiresIn, user}`; `GET /me` to validate a stored token and re-hydrate the user on app restart. Token + computed `expiresAt` + user stored via **`expo-secure-store`** (Keychain/Keystore-backed), not AsyncStorage — this is a credential, not app preference data (AsyncStorage remains correct for the onboarding-seen flag, which isn't sensitive). No silent refresh: `apiClient.ts` gained a `registerUnauthorizedHandler` seam (mirroring the existing `registerAccessTokenGetter`) that `AuthContext` wires to `logout()`, so any `401` anywhere in the app forces back to `/login`, matching `frontend/src/auth/AuthContext.tsx`'s own documented behavior exactly.

**Role routing**: `GET /me/dashboard-context` resolves `{role, organizationId, householdId, tournamentId}`. Root layout gates in order: onboarding (first launch) → auth (signed in) → role (`COACH` → tabs; anything else → a real `/role-not-available` screen with the user's name, resolved role label, and a working sign-out — not a broken/empty coach UI). A successful login as any real Rally26 role now works end-to-end; only the Coach experience is built out.

**Real API layer**, one `types.ts` + `api.ts` per feature under `src/features/`, each typed to match the exact backend DTO (field-for-field, confirmed against the real Kotlin source, not paraphrased):
- `teams` — `GET .../dashboard/coach/teams` (the coach's real assigned teams, not the org-wide team list)
- `events` — `GET /teams/{teamId}/events`, `GET .../events/{id}`, RSVP summary/submit (`GET/PUT .../events/{id}/rsvps`, `.../participants/{id}/rsvp`)
- `messaging` — `/me/message-threads` (inbox), `/me/message-threads/{id}/messages`, reply, mark-read — the caller-scoped surface, not the staff-management `MessageThreadController` surface
- `announcements` — `/me/announcements`, mark-read — the real Communication domain
- `roster` — `.../teams/{id}/participants`, explicitly documented server-side as "a coach's team roster picker"
- `settings` — copied field-for-field from `frontend/src/features/settings/` (same `/me/preferences`, `/me/notification-preferences`, `/me/sms-consent` endpoints)

`@tanstack/react-query` added (matching the web app's data layer) with a shared `queryClient.ts`; `apiClient.ts`'s `ApiError` now carries the real `{code, message, requestId, fieldErrors}` shape instead of an untyped body.

**Shared "which team" state** (`CoachContext.tsx`): a coach may have multiple teams; Dashboard, Calendar, and Teams all need to agree on which one is selected. Defaults to the first team (matching the backend's own default — coach-dashboard endpoints default `?teamId=` to the alphabetically-first accessible team) with a switcher modal when there's more than one. Implemented as **derived state at render time** (`override ?? teams[0]`), not an effect that calls `setState` — the first draft used an effect and both this and a `Date.now()`-in-`useMemo` call on the Dashboard were caught by React Compiler's purity lint rules (`app.config.ts`'s `experiments.reactCompiler: true`) and fixed properly rather than suppressed: the timestamp is captured once via a lazy `useState` initializer, and the team selection has no effect at all.

**Shared UI infrastructure** (`src/components/`): `Toast`/`useToast()` (lightweight, no external dependency), `Modal` (bottom-sheet style, RN's built-in `Modal`), `ConfirmDialog` (centered, used for the real Log Out confirmation), plus `LoadingState`/`ErrorState`/`EmptyState` mirroring the frontend's own state-component pattern — every rewired screen now has real loading/error/empty states, not just a happy path.

**Screens rewired to real data**: Dashboard (real teams/schedule/announcements + team switcher), Calendar (real events, real month-grid math unchanged from ADR-101, agenda grouped by the event's own IANA timezone via a new `eventFormat.ts` helper — not the device's), Event Details (real event + real RSVP summary; Edit shows an honest "not available yet" toast rather than a fake form; Share uses RN's real `Share` API), Messages (real inbox list + real thread detail with working send, reply respects the backend's `canReply` flag), Announcements (real list + a new Announcement Details screen + real mark-read), Team Roster (real participants, no position field), Settings (real appearance/notification-preference/SMS-consent wiring, real Log Out via `ConfirmDialog`).

**Verification**: `npm run typecheck`, `npm run lint` (including the React Compiler purity rules), and `npx expo-doctor` (18/18) all pass clean.

## Consequences

- `mockData.ts` is deleted — nothing in the coach screen set uses static content anymore.
- Login works for any real Rally26 account/role; only `COACH` reaches a built experience. Parent/Athlete/Owner personas remain genuinely unbuilt (`/role-not-available` is honest about this, not a silent failure).
- Editing an event from the mobile app is not yet possible — the Edit button is real UI with an honest toast, not a stub form. A future slice should either build it or the button should be removed if it's decided out of scope.
- The Appearance setting saves to the real backend but the app itself doesn't yet visually respond to it (still forced-dark per ADR-101) — the Settings screen says so directly rather than implying it works.
- The React Compiler purity violations this pass fixed (impure-function-in-render, setState-in-effect) are a preview of constraints the rest of Parent/Athlete/Owner screen work will need to keep satisfying — worth remembering as a pattern, not a one-off.
- No refresh-token backend work was done (founder decision) — mobile sessions expire on the same ~60-minute clock as web, forcing re-login. If this becomes a real UX complaint once real users are on it, that's a backend change (a real refresh-token endpoint), not something mobile can work around alone.
