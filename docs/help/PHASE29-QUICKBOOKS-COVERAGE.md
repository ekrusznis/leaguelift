# Phase 29 QuickBooks Help, Authorization, and Privacy Coverage

**Date:** 2026-08-09
**Status:** Phase 29 product implementation complete through Slice 29.5; operational completion still requires the repository/PR/deploy gates in `docs/PHASE29-CLOSEOUT-CHECKLIST.md`.

## Help Center coverage

V72 forward-updates the existing `quickbooks-online-readiness` OWNER_ADMIN article created by Phase 19. It now describes the Rally26/Phase 29 reality rather than the historical LeagueLift/Phase 19-20 wording:

- QuickBooks is scaffolded but inactive;
- no live Intuit credential is required by Phase 29;
- owner/accountant-customizable mappings use stable QuickBooks Account IDs;
- recommendations never silently configure accounting;
- inactive/incompatible accounts are blocked and warning-level choices require acknowledgement;
- export preview never sends accounting data;
- saved realm/company metadata is not live provider health;
- credential, sandbox, accounting-review, write-policy, and provider-write gates belong to a separately approved later activation phase.

No public or athlete/guardian/coach article is added because organization QuickBooks setup is not a workflow for those personas.

## Authorization matrix

| Persona/context | QuickBooks organization setup | Boundary |
|---|---|---|
| Organization Owner | Allowed | Organization-scoped manager authorization; connection must belong to the same organization |
| Organization Administrator | Allowed | Same organization/connection isolation as Owner |
| Coach | Denied unless separately an Owner/Administrator | Team access never grants accounting integration management |
| Guardian | Denied | Household access never grants organization accounting access |
| Athlete | Denied | Athlete scope never grants organization accounting access |
| Platform Admin without active support access | Denied by the organization-support request guard | Platform role alone is not customer accounting access |
| Platform Admin with active reasoned organization support access | Existing organization UI/services may be used | Employee remains actor; support access is organization/time/reason scoped; no provider secret is returned |

QuickBooks service methods continue to use `MembershipService` and the organization-owned `IntegrationOAuthService` connection lookup. The Phase 14 organization support-access interceptor remains the outer guard for Platform Admin entry into customer organization routes.

## Cross-organization fail-closed rules

- Organization overview requires manager authorization before loading QuickBooks organization data.
- Connection-scoped reads/mutations use `accessTokenForOrganizationConnection(organizationId, connectionId, currentUser)`; the connection lookup is organization-qualified.
- Slice 29.5 orders mapping revalidation so the organization/connection authorization seam runs before saved mapping rows are read.
- Export candidate counts remain explicitly filtered by `organization_id`.
- Export preview batch history is listed by `organization_id`.
- No QuickBooks repository method is exposed directly as a controller endpoint.

## Audit coverage

Meaningful QuickBooks setup actions preserve the authenticated user and organization scope:

- `integration.quickbooks_company_read`
- `integration.quickbooks_accounts_read`
- `integration.quickbooks_mapping_updated`
- `integration.quickbooks_mappings_revalidated` (added in 29.5)
- `integration.quickbooks_export_previewed`

The revalidation audit event intentionally stores no OAuth token, chart-of-accounts payload, account names, or other provider secret material in audit metadata.

## Privacy and secret handling

Phase 29 QuickBooks response DTOs expose setup/readiness/accounting-mapping information only. They do not expose:

- access tokens;
- refresh tokens;
- Intuit client secrets;
- encrypted credential ciphertext;
- OAuth PKCE/code verifiers.

Credentials remain owned by the generalized encrypted integration credential subsystem. The QuickBooks domain receives an access token only through the organization-qualified OAuth service seam when a provider read is permitted.

QuickBooks company/account data is organization accounting configuration. It must not be surfaced to coaches, guardians, athletes, public pages, or another organization.

## Provider-write invariant

Phase 29 closes with all of the following still true:

1. `QuickBooksService` reports `providerWritesEnabled = false`.
2. `QuickBooksProviderOperationService.providerWritesEnabled()` returns false.
3. `DisabledQuickBooksProviderWriteClient` is the only Phase 29 write/readback implementation and throws `QUICKBOOKS_WRITES_DISABLED`.
4. No QuickBooks activation endpoint exists in the Phase 29 API.
5. No Phase 29 endpoint can populate credential-verification, sandbox-verification, accounting-approval, or write-policy-approval evidence.
6. Export remains preview-only and returns `exportAllowed = false`.

A later credentialed activation phase must deliberately change these boundaries and update their tests/ADR rather than inheriting an accidental write path.
