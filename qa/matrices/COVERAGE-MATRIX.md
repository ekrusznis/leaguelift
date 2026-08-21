# Android AI QA Coverage Matrix

| Area | Primary YAML suite | Mutation level | Notes |
|---|---|---|---|
| First launch/login | `shared` | low | blank/invalid validation |
| Owner registration (sign-up) | `shared` | stateful | creates a real unverified account; use a disposable/timestamped email, never a reusable QA credential |
| Guardian/athlete household invitations | n/a | n/a | **Mobile-unavailable this session, web-only** — see `qa/KNOWN-REPO-FINDINGS.md` #7; no native screen or WebView embed exists yet |
| Coach Home/Calendar/Roster | `coach` | low | includes visual sweep |
| Coach messages | `coach` | low write | QA reply only |
| Coach More authority | `coach` | read | designed to catch owner-menu leakage |
| Coach authority tiers | `authority/coach-*` | read | separate credentials |
| Parent dashboard/calendar/RSVP | `parent` | low write | Maybe RSVP |
| Parent payments/docs | `parent` | read | no real payment |
| Parent commerce | `parent` | read | no checkout |
| Athlete self/schedule | `athlete` | low | own data only |
| Athlete SafeSport messaging | `athlete` | low write | QA message only |
| Athlete eligibility/privacy | `athlete` | read | restricted evidence must stay hidden |
| Owner dashboard/teams/members | `owner` | read | member modal opened then canceled |
| Owner reports/fees/financial ops | `owner` | read | no money mutation |
| Owner payout | `owner` | read | no transfer |
| Owner announcements/fundraising | `owner` | read | compose mutation left for manual/advanced |
| Owner Messages tab + org-wide oversight | `owner` | low write | QA reply only; oversight of others' conversations is read-only by design |
| Owner commerce/web embeds | `owner` | read | no checkout |
| Owner onboarding | `owner-onboarding` | stateful | disposable account |
| Owner authority tiers | `authority/owner-*` | read | catches UI leakage |
| Platform Admin current boundary | `platform-admin` | low | current mobile role unavailable |
| Subscription lifecycle gate | `subscription/*` | read | one account per state |
| Starter/Club visible entitlements | `subscription/*-entitlements` | read | backend checks still required |

## Recommended first round

Run only `[SMOKE]` tests on one Android device for:

1. `shared`
2. `coach`
3. `parent`
4. `athlete`
5. `owner`
6. `platform-admin`
7. `owner-onboarding`

Then fix P0/P1 findings before running the full suites.
