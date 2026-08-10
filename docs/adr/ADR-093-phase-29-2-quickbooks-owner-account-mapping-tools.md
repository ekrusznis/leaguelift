# ADR-093: Phase 29.2 Owner-Customizable QuickBooks Account Mapping Tools

**Status:** Accepted
**Date:** 2026-08-09

## Context

Phase 19 established an organization-scoped QuickBooks Online scaffold with company/account reads, chart-of-accounts mappings, and export preview. Phase 29.1 then made the provider response contract explicit without activating QuickBooks.

A third-party accounting integration cannot safely assume that every organization uses Rally26-chosen account names. Organizations and their accountants maintain their own charts of accounts, and the same business activity may be grouped differently from one company to another. Rally26 therefore needs an owner-controlled mapping model that stores the provider's stable account identity, validates the accounting role, and survives account renames.

Phase 29 still prohibits live Intuit activation and provider writes. The mapping workflow must therefore be complete and testable against the existing scaffold/fixtures while remaining ready for a later credentialed chart-of-accounts read.

## Decision

### 1. The organization owner/admin controls account selection

Rally26 defines semantic accounting **roles**, not required QuickBooks account names. Phase 29.2 supports:

- `PROGRAM_FEE_INCOME`;
- `SALES_INCOME`;
- `CONTRIBUTION_INCOME`;
- `SPONSORSHIP_INCOME`;
- `REFUNDS`;
- `FEES_RECEIVABLE`;
- `BANK_CLEARING`;
- `PAYOUT_CLEARING`.

The owner/admin may intentionally map multiple Rally26 roles to the same QuickBooks `Account.Id`. There is no uniqueness rule on external account ID across mapping roles.

Recommendations are advisory only. Rally26 never auto-saves an inferred account mapping.

### 2. QuickBooks Account.Id is the durable key

A saved mapping persists the selected provider `Account.Id`. Account name, fully-qualified name, account type, and subtype are stored as review/audit snapshots only.

Renaming a QuickBooks account must not sever an otherwise valid mapping. Revalidation compares the saved ID with a refreshed chart of accounts and refresh-time provider metadata.

### 3. Compatibility has three explicit outcomes

Each Rally26 accounting role classifies a provider account as:

- `RECOMMENDED` — normal compatible account type;
- `ALLOWED_WITH_WARNING` — technically supported but nonstandard enough to require explicit owner/accountant acknowledgement;
- `BLOCKED` — inactive or structurally incompatible and therefore not selectable.

The server is authoritative. The frontend may present the server-provided role definitions and compatibility guidance, but the save endpoint re-evaluates the selected account and refuses blocked choices.

An `ALLOWED_WITH_WARNING` mapping is persisted only when `acknowledgeWarning=true` is supplied by the owner/admin.

### 4. Revalidation is explicit and fail-closed

Saved mappings are revalidated against the current provider chart and reported as:

- `MISSING`;
- `VALID`;
- `VALID_WITH_WARNING`;
- `NEEDS_REVIEW`;
- `INACTIVE`;
- `ACCOUNT_NOT_FOUND`;
- `INCOMPATIBLE`.

An inactive account remains visible for diagnosis but cannot be newly selected. A missing provider ID is not silently remapped by account name.

### 5. The owner-facing Integrations UI uses the same mapping model

The existing QuickBooks readiness panel is extended rather than creating a second settings workflow. When a later credentialed connection can read the organization chart, the panel can:

- refresh the chart of accounts;
- show the provider's actual name/FQN/type metadata;
- sort recommended choices ahead of warning/block choices;
- disable blocked/inactive options;
- require an explicit checkbox for warning-level choices;
- revalidate saved mappings on demand;
- display preview diagnostics.

The UI continues to state that provider writes are disabled.

### 6. Posting intents are deterministic preview classifications, not live accounting advice

Phase 29.2 introduces local posting-intent definitions for program-fee assessment/payment, merchandise, contributions, sponsorships, refunds/corrections, and payout settlement. These definitions establish which mapping roles and debit/credit sides later request construction must consider.

They do **not** choose a live QuickBooks transaction entity, override the organization's cash/accrual basis, or authorize a write. Source-specific reversal behavior, provider request shapes, and accounting-basis-sensitive construction remain subject to the later request/activation slices and accounting review.

### 7. No QuickBooks provider write is activated

Phase 29.2 does not create or change a QuickBooks:

- account;
- customer;
- invoice;
- sales receipt;
- payment;
- deposit;
- journal entry;
- refund;
- or any other provider entity.

`providerWritesEnabled` remains false. No live Intuit credential is required.

## Persistence

Migration V69:

- adds `PROGRAM_FEE_INCOME` to the mapping-type constraint;
- stores account FQN and subtype snapshots;
- stores compatibility at selection;
- stores explicit warning acknowledgement;
- deliberately does not add uniqueness on `external_account_id`.

## API surface

Phase 29.2 adds/extends organization-manager QuickBooks endpoints for:

- mapping-role definitions;
- posting-intent definitions;
- chart account metadata including inactive entries needed for revalidation;
- ranked mapping options for a role;
- saved-mapping revalidation;
- warning-aware mapping updates;
- richer export-preview mapping diagnostics.

All endpoints reuse the existing organization-manager authorization boundary.

## Consequences

- Rally26 supports organizations with custom accountant-defined charts of accounts instead of imposing account names.
- Account renames do not break mappings because provider IDs are authoritative.
- Owners retain flexibility while obvious structural mistakes are prevented.
- Intentional same-account reuse is supported.
- Stale/inactive/incompatible mappings become visible before a future export is enabled.
- The later live provider can replace fixture/scaffold account reads without redesigning the mapping workflow.
- Accounting request construction remains intentionally deferred to Phase 29.3+ and later credentialed activation.
