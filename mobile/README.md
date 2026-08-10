# Rally26 Mobile

Native Android/iOS client — React Native + Expo + TypeScript. Sibling to `backend/` and
`frontend/` in the same monorepo, not nested inside either. Reuses the existing Spring
Boot/OpenAPI backend as the sole source of truth for authorization, privacy, money,
audit, messaging safety, eligibility, and organization isolation — this app does not
implement a second business-rule layer, and it is not a WebView wrapper around
`frontend/`.

Full plan: `DESIGN-DOC.md` §14.1N (Phase 33 — planning) and §14.1's Phase 36 entry
(reserved — real implementation/build). Detailed projection:
`docs/PHASE33-MOBILE-APPLICATION-PLANNING-PROJECTION.md`.

**Status:** three personas are real and backend-wired — **Coach** (ADR-102),
**Parent/Guardian** (ADR-103), and **Athlete** (ADR-104). Real login for any Rally26
role; only `COACH`/`PARENT`/`ATHLETE` reach a built experience, everything else lands
on an honest `/role-not-available` screen. Secure token storage, real loading/error/
empty states throughout, no mock data anywhere. Coach lives at `/` (5 tabs:
Home/Calendar/Teams/Messages/More), Parent at `/parent` (5 tabs: Home/Calendar/
Payments/Messages/More), Athlete at `/athlete` (4 tabs: Home/Calendar/Messages/More —
no Teams tab, since athletes get a real backend 403 on teammate-roster viewing, and no
Payments tab, since athletes hold no financial capability at all) — Messages,
Announcements, Settings, and Event Details/RSVP are shared screens, not duplicated per
persona. Owner persona is not built yet (explicitly last per founder sequencing).

## Structure

```text
mobile/
  src/
    app/                    Expo Router routes (file-based)
    components/              Shared UI. Platform-specific code uses Metro's
                              built-in .ios.tsx / .android.tsx suffix convention —
                              see platform-status-spacer.{tsx,ios.tsx,android.tsx}
                              for a real (not toy) example of when/why to split.
    constants/theme.ts        Rally26 brand tokens, mirrored from
                              frontend/src/styles/tokens.css — not a second palette.
    hooks/
    lib/
      apiClient.ts             Thin fetch wrapper, mirrors frontend/src/lib/apiClient.ts's
                              shape. Never invent an endpoint here beyond
                              docs/openapi.yaml (DESIGN-DOC.md §17.2).
      env.ts                  Reads EXPO_PUBLIC_* vars (Expo's built-in .env support).
  eas.json                    EAS Build profiles: development/preview/production.
  app.config.ts                Expo config (bundle IDs, plugins, EAS project ID).
  scripts/build-release.sh     Wraps `eas build` to produce a real installable artifact.
```

No `ios/`/`android/` native project folders are committed — this is the managed
workflow; EAS generates them at build time. Run `npx expo prebuild` locally only if a
config plugin genuinely needs inspecting.

## Screens

Shared across every persona:

| Route | Screen | Data |
|---|---|---|
| `/login` | Sign in | Real `POST /auth/login` |
| `/onboarding` | First-launch walkthrough | 3-slide carousel, AsyncStorage-gated, shared via `OnboardingContext` so the root gate sees updates immediately |
| `/role-not-available` | Non-built roles | Real `GET /me/dashboard-context`; any role without a built persona lands here with a working sign-out |
| `/messages/[threadId]` | Thread detail | Real messages + working send (respects `canReply`) + mark-read |
| `/guardians` | My Guardians (Athlete only) | Real `/me/dashboard/athlete/guardians` |
| `/event-details?id=` | Event Details | Real event + real RSVP summary; guardians linked to the household get an Attending/Maybe/Can't Go picker per athlete, and an athlete viewing their own event gets the same picker for themself (`source` resolves to `SELF` automatically, resolved via `GET /me/contexts`); Edit is an honest "not available yet" toast; Share uses RN's real Share sheet |
| `/announcements` | Announcements | Real `/me/announcements`, All/Unread filter, mark-read on open |
| `/announcement-details?id=` | Announcement detail | Full body, not truncated |
| `/settings` | Settings | Real appearance/notification-preference/SMS-consent, real Log Out via `ConfirmDialog` |

Coach persona (`/`, ADR-102) — 5 tabs: Home/Calendar/Teams/Messages/More:

