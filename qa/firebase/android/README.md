# Firebase App Testing Agent — Android

Firebase's App Testing agent accepts natural-language YAML tests and executes them
against an APK. The CLI recursively scans the test directory.

Official documentation:
https://firebase.google.com/docs/app-distribution/android/app-testing-agent

## Firebase registration

Rally26 Android package name from `mobile/app.config.ts`:

`com.rally26.mobile`

Use that exact case-sensitive package name when registering the Firebase Android app.

After registration, obtain the Firebase **App ID** from Project Settings and export it:

```bash
export FIREBASE_APP_ID='1:...:android:...'
```

No Firebase SDK is required solely to use App Distribution/App Testing agent.

## Install / authenticate Firebase CLI

```bash
firebase login
firebase --version
```

Use a current Firebase CLI version that supports `apptesting:execute`.

## Credentials

Never place passwords in YAML.

Accounts must be real, working logins first — see `../../../docs/qa/README.md` for
how to provision the actual QA organization/accounts (real signup, CSV import, real
invitations) against production before creating any of the files below.

Create:

```text
qa/firebase/android/.credentials/
  coach.username
  coach.password
  parent.username
  parent.password
  athlete.username
  athlete.password
  owner.username
  owner.password
  platform-admin.username
  platform-admin.password
  owner-onboarding.username
  owner-onboarding.password
```

Additional advanced suites have matching names under `tests/authority` and
`tests/subscription`; see `test-data/TEST-ACCOUNTS.md`.

## Device list

Copy:

```bash
cp qa/firebase/android/test-devices.txt.example qa/firebase/android/test-devices.txt
```

The example uses device specifications shown in Firebase's own documentation.
Verify currently available models with the Google Cloud CLI before relying on them.

For the first pass, use **one** portrait phone to conserve preview quota. Add a second
Android version after the smoke suite is stable.

## Run

```bash
chmod +x qa/firebase/android/run-suite.sh

export FIREBASE_APP_ID='YOUR_FIREBASE_APP_ID'
qa/firebase/android/run-suite.sh coach /absolute/path/to/rally26.apk --smoke
qa/firebase/android/run-suite.sh coach /absolute/path/to/rally26.apk
qa/firebase/android/run-suite.sh parent /absolute/path/to/rally26.apk
qa/firebase/android/run-suite.sh athlete /absolute/path/to/rally26.apk
qa/firebase/android/run-suite.sh owner /absolute/path/to/rally26.apk
qa/firebase/android/run-suite.sh platform-admin /absolute/path/to/rally26.apk
```

The runner supplies the role's username/password to Firebase's automatic-login
facility and points `--test-dir` at only that suite.

## Owner onboarding

Run separately because it changes persistent server-side onboarding state:

```bash
qa/firebase/android/run-suite.sh owner-onboarding /path/to/rally26.apk
```

Use a disposable/resettable account. Do not reuse your normal owner account.

## Advanced authority suites

```bash
qa/firebase/android/run-suite.sh authority/owner-viewer /path/to/rally26.apk
qa/firebase/android/run-suite.sh authority/owner-admin /path/to/rally26.apk
qa/firebase/android/run-suite.sh authority/coach-read /path/to/rally26.apk
qa/firebase/android/run-suite.sh authority/coach-editor /path/to/rally26.apk
qa/firebase/android/run-suite.sh authority/coach-manager /path/to/rally26.apk
```

These are deliberately strict UX/security tests. Some are expected to reveal current
UI leakage even where the backend ultimately returns 403.

## Subscription-state suites

Each state requires a separate pre-provisioned account:

```bash
qa/firebase/android/run-suite.sh subscription/no-org /path/to/rally26.apk
qa/firebase/android/run-suite.sh subscription/plan /path/to/rally26.apk
qa/firebase/android/run-suite.sh subscription/review /path/to/rally26.apk
qa/firebase/android/run-suite.sh subscription/checkout-pending /path/to/rally26.apk
qa/firebase/android/run-suite.sh subscription/active /path/to/rally26.apk
qa/firebase/android/run-suite.sh subscription/past-due /path/to/rally26.apk
qa/firebase/android/run-suite.sh subscription/canceled /path/to/rally26.apk
```

## Results to inspect

For every failed test, review:

- AI-detected issues
- screenshots
- action trace
- agent accessibility view
- video
- logs

A visually correct pass is not enough when the test is about authority. Confirm the
agent never reached an unauthorized management screen before a backend rejection.
