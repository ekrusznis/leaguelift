# ADR-094 — Phase 29.3 QuickBooks Idempotency, Error, Retry, and Readback Seams

**Status:** Accepted / implemented in scaffold
**Date:** 2026-08-09
**Phase:** 29.3
**Migration:** V70

## Context

Phase 29 must prepare Rally26 for a later QuickBooks Online Accounting API activation without making a live provider write now. Phase 29.1 established explicit Intuit transport/Fault contracts. Phase 29.2 established owner-reviewed account mappings and deterministic local posting intents. The next risk is operational: an accounting write can be duplicated if Rally26 loses the response, a stale QuickBooks object can reject an update, and provider throttling/system failures require different handling from business validation errors.

Intuit's current Accounting API documentation establishes several constraints that shape this decision:

- client-supplied `requestid` values correlate requests/responses and provide idempotence for write/modify/delete requests; non-batch request IDs are limited to 50 characters and must be unique for a realm;
- for an ambiguous create, query/readback before retry is the safe pattern; a document number is not itself a system idempotency key;
- HTTP 429 indicates throttling and Intuit instructs clients to wait 60 seconds before retrying;
- error 5010 is a stale-object conflict and requires retrieving the latest object/`SyncToken` before rebuilding the update;
- inactive/missing references such as 610/2500 require refreshing reference data rather than repeating the same request;
- `ValidationFault`, `SystemFault`, `AuthenticationFault`, and `AuthorizationFault` represent materially different recovery paths.

Reference documentation:

- https://developer.intuit.com/app/developer/qbo/docs/learn/learn-basic-field-definitions
- https://developer.intuit.com/app/developer/qbo/docs/learn/limits-and-throttles
- https://developer.intuit.com/app/developer/qbo/docs/develop/troubleshooting/error-codes
- https://developer.intuit.com/app/developer/qbo/docs/develop/troubleshooting/handling-common-errors

## Decision

### 1. Keep business-operation identity separate from exact-payload request identity

`QuickBooksRequestIdentityPolicy` produces:

1. a stable 64-character SHA-256 `operationKey` from organization, connection, realm, Rally26 source, provider operation kind, and provider entity type;
2. a 64-character SHA-256 `payloadHash` from the canonical provider payload;
3. a deterministic `r26-...` Intuit `requestid` derived from the operation key plus payload hash and limited to 50 characters.

The business operation key does not change merely because a payload is corrected. The Intuit request ID *does* change when the canonical payload changes. This prevents Rally26 from sending a corrected payload under an already-used provider idempotency key.

The same batch/source slot cannot silently accept a different operation key, payload hash, request ID, operation kind, or provider entity type. A changed accounting request requires a new reviewed operation instead of overwriting the original identity.

### 2. Reuse the Phase 19 `quickbooks_export_item` ledger

V70 extends the existing export-item table with provider entity/operation identity, request ID, attempt count, HTTP status, Intuit Fault metadata, `intuit_tid`, retry disposition, retry-not-before, and last-attempt timestamps.

Do **not** create a second QuickBooks provider-operation table. The existing flow remains the durable path from export batch/source item to a later provider result.

Existing pre-29.3 rows remain valid with nullable provider-operation metadata. New planned write operations store complete operation identity together.

### 3. Classify failure before deciding retry

`QuickBooksFailurePolicy` distinguishes:

- validation;
- authentication;
- authorization;
- throttling;
- stale object;
- missing/inactive reference;
- duplicate request ID;
- duplicate business key/document/name;
- closed accounting period/deposited-transaction restrictions;
- invalid/locked company status;
- transient system failure;
- ambiguous transport failure;
- unknown/manual-review failure.

A retry decision is derived from the failure category and operation kind. The policy does not treat every 4xx or 5xx the same.

### 4. Never blindly repeat an ambiguous financial write

For timeout, connection-close/failure, or 5xx/system outcomes where the write may have reached QuickBooks:

- `CREATE` requires query/readback using a stable provider reference before retry;
- `UPDATE`/`DELETE` requires reading the provider entity by ID before retry;
- if readback confirms the first operation succeeded, Rally26 records the provider result and does not create again;
- if readback confirms absence and the request is otherwise still valid, retry uses the **same Intuit request ID and identical payload**.

A duplicate Intuit request-ID response is treated as a readback/idempotency event, not permission to generate another write.

### 5. Provider-specific recovery rules remain deterministic

- HTTP 429: wait at least 60 seconds, then retry the identical request with the same request ID.
- 5010 stale object: read latest object/`SyncToken`, rebuild, and create a new exact-payload request identity if the payload changes.
- 610/2500/6250 reference problems: refresh provider reference data/mappings before rebuilding.
- 401/authentication fault: refresh current OAuth credentials once through the future credential manager; do not replay stale credentials.
- 403/authorization, closed books, invalid company status, duplicate business keys, and unknown failures: manual/provider-state review rather than automatic financial retries.
- validation errors: fix the request/data before a new provider operation is created.

### 6. Write/readback transport remains fail-closed

`QuickBooksProviderWriteClient` defines the future execution/readback seam. During Phase 29, the only Spring component is `DisabledQuickBooksProviderWriteClient`, and both methods always fail with `QUICKBOOKS_WRITES_DISABLED`.

No stub mode, environment flag, saved realm ID, mapping completion, or preview state may bypass this component and create/change a QuickBooks object.

## Consequences

### Positive

- later activation can add a credentialed HTTP adapter without redesigning financial retry semantics;
- corrected payloads cannot accidentally reuse an already-consumed provider request ID;
- ambiguous provider outcomes are resolved by readback instead of duplicate creation;
- provider trace/fault metadata is durable without storing credentials or raw response bodies;
- Phase 19's existing export-item ledger remains authoritative rather than adding a competing operation store.

### Tradeoffs

- entity-specific readback query construction still belongs to the later request-builder/activation phase because Phase 29.2 intentionally does not choose Invoice vs SalesReceipt vs Payment vs other live QuickBooks entities;
- V70 stores operation metadata before any provider write is permitted, so some columns remain unused until activation;
- a future adapter must capture `intuit_tid`, response IDs, latest `SyncToken`, and retry scheduling exactly as this policy expects.

## Non-goals

Phase 29.3 does not:

- obtain Intuit credentials;
- add OAuth scopes;
- select live QuickBooks transaction entity types for each Rally26 source;
- create/update/delete/query live QuickBooks financial objects;
- enable scheduled accounting synchronization;
- claim QuickBooks connection health;
- replace accounting review or owner mapping decisions.