| Route | Screen | Data |
|---|---|---|
| `/(tabs)` → `index` | Dashboard | Real coach teams + team schedule + `/me/announcements` preview; team switcher modal if >1 team |
| `/(tabs)` → `calendar` | Calendar | Real month-grid math + real team events, grouped by the event's own IANA timezone |
| `/(tabs)` → `teams` | Team Roster | Real participants (no "position" field — doesn't exist on the backend) |

Parent/Guardian persona (`/parent`, ADR-103) — 5 tabs: Home/Calendar/Payments/Messages/More:

| Route | Screen | Data |
|---|---|---|
| `/parent/(tabs)` → `index` | Dashboard | Real linked athletes + family schedule + outstanding balance + announcements preview |
| `/parent/(tabs)` → `calendar` | Family Calendar | Real `GET /households/{id}/events` — a genuine server-side union across every linked athlete's teams, not client-merged |
| `/parent/(tabs)` → `payments` | Payments | Real outstanding balance, itemized fee list, family credit balance (read-only — no in-app payment collection yet) |
| `/fee-details?id=` | Fee Details | Per-fee payment history |
| `/documents` | Documents | Real household document list + acknowledge (plain upload/ack flow — not Phase 31 eligibility/waivers, which doesn't exist on the backend) |

Athlete persona (`/athlete`, ADR-104) — 4 tabs: Home/Calendar/Messages/More:

| Route | Screen | Data |
|---|---|---|
| `/athlete/(tabs)` → `index` | Dashboard | Real `/me/dashboard/athlete/overview` + `/teams`, plus own upcoming schedule via `/participants/{id}/events` |
| `/athlete/(tabs)` → `calendar` | Calendar | Real own-schedule month-grid, same math as Coach/Parent |
| `/athlete/(tabs)` → `messages` | Messages | Shared thread list + a "New Conversation" action unique to Athlete |
| `/athlete/new-conversation` | New Conversation | Real SafeSport-gated flow: pick an enabled team → eligible contacts → compose, via `/me/messaging/athlete-teams`/`athlete-contacts`/`athlete-conversations` |

Owner variant isn't built yet — deliberately last per founder sequencing (it has more surface area than the other three combined).

## Local development

```bash
cd mobile
npm install
cp .env.example .env.local   # already present with a working localhost default
npm start
```

Then press `a` (Android emulator), `i` (iOS simulator — macOS only), or scan the QR
code with Expo Go / a development build on a physical device.

**Physical device caveat:** `EXPO_PUBLIC_API_BASE_URL=http://localhost:8080/...` only
reaches the backend from an emulator/simulator on the same machine. On a physical
device, replace `localhost` with your machine's LAN IP (Expo prints this in the QR
code screen) or use `npx expo start --tunnel`.

## First-time EAS setup (one-time, per Expo account)

Nothing above requires real Expo/Apple/Google credentials. Real device/store builds do:

```bash
npm install -g eas-cli
eas login                # real Expo account
eas init                 # creates a real project, prints a projectId
```

Paste the printed `projectId` into `app.config.ts`'s `extra.eas.projectId` (currently
`REPLACE_WITH_REAL_EAS_PROJECT_ID`) before running a real build.

## Producing a real distributable build

```bash
./scripts/build-release.sh android preview       # installable APK
./scripts/build-release.sh android production     # AAB for Google Play
./scripts/build-release.sh ios preview            # signed IPA (ad-hoc/internal)
./scripts/build-release.sh ios production          # signed IPA for App Store/TestFlight
```

First real Android/iOS build prompts EAS to generate or reuse signing credentials —
see [docs.expo.dev/app-signing/app-credentials](https://docs.expo.dev/app-signing/app-credentials/).
iOS builds require an Apple Developer Program account; Android AAB submission requires
a Google Play Console account. Neither is configured yet — that's founder-owned setup,
not something this scaffold can do for you (mirrors how Printify/Stripe credential
rotation is founder-owned per `DESIGN-DOC.md`).

**Known limitation:** `eas.json`'s `preview` and `production` profiles currently point
at the same backend (`https://api.rally26.com/api/v1`) because Rally26 has no dedicated
staging environment yet (ADR-061 — single-environment by design through initial
launch). Revisit this once/if a staging environment exists.

## Conventions

- **Platform-specific code:** only split into `.ios.tsx`/`.android.tsx` when there's a
  real, documented reason (see `platform-status-spacer.*`) — not preemptively.
- **Shared with `frontend/`:** API contracts/types/constants may be shared where
  genuinely reusable; UI and navigation are always native, never generated from or
  wrapping the web DOM tree (`DESIGN-DOC.md` §14.1N).
- **Design tokens:** add new colors to `frontend/src/styles/tokens.css` first, then
  mirror into `src/constants/theme.ts` — never invent a mobile-only brand color.
- **Auth:** real — `src/features/auth/AuthContext.tsx`, `expo-secure-store`-backed, no
  refresh-token flow (none exists on the backend); any `401` forces back to `/login`,
  matching `frontend/src/auth/AuthContext.tsx`'s own documented behavior.
