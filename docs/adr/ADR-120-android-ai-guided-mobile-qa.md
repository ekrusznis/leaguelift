# ADR-120 — Android AI-guided mobile QA/UX/UI testing with Firebase App Testing agent

**Status:** Accepted  
**Date:** 2026-08-15

## Context

Rally26's native mobile application is now large enough that founder-only manual click-through is no longer a sufficient first-line regression strategy. Coach, Parent/Guardian, Athlete, and Owner are backend-wired; Owner additionally has subscription/onboarding gates and authenticated WebView seams; the backend distinguishes materially different authority levels such as `OWNER`/`ADMINISTRATOR`/`VIEWER` and `TEAM_MANAGER`/`TEAM_EDITOR`/`COACH_READ`. Recent device testing already surfaced exactly the class of defects a systematic user-level pass must catch: an erroneous onboarding error, missing pricing/checkout presentation, and a draft Owner organization being able to reach a dashboard before subscription completion.

The founder selected Android as the first platform to finish and harden before deciding the equivalent iOS automation/device-testing approach. Firebase App Distribution's App Testing agent is an Android-only preview feature powered by Gemini. It accepts natural-language test cases, supports source-controlled YAML with multi-step goals/hints/final-screen assertions and prerequisite test cases, can receive login credentials at execution time, can execute against an APK from the Firebase CLI, and returns user-interaction evidence suitable for QA triage. Firebase's own documentation also makes two boundaries important to this decision: final assertions are about what is visible on screen, and registering/distributing/testing an app through this feature does not by itself require adding Firebase runtime SDKs to the application.

Manual exploratory testing remains valuable, but Rally26 needs a repeatable baseline that can be rerun against the same release-candidate APK after fixes. The test model must also avoid a common security mistake: a successful UI test is not proof that a direct API call is authorized. Spring Boot remains authoritative for organization isolation, role/capability enforcement, money, webhook state, audit, youth-data rules, and plan entitlements.

## Decision

Adopt a repository-root **`qa/` Android testing unit** using Firebase App Testing agent as Rally26's AI-guided user/UX/UI regression layer.

### Source-controlled structure

The unit is organized as:

```text
qa/
  firebase/android/
    tests/
      shared/
      coach/
      parent/
      athlete/
      owner/
      platform-admin/
      owner-onboarding/
      authority/
      subscription/
    run-suite.sh
    test-devices.txt.example
    .credentials/README.md
  matrices/
    ROLE-FEATURE-MATRIX.md
    SUBSCRIPTION-GATING-MATRIX.md
    COVERAGE-MATRIX.md
  test-data/
    TEST-ACCOUNTS.md
  triage/
    BUG-REPORT-TEMPLATE.md
    SEVERITY.md
```

The initial package seeds 21 YAML files / 72 test cases. `[SMOKE]` cases are the fast baseline; fuller cases provide deeper regression coverage after blockers are fixed.

### Firebase execution contract

- Register the Android application in Firebase using the exact Rally26 package name **`com.rally26.mobile`**.
- Keep the Firebase App ID outside the test YAML and supply it at runtime.
- Execute version-controlled YAML with `firebase apptesting:execute --app=... --test-dir=... <apk>`.
- Supply usernames/passwords using Firebase's runtime automatic-login options and ignored local/CI secret files; credentials never belong in committed YAML.
- Start with one representative portrait Android phone for the smoke baseline. Broaden Android OS/device coverage only after tests are stable enough that additional runs are useful rather than noisy.
- Re-verify Firebase preview status, CLI options, available devices, quota, pricing, and execution limits before encoding provider-specific assumptions into CI.

### Persona / authority coverage

Do not use one over-permissioned account as a substitute for role testing. Maintain distinct QA identities for:

- Coach `TEAM_MANAGER`;
- Coach `TEAM_EDITOR`;
- Coach `COACH_READ`;
- Parent/Guardian with representative household/team data;
- Athlete with a real `ATHLETE_SELF` link;
- Organization `OWNER`;
- Organization `ADMINISTRATOR`;
- Organization `VIEWER`;
- Platform Administrator;
- incomplete/disposable Owner onboarding;
- independent Owner organizations in key subscription lifecycle and plan states.

The role-feature matrix documents both positive capabilities and negative expectations. The AI layer must catch misleading UI leakage even when the backend would eventually reject a mutation. Conversely, hiding a button is never treated as authorization; backend tests must continue proving direct calls are denied.

### Subscription/onboarding coverage

