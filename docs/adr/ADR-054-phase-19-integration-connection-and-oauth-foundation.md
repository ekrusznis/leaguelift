# ADR-054: Phase 19 integration connection and OAuth foundation

**Status:** Accepted
**Date:** 2026-08-01
**Phase:** 19 slices 19.0–19.2

## Context

Rally26 already has several provider-specific integrations and event-source workflows, but it did not have one safe connection lifecycle for future organization- and user-owned OAuth/API-token providers. The next providers have different ownership and visibility rules:

- Google Calendar is a user-owned optional connection for authenticated roles.
- QuickBooks Online and SportsEngine are organization-owned and manager-controlled.
- GameChanger and MaxPreps remain file/import or partner-pending; no undocumented client may be invented.
- Stripe, Printify, Resend, Twilio, DigitalOcean Spaces, and Google Maps are platform-operated and must never ask an organization for Rally26 credentials.

Phase 19 must prepare code without claiming credentials, vendor approval, or a working production connection.

## Decision

### Provider catalog

Create a migration-owned `integration_provider_catalog`. It records category, ownership, auth modes, baseline readiness, adapter mode, user-facing description, and activation boundary. The catalog is read-only in application code; there is no generic provider CRUD UI.

### Generalized connections

Create `integration_connection` with explicit `PLATFORM`, `ORGANIZATION`, or `USER` ownership and the status vocabulary defined in the design document. Sanitized API responses may expose connection identity, scopes, external account identity, expiry, health/sync timestamps, and redacted errors. They never expose credential references or ciphertext.

Existing ICS feed rows remain authoritative for the proven Phase 12 sync path. V40 backfills them into the generalized registry through `legacy_resource_id` for read visibility. A later integration-placement slice may replace or explicitly bridge that legacy path; this slice does not destabilize working imports.

### Credential storage

Store provider secrets only in `integration_credential_secret`, encrypted with AES-256-GCM and authenticated additional data binding the ciphertext to provider, owner, and connection. Runtime configuration supplies a base64 32-byte key and key version. A credential row can be rotated or revoked but is never returned to the browser.

Platform credentials remain in their existing environment-only provider properties and are not copied into this table.

### OAuth and PKCE

`integration_oauth_state` stores only a SHA-256 hash of the browser-visible state and an encrypted PKCE verifier. State is expiring, single-use, provider-bound, owner-bound, and connection-bound. The callback atomically consumes state before exchanging a code. Callback URIs are generated from one configured base URL, never accepted from the browser.

Authorization start, callback, refresh, provider revoke, local disconnect, and health-check use a provider-neutral adapter interface. Refresh uses a database lock with expiry to prevent concurrent token rotation.

### Disabled adapters and stubs

No official provider HTTP client is invented in this slice. A deterministic OAuth stub can simulate success, expiry, refresh, revoke, and health locally when explicitly enabled. Startup rejects stub mode in staging or production. Enabling a real provider without encryption, client configuration, or endpoints fails startup instead of activating a no-op adapter.

### Frontend

The organization Integrations section reads readiness from the backend catalog. It does not show platform-operated credentials or label unverified services active. QuickBooks, SportsEngine, GameChanger, and MaxPreps show accurate readiness and activation requirements. Existing ICS and CSV workflows remain in their dedicated sections. Connect buttons are deferred until the provider-specific placement slices.

### Regression fix

The Phase 18 final-payment unit test now models repository state before and after insertion (`0`, then the full paid amount). The service validation remains unchanged: a payment must be positive and no greater than the actual outstanding balance. A second test proves an already-zero balance rejects another payment.

## Consequences

- Phase 20 activation can supply credentials and an official adapter without redesigning storage, status, callback, refresh, revoke, or health behavior.
- `CONNECTED` is only written after successful callback exchange and encrypted credential persistence.
- Partner-pending providers cannot be promoted merely by setting a feature flag.
- Platform infrastructure is no longer misrepresented to organization users as a customer-owned connection.
- Full sync-run records, provider-specific mappings/contracts, user-facing Google Calendar placement, QuickBooks mapping, sports-provider adapters, and platform-provider health hardening remain later Phase 19 slices.
