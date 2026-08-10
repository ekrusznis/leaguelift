# Rally26 Phase 33 — Native Mobile Application Planning & Product Specification

**Status:** Projected / planning-only
**Target platforms:** iOS, iPadOS, Android phones, Android tablets, foldables/windowed Android
**Client:** React Native + Expo + TypeScript
**Backend:** existing Rally26 Spring Boot API
**Repository shape:** same Rally26 monorepo; native client is a sibling application (recommended `mobile/`), not code nested inside the Spring Boot backend module.

## Purpose

Phase 33 does not ship the production mobile application. It produces the complete, implementation-ready mobile product and architecture specification so the subsequent implementation phase can build Android and iOS clients without inventing UX, API semantics, authorization rules, offline behavior, or native capability boundaries as it goes.

The mobile client must reuse Rally26's existing backend business rules. Spring Boot remains the authorization, privacy, money, audit, eligibility, messaging-safety, and organization-isolation boundary. Native code may improve presentation and device capabilities; it must not recreate server authorization in a second source of truth.

## Core architecture expectations

Recommended monorepo shape after implementation planning is approved:

```text
backend/        Spring Boot application and APIs
frontend/       React/Vite web client
mobile/         React Native/Expo iOS + Android client
infra/          deployment/runtime infrastructure
docs/           shared architecture, ADRs, product contracts
```

Phase 33 must decide and document:

- Expo/React Native project bootstrap and supported runtime/version policy at implementation time;
- Expo Router/native navigation structure and route/deep-link mapping;
- one API base contract shared with the web application through the existing Spring Boot/OpenAPI boundary;
- whether generated TypeScript API types/client code should be shared between `frontend/` and `mobile/` or independently generated from the same OpenAPI source;
- secure native authentication/session storage and refresh/revocation behavior;
- environment separation for local/development/preview/production mobile builds;
- native build/signing/submission workflow for Android and iOS;
- app-version compatibility/minimum-supported-version strategy;
- analytics/crash/error-reporting boundary consistent with Rally26 privacy rules.

Do not implement the mobile app as a WebView wrapper around the existing Vite application. Individual external/provider-hosted flows may legitimately open secure browser/provider surfaces, but ordinary Rally26 screens are native React Native screens.

## Responsive / screen-size-agnostic requirement

Every production screen must adapt to the **available application window**, not to a hard-coded phone model.

Phase 33 must specify every screen in at least these presentation states:

1. compact phone portrait;
2. large phone / compact landscape;
3. tablet portrait;
4. tablet landscape or wide multi-window;
5. foldable/resizable-window behavior where the platform can change available width at runtime.

The specification must define:

- content-width/size-class rules rather than device-name checks;
- single-column vs. multi-column behavior;
- bottom-tab/stack navigation for compact widths;
- navigation rail/sidebar and list-detail/split layouts where wide screens benefit from them;
- card/grid reflow and maximum readable content widths;
- safe-area, keyboard, modal/bottom-sheet, rotation, and system-bar behavior;
- large text/font scaling without clipped controls;
- accessibility focus/read order and screen-reader labels;
- touch target, contrast, reduced-motion, and error-state expectations;
- no critical action that exists only in a hover interaction.

A tablet is not a stretched phone. Wide layouts should use the additional space for useful context, side-by-side information, persistent navigation, or master/detail views where that improves the workflow.

## Phase 33 planning slices

### 32.0 — Mobile architecture and web-route parity inventory

Create an authoritative mobile parity matrix from the current web route registry and backend API surface.

Every reachable Rally26 route/workflow must be classified as one of:

- `NATIVE_FULL` — full native mobile capability expected;
- `NATIVE_ADAPTED` — same workflow with mobile/tablet-specific information architecture;
- `PROVIDER_BROWSER` — deliberately leaves the app for a provider-hosted secure flow;
- `WEB_ONLY_JUSTIFIED` — permitted only with a written product/security reason and an in-app explanation/deep link.

No current feature may simply disappear from the mobile plan because its desktop page is complex.

Deliverables:

- `MOBILE-ROUTE-PARITY-MATRIX.md`;
- mobile monorepo/module design;
- API reuse/gap inventory;
- initial navigation tree by persona;
- architecture ADR for React Native/Expo and same-backend reuse.

### 32.1 — Native design system and responsive layout specification

Translate the Rally26 visual language into native design tokens and components without trying to reuse React DOM components directly.

Specify:

- colors, typography, spacing, radii, elevation/shadow, icons, status styles;
- light/dark/System behavior aligned with Phase 28 account appearance preference;
- buttons, inputs, selectors, date/time controls, chips, cards, lists, tables/data grids, dialogs, sheets, toasts, banners, avatars, image uploaders, empty states, skeletons, and error states;
- compact/medium/expanded layout rules;
- phone vs. tablet navigation components;
- dense operational-table alternatives for small screens;
- reusable money, status, date/time, participant, team, organization, order, fee, message, and audit components;
- accessibility and dynamic-text rules.

