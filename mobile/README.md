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

**Status:** all four personas are real and backend-wired — **Coach** (ADR-102),
**Parent/Guardian** (ADR-103), **Athlete** (ADR-104), and **Owner** (ADR-105). Real
login for any Rally26 role; `COACH`/`PARENT`/`ATHLETE`/`OWNER` all reach a built
experience, everything else (e.g. `TOURNAMENT_ADMIN`, `PLATFORM_ADMIN`) lands on an
honest `/role-not-available` screen. Secure token storage, real loading/error/empty
states throughout, no mock data anywhere. Coach lives at `/` (5 tabs: Home/Calendar/
Teams/Messages/More), Parent at `/parent` (5 tabs: Home/Calendar/Payments/Messages/
More), Athlete at `/athlete` (4 tabs: Home/Calendar/Messages/More — no Teams tab, since
athletes get a real backend 403 on teammate-roster viewing, and no Payments tab, since
athletes hold no financial capability at all), Owner at `/owner` (4 tabs: Home/Teams/
Members/More — `role: 'OWNER'` covers `MembershipRole.OWNER`/`ADMINISTRATOR`/`VIEWER`
alike server-side, so mutating actions rely on the backend's own manager-tier gate
rather than being hidden client-side) — Messages, Announcements, Settings, and Event
Details/RSVP are shared screens, not duplicated per persona.

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

Owner persona (`/owner`, ADR-105) — 4 tabs: Home/Teams/Members/More:

| Route | Screen | Data |
|---|---|---|
| `/owner/(tabs)` → `index` | Dashboard | Real summary/financial-overview/team-performance/upcoming-events/recent-activity/reports-snapshot cards — no "attention required" or "onboarding progress" cards, both unconditionally hardcoded server-side |
| `/owner/(tabs)` → `teams` | Teams | Real org-wide team list, read-only this slice |
| `/owner/team-detail?id=` | Team Detail | Read-only team fields (sport/season/age group/level/colors/status) |
| `/owner/(tabs)` → `members` | Members | Real member list + role update/revoke; mutations rely on the backend's manager-tier 403, not a client-side hide |
| `/owner/reports` | Reports | Real revenue/fee-collections/refunds reports, trailing 30 days (server default) |
| `/owner/payout` | Payout Account | Real Stripe Connect status + balance, read-only — no onboarding-link/transfer actions this slice (money-movement, deferred) |
| `/owner/announcements-manage` | Announcements (manage) | Real list + publish; distinct from the shared recipient-facing `/announcements` |
| `/owner/announcement-compose` | New Announcement | Creates a DRAFT (org-scoped only this slice); publish is a separate step from the list |
| `/owner/broadcasts-manage` | Broadcasts (manage) | Real thread list |
| `/owner/broadcast-compose` | New Broadcast | Creates an org-scoped thread, then hands off to Broadcast Detail to send the first message |
| `/owner/broadcast-detail?threadId=` | Broadcast Detail | Real management-scoped message list + send |

**Deferred, not attempted this slice** (see ADR-105): Rally26 subscription billing,
payout `transfer`/`onboarding-link` actions, org profile/credit-settings edit forms,
Documents (owner-side), team/tournament create/edit/archive, and team-scoped (vs.
org-scoped) announcements/broadcasts. Swag Shop, Fundraising, and Sponsorships are
covered — see WebView embeds below (ADR-106).

## WebView embeds — Swag Shop and Sponsorships (ADR-106)

Rather than rebuild these natively, `/web-embed` (`src/app/web-embed.tsx`) loads the
**real, already-built `frontend/` pages** for these two feature areas inside an
in-app WebView (`react-native-webview`) — a deliberate exception to "not a WebView
wrapper around `frontend/`" (that principle is about the app's overall identity/
navigation, not a blanket ban on embedding one bounded, complex feature where a native
rebuild would be pure duplicate maintenance for no UX gain, e.g. live Printify mockup
preview).

