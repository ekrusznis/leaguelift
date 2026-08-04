# ADR-060: Merge the Finance Manager Role into Administrator

## Status
Accepted

## Context

A code-level audit of the four organization-facing dashboard roles (Owner,
Administrator, Finance Manager, Family/household) — done in place of a live
click-through, following the same static-review methodology ADR-030 (mobile) and
ADR-038 (accessibility) used — found that `MembershipRole.FINANCE_MANAGER` had drifted
into a real bug rather than a working role.

`Capabilities.kt`'s `organizationCapabilities()` granted Finance Manager the exact same
organization-level capability as `VIEWER` — `ORG_REPORT_VIEW` only. Every actual
financial-operations action (refunds and reversals in `FinancialCorrectionService`,
offline payment/reconciliation records in `OfflineFinancialRecordService`/
`ReconciliationService`) is gated behind `MembershipService.requireManagerRole`, which
only ever allowed `OWNER` and `ADMINISTRATOR`. Finance Manager was never added to that
check. The "Financial Operations" tab in `OrganizationDetailPage.tsx` is itself hidden
from anyone without `ORG_MANAGE`, so a Finance Manager couldn't even reach the pages
that would call those blocked endpoints. Net effect: despite DESIGN-DOC §4.4 describing
Finance Manager as a role with "real financial operations authority" (view fees/
payments/credits/revenue/ledger/payouts, issue permitted refunds/adjustments, export
reports), the role as shipped could do nothing a Viewer couldn't.

## Decision

Rather than build out a new, narrower capability set to make Finance Manager live up to
its documented description, the founder's call was simpler: stop treating it as a
distinct role. `MembershipRole.FINANCE_MANAGER` is removed entirely. Anyone who needs
finance-operations authority is assigned `ADMINISTRATOR`, which already has real
`requireManagerRole` access to refunds, offline records, and reconciliation — the exact
authority Finance Manager was supposed to have but didn't. This is a genuine
simplification, not a rename: Administrator remains excluded from `ORG_BILLING_MANAGE`
and `ORG_PAYOUT_MANAGE` (owner-only, per §4.4's "Danger Zone" boundary), so the merge
does not hand billing/payout/ownership control to anyone who didn't already have it —
it only closes the gap where Finance Manager had less access than its own job
description required.

**Scope of the change:**
- `MembershipRole` enum: `FINANCE_MANAGER` removed (`OrganizationMembership.kt`).
- `Capabilities.kt`: `organizationCapabilities()` and `householdCapabilitiesForOrgRole()`
  drop their `FINANCE_MANAGER` branches (the latter's now-unused `financialView` local
  removed with it).
- `DashboardContextService`, `WelcomeEmailFeatures`, `Invitation.INVITABLE_ROLES`,
  `MembershipService.REPORTING_ROLES`, `ReportingService`'s doc comment: `FINANCE_MANAGER`
  references removed; nothing needed adding in its place since `ADMINISTRATOR` was
  already present in every one of these.
- Frontend: `types.ts`'s `INVITABLE_ROLES`, `InvitationsPanel.tsx`'s `ROLE_LABELS`,
  `OnboardingPanel.tsx`'s `STAFF_ROLES`, `OwnerDashboard.tsx`'s role-label map all drop
  the `FINANCE_MANAGER` entry — Administrator was already present in each.
- `docs/openapi.yaml`: `MembershipRole`/`InvitableMembershipRole` enums and reporting
  endpoint summaries updated to match.
- New migration `V43__merge_finance_manager_into_administrator.sql`: converts any
  existing `organization_membership`/`invitation` rows with `role = 'FINANCE_MANAGER'`
  to `'ADMINISTRATOR'` **before** narrowing both tables' role check constraints — order
  matters, since narrowing first would make the conversion UPDATE itself fail the
  constraint on any row still holding the old value mid-statement on some engines, and
  more importantly would just be wrong sequencing regardless. `invitation` rows are
  converted regardless of status (not just `PENDING`), since the check constraint
  applies to historical `ACCEPTED`/`REVOKED`/`EXPIRED` rows too.
- Tests: the two `AuthorizationServiceTest` cases written specifically against
  `FINANCE_MANAGER` were either converted to test the equivalent `VIEWER` case (team
  capability non-inheritance) or deleted as now-redundant with an adjacent `VIEWER` test
  that already covered the same household-capability shape. `DashboardContextServiceTest`
  and `MembershipServiceTest`'s dedicated Finance Manager cases were converted to
  `ADMINISTRATOR`/deleted the same way.

## Consequences

- Any org that already had a real Finance Manager member gets that member automatically
  promoted to Administrator on this migration — a real capability *increase* for that
  person (team/tournament/communication management they didn't have before, in addition
  to the financial-operations access they were supposed to have all along). This is the
  intended outcome of the merge, not a side effect to mitigate.
- The role picker in invitations and CSV-import staff roles now offers one fewer option.
  Nothing downstream depended on distinguishing "Finance Manager" from "Administrator"
  in reporting/analytics — `REPORTING_ROLES`/`organizationCapabilities()` already treated
  them almost identically before this change (the one gap was the bug this ADR fixes).
- This was found and fixed via a static code-level audit rather than a live
  production walkthrough — the audit's own findings for Owner, Administrator (minor nav
  clarity note), and Family (dead `HOUSEHOLD_CREDIT_VIEW`/`HOUSEHOLD_ORDER_VIEW`
  capabilities with no backing feature) were not acted on in this pass; only the Finance
  Manager capability bug was, since it was the one clear, scoped, low-risk fix versus
  the others being either working-as-documented or a larger feature gap needing its own
  decision.
- Not machine-verified: this sandbox still has no network access to run the Gradle
  backend test suite. The migration, capability-model, and test changes were reviewed by
  hand for consistency (every removed `FINANCE_MANAGER` branch was cross-checked against
  what `ADMINISTRATOR` already had in the same `when` block) but not compiled or run.

## Alternatives Considered

- **Give Finance Manager its own real, narrower capability set** (e.g. `ORG_REPORT_VIEW`
  plus `financial-correction`/reconciliation access but not team/tournament/member
  management) instead of merging it into Administrator. This is closer to the role's
  original DESIGN-DOC description and would preserve a real least-privilege distinction
  for orgs that want a bookkeeper-type user who can't also manage rosters. Rejected per
  the founder's explicit instruction to combine the two roles — revisit if real usage
  ever calls for that narrower split.
- **Keep `FINANCE_MANAGER` as a label, alias its capabilities to Administrator's.**
  Would avoid a schema migration but leaves a confusing distinction with no behavioral
  difference — two names for one role is worse than one name, and the DB enum wasn't a
  Postgres native type (just a `varchar` + `check` constraint), so removing it outright
  was a small, low-risk migration rather than a reason to keep the alias.
