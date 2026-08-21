# QA Test Accounts / Data Contract

The AI tests are only as reliable as the data behind the accounts. Build a dedicated
QA organization rather than using live customer data.

The repository already contains `docs/qa/demo-data-import.csv`, designed to create
multi-team/multi-household demo data. Use it as the baseline.

## Primary accounts

Create one credential pair for each:

| Suite | Required backend identity/state |
|---|---|
| `coach` | Coach routed user with `TEAM_MANAGER` on a populated QA team |
| `parent` | Guardian linked to a household with at least 2 athletes across 2 teams |
| `athlete` | User with a real `ATHLETE_SELF` role assignment linked to one QA participant |
| `owner` | Membership `OWNER`, active organization, completed onboarding, active Club subscription |
| `platform-admin` | Real Platform Administrator role |
| `owner-onboarding` | Disposable owner registration with onboarding incomplete |

## Authority accounts

| Suite | Requirement |
|---|---|
| `authority/owner-viewer` | Active `VIEWER` membership in the QA organization |
| `authority/owner-admin` | Active `ADMINISTRATOR` membership |
| `authority/coach-read` | `COACH_READ` assignment on one team only |
| `authority/coach-editor` | `TEAM_EDITOR` assignment on one team only |
| `authority/coach-manager` | `TEAM_MANAGER` assignment on one team only |

Use different users, not one user with multiple overlapping memberships/role assignments,
otherwise dashboard routing and inherited capabilities can hide authority bugs.

## Subscription-state accounts

Provision distinct owner accounts/organizations for:

- no organization yet
- saved Plan step
- saved Review step
- checkout pending
- ACTIVE
- PAST_DUE
- CANCELED/SUSPENDED
- active Starter plan
- active Club plan

Do not reuse one account across these state tests unless you deliberately reset its
database/onboarding/subscription state between runs.

## Required demo content

For stable tests, the QA organization should contain at least:

- 2 teams
- 1 TEAM_MANAGER coach
- 1 TEAM_EDITOR coach
- 1 COACH_READ coach
- 2 households
- 1 household with 2 guardians
- 1 household with 2 athletes on different teams
- 3+ upcoming events
- 1 published announcement
- 1 message thread per primary persona where permitted
- 2 fees, one with payment history and one outstanding
- 1 family credit balance
- 1 document
- 1 eligibility requirement / clearance record
- 1 fundraiser in a non-destructive testable state
- 1 Swag Shop product/order if available
- 1 sponsorship if available
- 1 disposable member safe for role-change/revoke tests
- a Stripe Connect state that is safe to view

## Athlete account caveat

Real athlete self-service login now has a real invite path (household detail page →
participant card → "Invite athlete", `POST /participants/{id}/athlete-invitations`).
The athlete must be 13+ (`MINIMUM_ATHLETE_SELF_LOGIN_AGE`) — the demo CSV's athletes
are all under that age, so set a participant's date of birth to 13+ before inviting.
See `../../docs/qa/README.md` step 5.

## Secrets

Never commit QA passwords. The `.credentials` directory is ignored by this pack.