**How auth works, no new backend endpoint:** `useAuth().getWebSession()` returns the
current session in the *exact* JSON shape `frontend/src/auth/AuthContext.tsx` reads
from `sessionStorage` under the key `"rally26.session"` —
`{accessToken, expiresAt, user: {displayName, email}}`. `web-embed.tsx` injects that
via `injectedJavaScriptBeforeContentLoaded` before the page's own JS boots. Verified
directly against `frontend/src/auth/AuthContext.tsx` and `frontend/src/lib/apiClient.ts`
before relying on this: web sends no cookie, no CSRF/double-submit token — the bearer
token in `sessionStorage` is the only thing that gates access, so injecting it is
sufficient; no "exchange token" bridge endpoint was needed on the backend.

**Stripe checkout stays inside the WebView too** — web itself only does
`window.location.href = checkoutUrl` to Stripe's hosted Checkout (no Stripe SDK
anywhere in this codebase), so there's nothing extra to build for that; `web-embed.tsx`
watches for `status=success`/`status=canceled` in the WebView's navigated URL to show a
native toast.

| Route | Screen | Entry points |
|---|---|---|
| `/web-embed?path=&title=` | Real `frontend/` page in a WebView | Owner More: Swag Shop (`/app/organizations/{id}/swag-shop`, management) and Sponsorships (`/app/organizations/{id}/sponsorships`, management). Coach More + Parent More: Swag Shop (`/app/organizations/{id}/swag-shop/order`, the real buyer/personalization/checkout flow) |

**Known limitation:** the authenticated in-app Swag Shop *order* flow's Stripe
success/cancel redirect target is hardcoded server-side to `frontendProperties.baseUrl`
(`OrderService.createSwagShopCheckoutSession`, `OrderDto.kt`'s doc comment confirms no
caller-supplied override exists) — it redirects back to the same `frontend/` page
inside the WebView, which is fine, but there's no way to redirect out to a
`rally26://` deep link instead without a backend change. Sponsorship checkout does
accept caller-supplied `successUrl`/`cancelUrl`, but this slice still routes it through
the WebView (not a native deep-link handoff) for consistency with Swag Shop and to
avoid a second redirect pattern for no real gain yet.

**QuickBooks stays web-only, no mobile screen at all** — not even a WebView link. Its
own Intuit OAuth core is inactive on web too (fails closed,
`QUICKBOOKS_CLIENT_NOT_ACTIVATED`), so there's nothing live to reach from mobile right
now.

**Now built natively on mobile:** Fundraising management (`/fundraising*`), Help
Center (`/help`, `/help/[slug]`), support ticketing (`/support-request`), Action
Center (`/action-center`), owner-side Documents (`/owner/documents`), guardian
messaging-safety controls (`/safety-controls`), and event create/edit (`/event-form`).

**Still missing for full parity:** household media, family credit application /
transfer, owner org-profile / credit-settings edit, and owner team/tournament
create-edit-archive.

## Local development

```bash
cd mobile
npm install
cp .env.example .env.local   # already present with a working localhost default
npm start
```

Then press `a` (Android emulator), `i` (iOS simulator — macOS only), or scan the QR
code with Expo Go / a development build on a physical device.

**`localhost` doesn't mean the same thing on every target — set `.env.local` per platform:**
- **iOS Simulator:** `http://localhost:8080/...` works as-is — the simulator shares the host's network stack.
- **Android Emulator:** `localhost` refers to the emulated device itself, not your host machine — use the special alias `http://10.0.2.2:8080/...` instead (applies to both `EXPO_PUBLIC_API_BASE_URL` and `EXPO_PUBLIC_FRONTEND_BASE_URL`, the latter only mattering once you're inside a WebView embed screen, ADR-106).
- **Physical device:** neither of the above reaches your machine — replace the host with your machine's LAN IP (Expo prints this in the QR code screen) or use `npx expo start --tunnel`.

## First-time EAS setup (one-time, per Expo account)

Nothing above requires real Expo/Apple/Google credentials. Real device/store builds do:

```bash
npm install -g eas-cli
eas login                # real Expo account
eas init                 # creates a real project, prints a projectId
```

**Done as of 2026-08-11** — `app.config.ts`'s `extra.eas.projectId` is a real EAS
project (`94588650-4ee1-4798-a023-8aab2bf1d7f5`), no longer the
`REPLACE_WITH_REAL_EAS_PROJECT_ID` placeholder.

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