Deliverables include annotated phone/tablet wireframes for all shared components and a screen-layout template every later page spec must use.

### 32.2 — Authentication, onboarding, shell, deep links, and account switching

Design native expectations for:

- app bootstrap/splash/session restore;
- sign in, sign out, password recovery/reset, invitation acceptance, email verification;
- owner registration/onboarding where mobile support is appropriate;
- organization/context switching;
- authenticated app shell/navigation;
- profile/settings entry;
- active Platform Support-session indication;
- universal/app/deep links for invitations, support, public pages, campaigns, athlete storefronts, orders, messages, events, and other shareable destinations;
- invalid/expired link handling;
- optional biometric unlock only as a local convenience over a valid Rally26 session, never as a replacement for backend authentication.

The planning output must identify any browser-only authentication assumption that cannot safely be reused by a native app.

### 32.3 — Page-by-page UI/UX specification for every persona

Create one native screen specification for every route/workflow in the parity matrix. Each specification must contain:

- purpose and authorized personas;
- backend endpoint/data dependencies;
- compact-phone layout;
- tablet/wide layout;
- primary/secondary actions;
- navigation entry and deep-link behavior;
- loading/skeleton state;
- empty state;
- validation/error/retry state;
- permission-denied/revoked-access state;
- offline/stale-data behavior;
- destructive-action confirmation;
- sensitive-data/privacy notes;
- accessibility requirements;
- analytics/audit expectations where applicable.

At minimum the inventory must cover these Rally26 product families:

**Shared/authenticated**
- Dashboard and persona landing experience
- Action Center
- global/context search where retained
- Events / calendar / RSVP
- Announcements
- Messages and safety/reporting flows
- Help Center / support cases
- History
- Settings: appearance, notification preferences, account links, organization directory
- notification center / push-entry destinations

**Owner / Administrator**
- organization switcher and organization workspace
- organization profile, address/timezone, branding and public-page management
- teams, tournaments, rosters, households, adults and participants
- invitations/onboarding/import workflows
- fees, household balances, payment plans, manual payments and financial operations
- family-credit and markup policy surfaces
- fundraising/campaigns and contribution operations
- sponsorship management
- Swag Shop catalog, products, orders, fulfillment/reprints
- documents and, once Phase 31 exists, eligibility/waiver administration
- integrations
- billing/subscription
- reconciliation, History and administration/support-aware screens

**Coach / scoped staff**
- team workspace
- roster/participant views permitted by scope
- schedule/events/RSVP operations
- team communications/messages
- correction/onboarding actions actually permitted to the coach
- relevant Action Center items

**Guardian**
- household home
- linked athletes/participants
- fees, payment plans, credits and payment history
- schedule/RSVP
- documents and Phase 31 waiver/eligibility completion
- announcements/messages
- fundraising links
- Swag Shop/orders
- profile-correction flow

**Athlete**
- athlete dashboard
- own teams/roster context
- schedule/RSVP where allowed
- own profile/correction flow
- messages under the Phase 25 safety policy
- storefront/fundraising destinations
- Phase 31 eligibility status without exposing restricted document contents

**Platform Administrator**
- platform dashboard
- organization lookup/detail
- support sessions and active-support context
- audit/history
- data-integrity/duplicate-resolution workflows
- integration/provider health and sanitized operational views

Platform Admin operational screens may be tablet-first, but they may not gain authorization simply because they are rendered in mobile.

### 32.4 — Mobile-native capabilities, push notifications, and offline policy

Define the device features Rally26 will use and the backend seams they require.

**Push notifications**
- push is a separate delivery channel from Phase 28's in-app/email/SMS choices;
- account preference and operating-system permission are separate prerequisites;
- design device-token registration, rotation, invalidation, logout cleanup, multi-device behavior, and delivery observability;
- map each optional notification topic to push eligibility;
- required/safety communications keep the same non-disableable policy boundaries, while OS notification permission remains under the user's device control;
- notification taps deep-link to the authorized destination and must recheck access after opening.

**Native device capabilities to evaluate/specify**
- camera/photo upload for profile/brand/document evidence;
- QR scanning for Rally26 links/check-in workflows only where a real domain use exists;
- share sheet;
- file/document picker;
- calendar handoff/add-to-calendar;
- maps/directions handoff;
- biometric local unlock;
- badges where platform behavior is reliable;
- haptics only as progressive enhancement.

**Offline/stale-data classification**

Every screen/data type must be classified, not globally cached by default:

