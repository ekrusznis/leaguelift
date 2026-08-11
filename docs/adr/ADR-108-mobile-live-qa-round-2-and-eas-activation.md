# ADR-108 — Live QA round 2 (Owner/Parent messaging & safety) and real EAS project activation

**Status:** Accepted
**Date:** 2026-08-11

## Context

Continuing the same live device/emulator QA loop from ADR-107, the founder called out three more gaps while testing, explicitly deferred to this next round rather than interrupting the round-1 push, plus asked to confirm a fourth open question from round 1 (TeamSnap/SportsEngine season data). Separately, this round also produced the first real installable APK: `eas init`/`eas build` were run against the actual EAS account for the first time, surfacing and fixing a real project-linking mismatch.

## Decision

**Owner needed event create/edit for teams, not just org-wide visibility.** Owner's Team Detail screen gained a "New Event" header action (pushes to the existing shared `event-form.tsx` with that team's id) and Owner Dashboard's Upcoming Events cards became tappable through to Event Details, where the Edit button was already permitted for `role === 'OWNER'` but had no entry point to reach it from.

**Owner needed messaging to coaches/parents/whole teams, not just org-wide broadcasts.** Team Detail also gained a "Message Team" header action, reusing the exact same `message-compose.tsx` Coach already used (team-scoped `CreateMessageThreadRequest`/`CreateConversationRequest`) — no new backend needed, already proven possible during Coach's compose build (ADR-107). The shared compose screen also gained the missing "Coaches & Staff" (`STAFF`) audience option, so "coaches, parents, or entire teams" is now fully covered for both Coach and Owner.

**Parent messaging needed the same safety processes web already has for household athletes.** Built `safety-controls.tsx` — real guardian communication controls (stop staff→athlete messages, or all messaging, for a linked athlete; view/lift existing restrictions) using real backend endpoints (`/me/messaging/guardian-participants`, `/me/messaging/contact-restrictions`) that already existed and already had a real web UI (`GuardianMessageSafetyControls` in `frontend/src/features/messaging/SafeSportPolicyPanel.tsx`) but no mobile equivalent until now. Entry point: Parent's More menu → "Messaging Safety."

**TeamSnap/SportsEngine season-data question resolved, no build needed.** A source read confirmed no real TeamSnap/SportsEngine sync writes `team.season` anywhere — `"season"` only appears inside `SportsDataProviderClient.kt`'s deterministic local stub/fixture data, never in a real write path, matching Phase 19's documented review-only external-ID mapping scope. Owner Teams' season display (ADR-105) is already correctly wired to the real `team.season` column; it's empty until an owner manually enters it, which is honest, not a bug.

**Real EAS project activation.** `eas init --id <uuid> --force` hit an eas-cli internal bug (`Cannot read properties of undefined (reading 'CommonJS')` while parsing `app.config.ts` — confirmed not a real config error, since `npx expo config --type public` loaded fine), worked around by editing `app.config.ts`'s `extra.eas.projectId` directly. A subsequent build then failed with a slug mismatch ("Slug for project identified by extra.eas.projectId (rally) does not match the slug field (rally26)") — fixed by changing `app.config.ts`'s `slug` from `'rally26'` to `'rally'` to match what the EAS project was actually created as. Two successful `eas build --profile preview` runs produced real installable APKs, verified by direct binary inspection (`aapt dump badging`/`aapt dump xmltree`, asset extraction) — package name, permissions, adaptive icon layers, and bundled art all confirmed real.

**Verification:** typecheck/lint (including purity rules)/expo-doctor all clean, same bar as every prior mobile ADR.

## Consequences

- All items explicitly deferred from ADR-107's QA round are now closed except company-wide (Rally26-the-company-to-user) notifications, which stays open and lower-priority — it needs real scoping (authoring model, in-app inbox versus push, any existing Platform Admin mechanism to build on) before any build starts.
- `preview` build profile (installable APK) versus `production` (AAB, Play-submission format only, not directly installable) is now a load-bearing distinction for every future build — flagged explicitly to avoid a future build producing a file that can't actually be side-loaded for testing.
- The mobile app now has a proven, repeatable path from source to a real device-installable artifact, unblocking regular QA builds going forward.
