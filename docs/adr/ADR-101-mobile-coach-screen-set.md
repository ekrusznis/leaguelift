# ADR-101 — Mobile coach-persona screen set from design reference

**Status:** Accepted
**Date:** 2026-08-10

## Context

The founder added `docs/design/mobile_sample_design.png` — a generated reference design covering the splash screen, a 3-slide first-launch onboarding flow, and a full coach-persona screen set (Dashboard, Calendar, Team Chat, Event Details, Announcements, Team Roster, and one Parent/Guardian view), all in a consistent dark-navy/orange Rally26 brand treatment with a 5-tab bottom navigation (Home, Calendar, Teams, Messages, More).

Asked how far to take this, the founder chose the largest option: build out the full coach screen set now, as real screens in the `mobile/` scaffold from ADR-100 — not just capture it as reference material for later Phase 33 planning work.

## Decision

Built all of it as real, navigable screens:

1. **Onboarding gate** (`src/lib/onboarding.ts`, `@react-native-async-storage/async-storage`) — first launch only, persisted across app restarts. Root `_layout.tsx` checks status before rendering the main `<Stack>`, using `<Redirect href="/onboarding" />` while unseen (Expo Router's documented auth-gate pattern).
2. **Navigation restructure** — moved from the prior 2-tab scaffold (Dashboard/Settings) to the real 5-tab structure the mockup shows: `(tabs)` route group with `index` (Dashboard), `calendar`, `teams`, `messages`, `more`; `event-details`, `announcements`, and `settings` live as root-level pushed stack screens reachable from any tab.
3. **Forced dark-navy theme** — the mockup shows a consistent brand-forward dark UI, not "whatever the OS is set to." `src/hooks/use-theme.ts` now always returns `Colors.dark` and the root layout forces `ThemeProvider value={DarkTheme}`, both explicitly documented as provisional — real System/Light/Dark alignment with Phase 28's account appearance preference is still a Phase 33 §32.1 design-system task, not decided here.
4. **Tab icons via `@expo/vector-icons`** (Ionicons), using `NativeTabs.Trigger.VectorIcon` — Expo's own documented pattern for cross-platform tab icons without shipping new binary icon assets. Removed the two placeholder PNG tab icons the ADR-100 scaffold used.
5. **Static mock data** (`src/constants/mockData.ts`) shaped close to the real backend DTOs (so swapping in real `apiFetch` calls later is mechanical, not a redesign) but **not wired to any real endpoint** — matching content from the reference design (Tigers Volleyball, Coach Anna, the practice/game/meeting events, the roster). This is a deliberate choice, not a shortcut: Phase 33's own stated boundary is "does not add production mobile-only business logic merely to make mockups work," and no mobile auth/session exists yet (Phase 33 §32.2, not started) to fetch a real signed-in coach's real team data. The one exception is the Dashboard's backend-connectivity check (`GET /public/status`, from ADR-100) — kept as-is since it needs no auth.
6. **New shared components**: `Button` (primary/secondary, matches the mockup's orange/outlined buttons), `ScreenHeader` (back button + title, used by the three pushed screens), `EventCard` (shared between Dashboard's "Upcoming" list and Calendar's agenda list).
7. **Real month-grid calendar math** (`src/lib/calendarGrid.ts`) — not a static image of a calendar; day-of-week alignment, adjacent-month padding, and today/selected-day state all computed for real.

**Verification:** `npm run typecheck`, `npm run lint`, and `npx expo-doctor` (18/18) all pass clean, same as ADR-100. Route-string correctness (`/event-details`, `/(tabs)/calendar`, etc.) is additionally checked at compile time via `app.config.ts`'s `experiments.typedRoutes: true` — every `router.push`/`router.replace` call in this screen set is a verified-valid route, not just a string that happens to work.

## Consequences

- The coach persona now has a real, navigable, visually faithful screen set — a strong reference implementation for the still-unstarted Phase 33 §32.3 page-spec work (which now has a concrete pattern to generalize from, not just a blank page).
- Athlete, Guardian/Parent, and Owner persona screen sets are explicitly **not built**. The reference design includes one Parent/Guardian screen as a starting point; Athlete and Owner have no reference mockup at all yet. This was a deliberate scope boundary (the founder's own framing: "we can make similar ones for athlete, parent, and owner" — future work, not this pass).
- No screen in this set is backend-wired beyond the pre-existing status check. Real data wiring is blocked on Phase 33 §32.2 (auth/session) not yet existing — attempting it now would mean building ad hoc, throwaway auth just to unblock mock screens, which is worse than clearly-labeled mock data.
- The forced-dark-theme decision in `use-theme.ts`/`_layout.tsx` is provisional and documented as such in three places (this ADR, the hook's own comment, the layout's own comment) so a future session doesn't mistake it for a settled design decision.
- `docs/design/mobile_sample_design.png` is now the canonical visual reference for the coach persona — future sessions building out Athlete/Guardian/Owner screens should look for updated reference art before inventing a new visual language.