- safe for short-lived local cache;
- memory-only;
- encrypted persistent cache if justified;
- online-only / never persisted.

Financial mutation, payment-provider confirmation, credential/security changes, support-access grants, merge/data-integrity mutations, and other high-risk operations are online-only. Any future queued write must be explicitly idempotent and conflict-safe before it is allowed offline.

### 32.5 — Spring Boot API/mobile contract gap analysis

The mobile application uses the same business APIs and authorization service as web. Phase 33 must list every required API change rather than creating an untracked mobile backend.

Expected mobile-specific seams to evaluate include:

- device/push-token registration;
- device/app-installation identity separate from user identity;
- app-version/minimum-version/readiness response;
- native deep-link/invitation callback requirements;
- upload constraints/resumability for mobile networks;
- pagination and payload-size suitability;
- reconnect/retry/idempotency behavior;
- account/context changes and cache invalidation;
- notification receipt/open telemetry only if privacy-approved.

OpenAPI remains the authoritative HTTP contract. Backend authorization is never replaced by hiding a button in the native client.

### 32.6 — Mobile security, privacy, youth-safety, and accessibility review

Produce a mobile-specific threat/privacy review for:

- credential/session storage;
- screenshots/app switcher previews for sensitive screens;
- clipboard handling;
- logs/crash reports;
- local cache purge on logout/account switch;
- rooted/jailbroken-device policy if needed;
- file/photo permissions;
- notification content shown on lock screens;
- guardian/athlete household isolation;
- Phase 25 SafeSport messaging behavior;
- Phase 31 waiver/document privacy;
- payment/provider browser returns;
- Platform Support session boundaries;
- accessibility across VoiceOver/TalkBack, dynamic type/font scaling, contrast, focus and switch/keyboard navigation where applicable.

### 32.7 — Build, testing, store-release, and implementation roadmap

Specify the complete delivery model before production code begins:

- local development and development-build workflow;
- preview/internal-distribution builds;
- Android and iOS signing ownership;
- CI gates for TypeScript, unit/component tests, lint, native config validation, API-contract drift, and buildability;
- device test matrix covering representative compact/large phones, tablets, orientation changes, and at least one resizable/foldable Android scenario;
- end-to-end testing strategy and selected framework;
- TestFlight and Google Play internal/closed testing;
- App Store / Play Store privacy, permission, age-rating, account-deletion and support expectations;
- release/versioning/channel strategy;
- crash/error and performance monitoring;
- rollback/update policy, including which changes require a new native binary;
- implementation sequencing and estimates/slices for the subsequent mobile build phase.

Phase 33 ends with a founder-approved implementation backlog. It does not count as a shipped mobile application.

## UI specification standard for every screen

Every page spec produced in Phase 33 must use the same template:

```text
Screen:
Personas:
Source web route/workflow:
API dependencies:

Phone / compact:
Tablet / wide:

Primary action:
Secondary actions:
Navigation/deep links:

Loading:
Empty:
Error/retry:
Offline/stale:
Permission denied/revoked:
Destructive confirmation:

Sensitive data:
Accessibility:
Push/deep-link entry behavior:
Audit/analytics behavior:
Acceptance tests:
```

This makes the planning artifact directly usable as the implementation contract instead of a collection of mockups without behavior definitions.

## Phase 33 acceptance criteria

1. Every current Rally26 web route/workflow appears in the parity matrix with an explicit mobile disposition.
2. Every `NATIVE_FULL`/`NATIVE_ADAPTED` screen has phone and tablet/wide UI specifications plus loading/empty/error/offline/authorization states.
3. No native screen creates a second authorization model; Spring Boot remains authoritative.
4. The plan works across changing window sizes and orientation without device-specific hard-coded layouts.
5. Deep links and notification destinations cannot bypass current-user authorization checks.
6. Push is modeled independently from in-app/email/SMS and requires both account policy and device permission where optional.
7. Mobile local persistence is data-classified; sensitive/high-risk mutations are not silently queued offline.
8. Existing Phase 25 youth messaging safety, Phase 27 audit/history scoping, Phase 28 preferences, Phase 31 eligibility/privacy, and Phase 32 payment/provider boundaries are represented in the native design.
9. Android and iOS build/signing/testing/store-release ownership is documented.
10. The phase produces an approved, slice-by-slice implementation backlog before production mobile development starts.

## Current technology assumptions to re-verify when Phase 33 begins

At the time this projection was recorded (2026-08-09), current official React Native guidance recommends responsive layouts based on the changing application window, and Expo provides native routing/deep-link, push-notification, Android/iOS build, update, and store-submission tooling. Exact Expo SDK/React Native versions and store requirements are intentionally not pinned in this projection; Phase 33 must re-verify them when implementation planning starts.
