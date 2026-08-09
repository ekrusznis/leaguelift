# Phase 28 Settings Help and Persona Coverage

**Date:** 2026-08-09
**Status:** Implementation complete through Slice 28.5; final operational closeout remains gated by Phase 28.0 CI/CD checks.

## Help Center coverage

V68 publishes:

1. `personal-settings-and-notifications` — PUBLIC, safe for every authenticated persona and useful before sign-in for explaining what Settings controls.
2. `organization-settings-directory` — OWNER_ADMIN, explaining the capability-filtered organization directory and why Settings does not bypass existing domain authorization.

Phase 28.4 payment-choice/financing design is intentionally **not** published as user-facing Help Center functionality because Phase 31 has not implemented or activated those methods.

## Persona matrix

| Persona/context | `/app/settings` | Personal appearance | Optional notifications | Organization directory | Important boundary |
|---|---|---|---|---|---|
| Owner / Administrator | Yes | Own account only | Own account only | Organizations with real management capability | Destination domain still authorizes every write |
| Payout-only organization role | Yes | Own account only | Own account only | Payout/financial entry only | No profile, member, billing, integration, or other org-management expansion |
| Coach | Yes | Own account only | Own account only | None unless separately granted organization-management capability | Team scope does not become organization scope |
| Guardian | Yes | Own account only | Own account only | None | Household visibility remains household-authorized |
| Controlled athlete | Yes | Own account only | Own account only | None | Athlete scope remains self/team/safety bounded |
| Platform Admin without active support session | Yes | Own account only | Own account only | No customer organization directory | Platform role alone does not create customer access |
| Platform Admin with active reasoned support session | Yes | Own account only | Own account only | Supported organization only | Platform Admin remains the actor; support access is time/reason scoped |

## Required closeout behaviors

- System/Light/Dark persists per account and does not affect authorization.
- Optional in-app/email/SMS choices are self-owned.
- SMS requires individual consent plus explicit topic enablement.
- Required account/security/invitation/financial/legal communications cannot be disabled.
- Safety-required guardian observer visibility for athlete messaging cannot be disabled.
- Preference changes apply to future recipient snapshots only.
- Settings does not duplicate History, Integrations, Billing, organization profile, commerce, financial, event, communications, or support data.
- Organization links must never point at a different organization ID than the card they are rendered for.
- No Phase 30 eligibility/waiver or Phase 31 payment-provider toggle appears before its real workflow exists.

## Operational gate

Before calling Phase 28 fully production-ready, confirm the existing Phase 28.0 GitHub Actions checks are green, including the reported frontend, dependency-review, and OpenAPI/security jobs, then complete the full backend/frontend/deploy gate in ADR-091.
