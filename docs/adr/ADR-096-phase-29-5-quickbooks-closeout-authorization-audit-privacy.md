# ADR-096: Phase 29.5 — QuickBooks Authorization, Audit, Privacy, and Closeout

## Status
Accepted

## Context

Phase 29.1-29.4 created an activation-ready but intentionally inactive QuickBooks architecture: transport contracts, owner-customizable accounting mappings, posting intents, deterministic provider-operation identity, retry/readback seams, and truthful activation-readiness gates.

The final slice must prove those seams do not broaden customer accounting access, leak OAuth secrets, or accidentally create a provider-write path. It must also reconcile the Help Center and design documentation so the historical Phase 19 wording no longer implies that the old Phase 20 automatically activates QuickBooks.

A closeout review also found one ordering hardening opportunity: `validateMappings` loaded saved mapping rows before `listAccounts` invoked the organization-qualified connection authorization seam. An unauthorized request could not receive those rows because the later authorization exception aborted the call, but reading customer mapping state before authorization was unnecessary and weaker than the project's normal fail-closed standard.

## Decision

Phase 29.5 closes QuickBooks scaffolding under these rules:

1. QuickBooks organization setup remains Owner/Administrator functionality. Platform Admin customer access continues to depend on the existing Phase 14 reasoned organization support-access request guard; the employee remains the authenticated actor.
2. Connection-scoped QuickBooks operations continue through `IntegrationOAuthService.accessTokenForOrganizationConnection`, which qualifies the connection by both organization and connection ID.
3. Mapping revalidation is reordered so the organization/connection access check completes before `quickbooks_account_mapping` rows are queried.
4. Successful explicit mapping revalidation records `integration.quickbooks_mappings_revalidated` in the organization-scoped immutable audit stream. No provider credential or account payload is added to audit metadata.
5. Tests assert manager authorization precedes overview data access, foreign connection IDs fail before mapping/provider reads, revalidation emits audit evidence, and QuickBooks response DTOs contain no OAuth credential/secret fields.
6. V72 forward-updates the existing OWNER_ADMIN QuickBooks Help Center article. Applied historical migrations are not edited.
7. No new QuickBooks API contract is required in 29.5; the existing OpenAPI contract is reviewed rather than changed.
8. The Phase 29 closeout checklist requires full backend/frontend/OpenAPI/security/dependency/protected-main deployment gates. A failing gate is fixed, not bypassed.
9. QuickBooks remains inactive: `providerWritesEnabled` is false, export is preview-only, the disabled write client remains the only write/readback implementation, no activation endpoint is added, and no live Intuit credential is required.

## Consequences

- Organization/connection isolation is fail-closed before QuickBooks mapping state is read.
- Mapping revalidation becomes a distinct history event visible under Phase 27's role-scoped audit model.
- Help content accurately describes owner-customizable mapping and the future activation gates.
- Phase 29 can be marked product-complete without claiming Intuit credentials, sandbox verification, accounting approval, provider health, or production accounting writes.
- A future credentialed activation phase must deliberately replace the disabled write boundary and update the affected tests/ADR; it cannot become active merely by populating local metadata.

## Alternatives Considered

### Leave mapping revalidation authorization in the later provider-read call
Rejected. Although the request already failed before returning customer data, authorization should precede even internal reads of a foreign connection's mapping state.

### Add QuickBooks activation controls during closeout
Rejected. Closeout verifies the scaffold; it does not broaden Phase 29 into credentialed provider activation.

### Edit V42's old Help Center text in place
Rejected because applied Flyway history is immutable. V72 forward-corrects the published article instead.
