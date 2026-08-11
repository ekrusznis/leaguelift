# ADR-100 — Phase 33/36 native mobile scaffold kickoff

**Status:** Accepted
**Date:** 2026-08-10

## Context

Phase 33's plan (§14.1N) was reviewed and sharpened earlier the same day (ADR references in that section). The founder then judged native mobile "the largest upcoming phase" and asked to begin the actual `mobile/` module and build infrastructure — real scaffolding, not only planning documents — ahead of the full route-parity inventory (§32.0) and page-by-page specs (§32.3) being completely written out.

This is infrastructure groundwork, not a start on Phase 33's planning deliverables or Phase 36's real screen implementation. Phase 33's planning slices (route parity matrix, native design system, page-by-page specs, security review, etc.) are still open and unstarted; this ADR only covers what now physically exists in the repo.

## Decision

Created `mobile/` as a sibling application to `backend/` and `frontend/`, bootstrapped via `create-expo-app` (Expo's official scaffolding tool) with TypeScript + Expo Router, then substantially customized:

1. **Removed the web platform target entirely** — `frontend/` is already Rally26's web app; a second Expo-web build target would be redundant and risks becoming an accidental unauthorized "WebView-adjacent" surface. Removed `react-native-web`/`react-dom` dependencies and all `.web.tsx` template files.
2. **Rebranded** — app name/slug/scheme, `com.rally26.mobile` bundle identifier (iOS) and package name (Android), Rally26 brand colors (`src/constants/theme.ts`, mirrored from `frontend/src/styles/tokens.css` rather than inventing a second palette) replacing Expo's default template branding/assets.
3. **`app.json` → `app.config.ts`** so environment-driven values (EAS project ID) are computed rather than hand-duplicated, per §14.1N's "environment separation" requirement.
4. **Removed unused template boilerplate** (tutorial hero screen, hint-row, external-link, collapsible components, unused image assets, the `reset-project` script) rather than leaving dead scaffold code in place.
5. **Added a real `.ios.tsx`/`.android.tsx` platform-split example** (`platform-status-spacer.*`) demonstrating a genuine platform difference (iOS's safe-area top inset already reflects the notch/Dynamic Island; some Android OEM skins under-report it, needing a `StatusBar.currentHeight` fallback) — not a toy/contrived split, so it's a real template for when a genuine platform difference is found later.
6. **Added `src/lib/apiClient.ts`/`env.ts`** — a thin fetch wrapper mirroring `frontend/src/lib/apiClient.ts`'s shape and the same `registerAccessTokenGetter` indirection seam, reading `EXPO_PUBLIC_*` env vars. No real secure token storage or auth flow is wired yet — that's Phase 33 §32.2's job. The Dashboard placeholder screen calls the real `GET /public/status` backend endpoint to prove connectivity end-to-end rather than just rendering static mock content.
7. **`eas.json`** with `development`/`preview`/`production` build profiles: Android `apk` for development/preview (installable, direct-test distribution), Android `app-bundle` (AAB) for production (Google Play submission format), iOS signed builds for preview/production (`simulator: false`) — directly satisfying the Phase 36 real-artifact mandate structurally, though no build has actually been run (no real Expo/Apple/Google credentials configured yet — `app.config.ts`'s `extra.eas.projectId` is a placeholder).
8. **`scripts/build-release.sh`** wraps `eas build` with platform/profile arguments and a `--local` flag (Android-only; iOS local builds still require a Mac regardless).
9. **TypeScript/ESLint platform-suffix resolution** — `tsconfig.json`'s `moduleSuffixes` and `eslint.config.js`'s `import/resolver` needed explicit configuration to understand Metro's `.ios.tsx`/`.android.tsx` convention; without both, `tsc --noEmit` and `expo lint` false-positive on every platform-split import. Order matters for `moduleSuffixes` — platform suffixes must be listed before the empty-string fallback to match Metro's actual runtime resolution priority.

**Verification:** `npm run typecheck` (`tsc --noEmit`), `npm run lint` (`expo lint`), and `npx expo-doctor` (18/18 checks) all pass clean. No device/simulator run was performed — that requires either a physical device with Expo Go / a dev client, an Android emulator, or (for iOS) a Mac with Xcode, none of which this session's environment has.

## Consequences

- `mobile/` exists and passes its own typecheck/lint/doctor checks, but contains only a placeholder Dashboard/Settings shell — no real product screens. Phase 33's planning slices (§32.0–§32.7) are still the source of truth for what actually gets built next; this scaffold doesn't preempt or skip that work.
- Real builds (`eas build`) cannot run until a founder runs `eas login`/`eas init` with a real Expo account and pastes the resulting project ID into `app.config.ts`. Apple Developer Program and Google Play Console accounts are separately required before store submission — both founder-owned setup steps this ADR flags but does not perform, mirroring how Printify/Stripe credential rotation is documented as founder-owned elsewhere in `DESIGN-DOC.md`.
- `eas.json`'s `preview` and `production` profiles point at the same backend (`https://api.rally26.com/api/v1`) because Rally26 has no dedicated staging environment yet (ADR-061) — a known, documented limitation, not an oversight.
- The local dev Node version (v20.19.0) is slightly below several packages' declared engine requirement (`^20.19.4`) — `npm install` succeeds with warnings only; worth bumping Node before real device testing to eliminate the warning noise.
