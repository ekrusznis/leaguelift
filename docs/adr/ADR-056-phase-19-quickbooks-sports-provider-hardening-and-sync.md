# ADR-056: Phase 19 QuickBooks, Sports-Data, Platform Hardening, and Sync Scaffolds

**Status:** Accepted
**Date:** 2026-08-02

## Context

Phase 19 must leave provider activation as credential, official-contract, and verification work rather than a redesign. Google Calendar and the generalized connection/OAuth foundation already existed after ADR-054/055. The remaining scope was QuickBooks Online, SportsEngine, GameChanger/MaxPreps partner-pending workflows, platform-provider hardening, and durable sync/issue visibility.

Rally26 does not currently possess an Intuit application, a verified current SportsEngine API product, or official direct GameChanger/MaxPreps access. Stripe, Printify, Resend, Twilio, and DigitalOcean Spaces are platform-operated and must never appear as organization-owned credential forms.

## Decision

1. Add a provider-neutral `integration_sync_run` and `integration_sync_issue` history. Runs preserve owner, provider, connection, trigger, direction, counts, cursor/checkpoint, rate-limit metadata, redacted errors, and immutable issues.
2. Model QuickBooks as an organization-owned OAuth connection limited to owners/administrators. Persist realm/company settings, reviewed chart-of-accounts mappings, idempotent export-preview batches, and source-item identities. Phase 19 never sends accounting writes; `providerWritesEnabled` is always false.
3. Provide deterministic local/test QuickBooks company/account fixtures. Non-stub environments fail closed until the official Intuit client and sandbox contract tests are supplied.
4. Model SportsEngine external organization/team/participant/roster/event identity and preview runs. The local/test client can return deterministic fixtures; no non-stub HTTP client is invented.
5. Keep GameChanger and MaxPreps `PARTNER_PENDING`. Their scaffold accepts reviewed file-shaped records for validation/preview only and never scrapes or automates a consumer website. Existing CSV event import and ICS feeds remain the supported operational paths.
6. Add sanitized Platform Admin contract readiness for Stripe, Printify, Resend, Twilio, and Spaces. It reports credential ownership, rotation posture, webhook-verification seam, idempotency, and code capability. A provider-neutral signed-webhook verifier registry, credentialed-health adapter seam, and fail-closed Stripe subscription-billing provider are present, but no unverified live probe or subscription call is made.
7. Add optional Printify/Resend webhook-secret configuration fields for future verified callback activation. Missing values do not fabricate readiness.
8. Publish Help Center articles for QuickBooks readiness, SportsEngine readiness, GameChanger/MaxPreps imports, and sync-history/error interpretation.

## Consequences

- Phase 20 can activate providers independently after official documentation/access review, credentials, sandbox calls, scope/rate-limit validation, webhook fixture verification, and disconnect/recovery rehearsal.
- QuickBooks account mapping and export previews can be tested locally without creating an accounting write.
- Sports-data mappings and preview issues have stable persistence without silently mutating organization, roster, participant, or event records.
- Platform Admins receive operational transparency without access to secret values.
- Phase 19 is complete locally, but no provider that lacked verified credentials or official access is claimed connected or live.
