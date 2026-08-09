# ADR-092: Phase 29.1 QuickBooks API Contract Foundation

**Status:** Accepted
**Date:** 2026-08-09

## Context

Phase 19 created Rally26's organization-scoped QuickBooks scaffold: realm/company reads, chart-of-accounts selection, accounting mappings, export preview, and a provider seam. It intentionally did not implement a live Intuit HTTP client and hard-disabled provider writes.

Phase 29 must make that seam activation-ready without quietly converting scaffolding into a live accounting integration. The first risk to remove is contract ambiguity: Rally26 needs explicit wire DTOs and deterministic tests for the Intuit responses that a future credentialed provider will consume.

Current Intuit documentation establishes several important constraints for this slice:

- QuickBooks Online Accounting API entity contracts are defined in the API Explorer and are JSON-capable REST resources.
- Minor versions below 75 are discontinued/ignored; minor version 75 is the current minimum/default compatibility baseline.
- Company identity is available through `CompanyInfo`.
- The company home currency is a `Preferences.CurrencyPrefs.HomeCurrency` value; Rally26 must not guess currency from country.
- Chart-of-accounts data uses the `Account` entity, including `Id`, `Name`, `Active`, `Classification`, `AccountType`, and `AccountSubType`.
- Accounting API failures can be returned in a JSON `Fault` envelope with one or more `Error` entries and a fault type such as `ValidationFault`.

## Decision

### 1. Introduce a transport-only contract package

Add `com.rally26.integration.quickbooks.contract` containing DTOs for:

- `CompanyInfo` read responses;
- `Preferences` read responses needed for home currency;
- `Account` query responses;
- shared Intuit references/metadata;
- Accounting API `Fault` responses.

These DTOs are not persistence entities and are not Rally26 business models.

### 2. Pin deterministic contract fixtures to minor version 75

Phase 29.1 adds synthetic, schema-derived fixtures under `src/test/resources/fixtures/quickbooks` for:

- company info;
- preferences/home currency;
- account query results containing both active and inactive accounts;
- validation faults.

Fixtures contain no live Intuit credentials, tokens, realm data, customer data, or production payloads.

The DTOs ignore unknown additive fields so harmless provider schema additions do not break reads. Required Rally26 accounting values are still validated explicitly during domain conversion.

### 3. Map transport DTOs into the existing Phase 19 domain

`QuickBooksContractMapper` converts parsed transport data to the already-existing `QuickBooksCompany` and `QuickBooksAccount` models.

The mapper fails closed when a field Rally26 needs is missing. In particular, company currency must come from `Preferences.CurrencyPrefs.HomeCurrency.value`; country is not used to infer currency.

Inactive accounts are preserved by the transport/domain mapper. Existing application policy remains responsible for excluding inactive accounts from selectable mappings.

### 4. No runtime provider activation in 29.1

This slice does **not**:

- add Intuit client ID/client secret configuration;
- request OAuth scopes or tokens;
- add an HTTP client;
- change `QuickBooksProviderClient` wiring;
- create customers, invoices, sales receipts, payments, journal entries, or accounts;
- change `providerWritesEnabled = false`;
- claim connection health;
- add a migration, OpenAPI endpoint, or frontend control.

The current scaffold remains the only runtime behavior.

## Phase 29 slice sequence

1. **29.1 — API contract foundation:** DTOs, v75 fixtures, contract parser/mapper tests. **This ADR.**
2. **29.2 — Accounting mapping tools:** explicit Rally26 financial-source-to-QuickBooks posting intents, account-type compatibility rules, amount/sign/date/reference validation, and preview diagnostics. Still no provider writes.
3. **29.3 — Request/idempotency/error/retry seams:** deterministic request preparation, stable operation keys, query-before-retry/readback rules, HTTP/Fault classification, and retry policy interfaces. Still no live credentials or provider calls.
4. **29.4 — Activation boundary:** credential/provider capability model, sandbox-only readiness checks, connection-state truthfulness, and UI/API readiness presentation without activating writes.
5. **29.5 — Phase closeout:** authorization, audit/privacy/security review, Help/ADR/design-doc reconciliation, contract/OpenAPI review if needed, and full repository test/build gates.

## Consequences

- A future Intuit HTTP adapter can deserialize into stable Rally26-owned contracts rather than coupling application services directly to provider JSON.
- Rally26's existing Phase 19 domain and preview behavior remain authoritative.
- Currency mapping is correct by design for non-US companies because it comes from QuickBooks Preferences.
- Provider responses can evolve additively without making the scaffold brittle.
- No organization can accidentally become "connected" and no financial data can be written to QuickBooks during Phase 29.1.
