# Rally26 Android AI QA Pack

This folder is designed for Firebase App Distribution's **App Testing agent (Android)**.
It is intended to be committed at the repository root as `qa/`.

Reviewed against `ekrusznis/leaguelift` `main` on 2026-08-15. The repository head observed
during preparation was `c3e2caaa41061a0bf885ae4a92b3a9f0c971bae1`.

## What this pack covers

- first-launch and authentication UX
- Coach
- Parent / Guardian
- Athlete
- Owner
- Platform Administrator current mobile boundary
- owner onboarding and subscription-access gating
- authority-tier checks:
  - OWNER / ADMINISTRATOR / VIEWER
  - TEAM_MANAGER / TEAM_EDITOR / COACH_READ
- plan-entitlement expectations for Starter / Club / League
- cross-role leakage checks
- visual / interaction assertions on every major path
- known gaps found directly in the current source tree

The Firebase YAML schema intentionally uses only the documented keys:

`tests`, `displayName`, `id`, `prerequisiteTestCaseId`, `steps`, `goal`, `hint`,
and `finalScreenAssertion`.

## Start here

1. Read `firebase/android/README.md`.
2. Create/register the Android app in Firebase with package:
   `com.rally26.mobile`.
3. Opt in to the App Testing agent preview.
4. Prepare QA accounts using `test-data/TEST-ACCOUNTS.md`.
5. Put role credentials in `firebase/android/.credentials/` (never commit them).
6. Run a single-device smoke pass first:
   `firebase/android/run-suite.sh coach /path/to/rally26.apk --smoke`
7. Run the other primary personas:
   `parent`, `athlete`, `owner`, `platform-admin`.
8. Run `owner-onboarding` separately against a resettable incomplete owner account.
9. After baseline UI passes, run `authority/*` and `subscription/*` suites.

## Important scope note

Firebase's AI-guided Android agent is screen/action based. It is excellent at detecting
broken navigation, missing fields, bad forms, visible errors, inaccessible controls,
authorization leakage in the UI, and broken user flows. It does **not** replace backend
unit/integration tests for invisible database state, webhook correctness, audit records,
or authorization checks that cannot be observed through the UI.

Keep this QA pack alongside backend tests; do not replace them with it.
