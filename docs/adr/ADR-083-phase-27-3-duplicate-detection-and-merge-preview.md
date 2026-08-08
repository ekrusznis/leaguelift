# ADR-083 — Phase 27.3 Duplicate Detection & Merge Preview

**Status:** Accepted for implementation  
**Date:** 2026-08-08

## Context

Phase 27.1 established structured audit data and Phase 27.2 exposed role-scoped audit history. The remaining Platform Admin data-integrity work begins with duplicate identity review. Rally26 intentionally creates organization-owned guardian/participant shells before login activation, and invitation acceptance must never silently create or link an ambiguous duplicate identity.

The production database may contain app users whose normalized email differs only by case from another account, app users whose linked guardian profiles share a phone/email with another identity, or unlinked `household_adult` shells that match an existing account or another shell. Manual onboarding also records organization-scoped external IDs; those IDs are useful corroborating evidence but are not treated as globally unique person identifiers.

## Decision

Phase 27.3 is **read-only/dry-run**. It adds a Platform Admin Data Integrity page and GET-only API surface that:

1. Finds duplicate candidate groups using normalized email and phone evidence.
2. Represents active `app_user` identities and **unlinked active guardian shells** separately so a legitimate `guardian_relationship` is not itself reported as a duplicate.
3. Shows organization/household context, memberships, existing links, platform-admin status, and any guardian onboarding external IDs.
4. Builds a source→target preview and dynamically inventories every current foreign-key dependency that references the source `app_user` or `household_adult` row.
5. Produces deterministic plan items and explicit blockers. No role/privilege union is automatic.
6. Preserves audit/history attribution rather than planning to rewrite historical actor references.
7. Exposes **no POST/PUT/PATCH/DELETE endpoint and adds no migration**. V63 remains the latest migration after this slice.

## Matching rules

- Email: lowercase + trim, exact equality after normalization.
- Phone: digits only, minimum seven digits, exact equality after normalization.
- Guardian-shell candidates exclude shells that already have an ACTIVE `guardian_relationship`; their linked app user represents that identity in candidate matching.
- An app user inherits email/phone match evidence from its ACTIVE guardian relationships, allowing a linked guardian profile to match another unlinked shell or app user.
- `onboarding_import_identity.external_id` values are displayed as evidence. They are organization-scoped by schema and therefore are not treated as a safe global person-match key.

## Preview strategies

- **GUARDIAN_SHELL → APP_USER:** `LINK_SHELL_TO_EXISTING_USER`. The household-adult row stays in place; 27.4 may create the missing guardian relationship after conflict checks.
- **APP_USER → APP_USER:** `MERGE_USER_ACCOUNTS`. Memberships, role assignments, guardian relationships, and all other FK references must be resolved explicitly. Conflicting organization roles block the mutation preview rather than unioning privileges.
- **GUARDIAN_SHELL → GUARDIAN_SHELL:** direct row merge is blocked. The safer resolution is to determine whether both shells should link to the same adult account while retaining organization-owned profile rows.
- A guardian shell cannot be the surviving account target.
- Platform Administrator accounts are hard-blocked from customer-account merge/link targets.

## Dependency inventory

The repository reads PostgreSQL foreign-key metadata from `information_schema` and counts references to the selected source identity. This prevents the preview from silently becoming incomplete as later migrations add user or guardian references. Table and column identifiers are accepted only when they match a strict server-side identifier regex before being interpolated into count SQL.

`audit_event.actor_user_id` is classified as historical and is planned for preservation, not reassignment.

## Security and privacy

The API requires the existing `PLATFORM_USER_VIEW` capability and is available only in the Platform Admin console. It does not expose password hashes, reset tokens, invitation tokens, provider secrets, financial credentials, or document contents. External IDs are operational onboarding identifiers only.

Opening customer organization workspaces still follows the existing reasoned, time-bounded support-access model. This slice does not bypass that model or impersonate customers.

## Consequences

Phase 27.4 can implement mutation only after these preview rules are exercised against real pre-production data. 27.4 must add its own audited confirmation/reason flow, concurrency protection, transaction boundaries, source-account retirement model, and rollback/recovery strategy. It must not simply translate every INFO plan item into a blind update.
