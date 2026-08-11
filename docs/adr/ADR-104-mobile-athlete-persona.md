# ADR-104 — Mobile Athlete persona

**Status:** Accepted
**Date:** 2026-08-10

## Context

Third persona in the founder's stated build order (Coach → Parent → Athlete → Owner last). Unlike Parent (ADR-103), there was no reference mockup for Athlete in `docs/design/mobile_sample_design.png` to build from — the screen set and scope were derived from real backend capability, not a design reference.

## Decision

**Self-discovery uses a different endpoint than dashboard-context.** An athlete's own `participantId` is not present on `GET /me/dashboard-context` at all. It comes from `GET /me/contexts`, filtering for `contextType === 'ATHLETE'` and reading `resourceId`. New `AthleteSelfContext`/`useAthleteSelf()` provider and a new `useContexts()` hook in `features/dashboard/api.ts`.

**No teammate roster access — a real 403, not a gap to route around.** The generic team-participants endpoint requires `TEAM_VIEW`, a capability athletes don't hold. Athlete's tab shell is **4 tabs, not 5**: Home/Calendar/Messages/More — no Teams tab (no roster access) and no Payments tab (athletes hold no financial capability at all). Coach and Parent's 5th tabs exist because they have real backend access to that domain; a 5th Athlete tab would have been decorative.

**`recent-history`/`orders` dashboard cards are confirmed hardcoded `emptyList()` server-side** (`AthleteDashboardService`) — skipped entirely rather than shipping a permanently-empty card, matching this codebase's established norm of not faking a feature the backend doesn't back yet.

**Calendar and Dashboard reuse the generic `GET /participants/{id}/events` endpoint** (new `useParticipantEvents` hook) rather than the dashboard's own simplified `ScheduleItem`/`week-events` card shape — the same "prefer the richer real `EventResponse` shape" call Parent made over `/households/{id}/events`'s dashboard-card equivalent.

**Self-RSVP reuses the exact same endpoint as guardian RSVP** (`PUT /events/{id}/participants/{participantId}/rsvp`). `event-details.tsx` (already made persona-agnostic in ADR-103) now also derives a self `{participantId, name: 'You'}` row from `/me/contexts` alongside any guardian's linked-athlete rows; `source` resolves to `SELF` automatically server-side.

**Athlete messaging is genuinely new UI, not a reused screen.** On top of the shared inbox (`/me/message-threads`, unchanged), athletes get a real "New Conversation" flow (`/athlete/new-conversation`): pick a team from `/me/messaging/athlete-teams` (each pre-flagged `athleteMessagingEnabled` by the backend's own SafeSport-approval check) → eligible peer contacts from `/me/messaging/athlete-contacts` → compose via `/me/messaging/athlete-conversations`. Teams not yet SafeSport-approved are shown but disabled ("Messaging pending organization review"), so the app never surfaces a raw 409. The 403 `ATHLETE_MESSAGING_RESTRICTED` (a guardian-set restriction) can still occur at the contacts-fetch or create-conversation step and is handled with a specific message, not a generic error toast. `MessagesListScreen` (shared) gained an optional `onNewConversation` prop, only passed by Athlete's tab.

**Verification:** `npm run typecheck`, `npm run lint` (React Compiler purity rules included), and `npx expo-doctor` all pass clean, same bar as every prior mobile ADR.

## Consequences

- Athlete's "fewer tabs than Coach/Parent" pattern establishes the norm going forward: tab count tracks real backend access, not visual parity across personas.
- No teammate roster, no history/orders card, and no financial visibility are real, permanent backend states for this persona — not app-side shortcuts to revisit later.
- Owner (last persona, biggest scope) was next — see ADR-105.
