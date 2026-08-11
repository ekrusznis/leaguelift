# ADR-107 — Live device/emulator QA round 1: real theme, art, event CRUD, messaging

**Status:** Accepted
**Date:** 2026-08-10/11

## Context

The founder got a real Android emulator (Pixel_XL_API_31) and a physical phone connected via USB debugging working, both through `npx expo run:android` rather than Expo Go — `react-native-webview` (ADR-106) is not Expo-Go-compatible, which was the actual cause of an early "app not currently on my device" QR-scan error: the project needs a real custom dev client. This also surfaced a real networking gotcha: the Android Emulator needs `10.0.2.2`, not `localhost`, to reach the host machine — iOS Simulator's `localhost` shortcut does not apply to Android; `mobile/.env.local` and the README were both wrong or incomplete on this before now.

With a real device in hand, live QA began, and each finding below was fixed the same session rather than logged for later.

## Decision

**Real splash and onboarding art** replaced the Ionicons/color-block placeholder from ADR-101 — the founder supplied `docs/design/splash1-4.png` (splash1 is the full splash background with wordmark/tagline baked in; splash2-4 are the 3 onboarding illustrations, not additional splash screens).

**Real app icon** generated from the existing web `frontend/public/favicon.svg` (navy square, orange arrow, white "R") using a temporary `sharp` install in the scratchpad, since no SVG rasterizer was available locally. The Android icon and all 3 adaptive-icon layers were verified by compositing onto a visible backdrop before trusting them — transparent PNGs preview as blank against a white viewer background, not actually broken. The iOS Icon Composer bundle (`assets/expo.icon/`) was updated in good faith but could not be visually verified — no local tool renders that newer Xcode format — flagged as needing a real Xcode/iOS check before being trusted.

**Real light/dark/system theme.** `useTheme()` was hardcoded to always return dark (ADR-101's own documented provisional state); replaced with a real `AppThemeProvider` Context resolving the account's saved Settings appearance preference against the OS scheme via `useColorScheme()`, wired into React Navigation's theme at the root layout too. Known, undone follow-up: several screens still hardcode dark-only hex colors in their own StyleSheets (chips, message bubbles) — the same class of gap already tracked for web (dark-mode full inversion), not yet audited on mobile.

**Settings screen wasn't scrollable** — a real bug (a missing `ScrollView`, not "just how the layout ends" as initially guessed) — fixed.

**Logout redirect verified already correct on both platforms**, no code change needed — mobile's root `<Redirect href="/login">` on `user === null` and web's `ProtectedRoute` redirect to `/auth/sign-in` both already worked.

**Real event create/edit** (`event-form.tsx`, one screen for both modes). A real React Compiler purity violation was caught the same way earlier ADRs did — hydrating edit-mode state via `useEffect`+`setState` instead of deriving it at mount — fixed by splitting into an outer component that gates on the query's loading state and an inner `EventFormFields` component that only mounts once data is ready, so `useState` initializers can read the loaded event directly with no effect at all. Create always auto-publishes (no separate draft-then-publish step exposed to the coach), matching how the rest of the app treats "create" as "make it real."

**Real Directions button** on Event Details using the backend's own existing keyless Google Maps directions endpoint (ADR-028) — `GET .../events/{id}/directions` returns a ready-made URL, opened via `Linking.openURL`; no client-side maps URL construction needed.

**Real Coach message compose** (`message-compose.tsx`). "Full Team"/"Parents Only" reuse the exact same team-scoped broadcast-thread endpoints Owner's compose (ADR-105) already proved out. "Select Specific People" uses a different, previously-unused endpoint pair (`GET .../message-threads/contacts` + `POST .../message-threads/conversations`) — a source read confirmed the generic broadcast-thread endpoint explicitly rejects `SELECTED` audience (`MESSAGE_THREAD_NOT_BROADCAST` conflict), so targeted messaging is a structurally different "conversation" thread type, not a broadcast audience value.

**New native dependency this round:** `@react-native-community/datetimepicker` (event form date/time pickers) — requires `npx expo run:android`/`ios` again, not just `npm start`, same as `react-native-webview` in ADR-106.

**Verification:** typecheck/lint (including purity rules)/expo-doctor all clean.

## Consequences

- Live device/emulator QA is now the working mode for mobile — future findings get fixed same-session unless explicitly deferred (see ADR-108 for the next round).
- Deferred to the next QA round, not built here: Owner-side event create/edit, Owner messaging to specific teams/coaches/parents, Parent-side SafeSport restriction management, and verifying TeamSnap/SportsEngine season-sync data actually reaches the Owner Teams screen.
- The dark-only-hex-color audit (screens that don't yet respond to the new real theme) remains open, not yet scheduled.