The Android suite explicitly verifies the Owner access contract now implemented in mobile:

- no organization → setup locked;
- `DRAFT`/PLAN/REVIEW/CHECKOUT_PENDING → setup locked;
- Checkout browser/WebView return alone → still locked while webhook-backed state is pending;
- `ACTIVE` organization + COMPLETE onboarding + accessible subscription state (`ACTIVE`, `TRIALING`, `PAST_DUE`) → native Owner workspace unlocked;
- canceled/suspended/incomplete states → fail closed/recovery path.

Separate Starter/Club test organizations verify user-visible plan behavior. Starter's current backend entitlements (three-team cap; gated SMS, SportsEngine, TeamSnap, and QuickBooks Online) remain backend-enforced; Firebase tests validate the explanatory/upgrade UX and detect misleading availability.

### UX/UI assertions

Each major flow checks more than destination existence. Tests should look for:

- clipped/overlapping controls or text;
- unsafe-area/keyboard/scroll failures;
- dead taps;
- missing fields/buttons/options;
- misleading permission controls;
- empty loading areas without intentional empty/error state;
- raw API/stack/internal error leakage;
- wrong-persona destinations;
- authenticated WebViews opening the wrong management/buyer surface;
- cross-organization, cross-household, or restricted youth-data exposure.

Final-screen assertions must be phrased only in terms of what the agent can actually see. Database, webhook, audit, ledger, and isolation invariants that are not visible remain ordinary backend/integration-test responsibilities.

### Mutation safety

The normal AI suite is non-destructive by default. It may create clearly disposable QA messages, open/cancel safe forms, or change a reversible QA RSVP where the fixture is intentionally designed for it. It must not initiate live transfers, refunds, irreversible member changes, real purchases, real sponsorship charges, destructive production actions, or other financial/security mutations unless a dedicated sandboxed workflow and cleanup contract is approved separately.

### Triage

Firebase results feed a common P0-P3 triage contract. P0 includes cross-organization/household data exposure, unauthorized financial/admin behavior, onboarding/subscription bypass, crash/data loss, or inability of a core role to sign in/use the app. Failed or suspicious tests should be reviewed with screenshots, action trace, accessibility view, video, and logs before assigning ownership.

### Maintenance rule

A mobile change that alters visible fields, routes, role/capability behavior, onboarding/subscription gates, plan entitlement UX, or WebView destinations must update the relevant `qa/firebase/android` YAML and matrix in the same change. The QA unit is part of the product contract, not a one-time test script dump.

### iOS boundary

This ADR decides **Android only**. Firebase App Testing agent's Android result is not evidence of iOS correctness. After Android reaches a stable founder-approved baseline, select the iOS device/UAT/automation approach appropriate to the then-current Expo/TestFlight/tooling environment and record it in a separate ADR. Do not force an Android-specific provider/tool onto iOS merely for symmetry.

## Consequences

- Rally26 gains a repeatable AI-driven first-round QA/UX/UI baseline instead of relying only on ad hoc founder click-through.
- Role leakage and subscription-gating behavior become explicit test contracts, making the UI easier to keep aligned with backend authority.
- The test-account fixture model becomes a real dependency: stable QA identities/data states must exist and must not accidentally overlap capabilities.
- Firebase App Testing agent is preview software, so provider availability/CLI/device/quota changes can temporarily block execution. The repository YAML and matrices remain valuable even if the provider changes later.
- The agent's visible-screen assertions cannot prove hidden backend state. Backend authorization, subscription, webhook, ledger, audit, idempotency, and organization-isolation tests remain mandatory.
- Android release readiness becomes separate from iOS readiness. Completing the Android AI QA gate does not clear iOS.
- The first full run may intentionally expose known current issues, including role/menu leakage or stale unsupported-role copy; such failures are QA findings to fix, not reasons to weaken the assertions.

## References

- `DESIGN-DOC.md` §14.1N, §14.1W, §14.1X, §14.4, §20
- `qa/`
- `mobile/app.config.ts` (`android.package = com.rally26.mobile`)
- `mobile/src/app/_layout.tsx`
- `mobile/src/app/owner/_layout.tsx`
- `mobile/src/features/ownerOnboarding/routing.ts`
- `backend/src/main/kotlin/com/rally26/authorization/domain/Capabilities.kt`
- `backend/src/main/kotlin/com/rally26/subscription/application/PlanEntitlementService.kt`
- Firebase App Testing agent (Android): `https://firebase.google.com/docs/app-distribution/android/app-testing-agent`
