# ADR-084: Phase 27.4 Audited Duplicate Identity Resolution

- **Status:** Accepted
- **Date:** 2026-08-08
- **Decision owners:** Rally26 platform engineering
- **Related:** ADR-043 Platform Admin support access, ADR-082 audit history, ADR-083 duplicate detection and merge preview

## Context

Phase 27.3 introduced a read-only duplicate scanner and deterministic preview over `app_user` and unlinked `household_adult` guardian shells. Phase 27.4 is the first slice allowed to mutate duplicate identity state. These records sit on authorization, household, authentication, and immutable audit boundaries, so a generic "merge every foreign key" operation would be unsafe.

The mutation must preserve youth/household privacy boundaries, avoid privilege expansion, preserve historical actor attribution, remain recoverable by support, and be resistant to stale previews or direct API calls that bypass the UI.

## Decision

### 1. Only two mutation shapes are supported

Phase 27.4 executes only:

1. `GUARDIAN_SHELL -> APP_USER`: create the missing active `guardian_relationship`. The organization-owned `household_adult` row remains in place.
2. `APP_USER -> APP_USER`: move or deduplicate explicitly supported current relationships, then retain the source `app_user` as `SUSPENDED` with `merged_into_user_id` / `merged_at` pointing to the survivor.

Shell-to-shell resolution and app-user-to-shell targets remain blocked review cases.

### 2. Resolution is bound to a fresh deterministic preview

The preview now includes a SHA-256 `previewHash` over the identity state, active memberships, active resource roles, guardian links, dynamic source dependency inventory, shared duplicate evidence, strategy, support scope, and plan codes/severities.

The POST request supplies that hash. Inside one transaction the server:

- locks source and target identity rows in deterministic order;
- locks their known mutable association rows;
- rebuilds the preview from the database;
- rejects a changed hash or any new blocker before writing.

The endpoint also independently verifies that source and target still share normalized email or phone evidence. A caller cannot safely bypass the candidate scanner by posting unrelated IDs.

### 3. Platform support authorization is mandatory

The mutation requires both `PLATFORM_USER_MANAGE` and `PLATFORM_SUPPORT_ACCESS`.

The preview must resolve to exactly one safe organization support scope. The request must supply an active, reasoned Platform Admin support-access session for that organization. The support-access row is locked before validation so ending the session cannot race the mutation commit.

Automatic app-user merges whose source identity currently spans zero or multiple tenant organizations are blocked for manual review.

### 4. Privileges never union automatically

For organization memberships and resource-scoped role assignments:

- source-only supported rows may move to the surviving target;
- exact duplicates are preserved on the target and the source duplicate is revoked;
- different role or status values for the same scope are hard blockers.

Any identity that has a Platform Admin role assignment is non-mergeable by this customer-identity tool. The persisted canonical role value is `PLATFORM_ADMIN`.

### 5. Unknown live dependencies block

Phase 27.4 continues to inspect PostgreSQL foreign-key metadata dynamically. It explicitly handles only known current identity relationships and authentication-token tables. Historical attribution references remain on the source. Any other live source dependency is a blocker rather than an inferred UPDATE.

Historical references include immutable `audit_event.actor_user_id`, grant attribution such as `role_assignment.granted_by`, and other `*_by_user_id` attribution columns.

### 6. Source credentials are retired immediately

A merged app-user source is retained for history but is no longer a valid authentication identity:

- status becomes `SUSPENDED`;
- active email-verification and password-reset tokens are consumed;
- password authentication rejects every non-`ACTIVE` account;
- JWT conversion reloads account status and rejects every non-`ACTIVE` account, invalidating already-issued source JWTs on their next request.

The surviving target owns the usable login identity.

### 7. Every successful mutation leaves a durable receipt and an audit event

Migration V64 creates `identity_resolution_operation`, recording the operation type, polymorphic source/target IDs, organization, Platform Admin actor, support-access session, reason, preview hash, outcome counts, and non-secret recovery metadata.

A partial unique index allows only one completed resolution per source identity, supporting idempotent retries. The operation row and normal `audit_event` are written in the same transaction as the mutation.

Recovery metadata records row IDs moved/revoked and the pre-merge source status. It intentionally does not contain passwords, tokens, or a generic automatic rollback procedure.

## Consequences

### Positive

- Duplicate resolution becomes usable without sacrificing the Phase 27.3 dry-run safety model.
- Privilege conflicts cannot be silently widened.
- Historical audit actors remain truthful.
- Source credentials stop working immediately after a merge.
- Completed operations are support-traceable and idempotent.
- Future foreign-key additions fail closed until explicitly supported.

### Tradeoffs

- Some legitimate multi-organization or conflict-heavy duplicates remain manual-review cases.
- Source `app_user` rows are retained rather than physically deleted.
- Recovery is a deliberate support/domain operation; Phase 27.4 does not expose a one-click rollback endpoint.

## Rejected alternatives

- **Generic FK reassignment:** rejected because authorization and historical references have different semantics.
- **Hard-delete source users:** rejected because it breaks immutable attribution and recovery.
- **Trust the UI candidate group:** rejected because mutation authorization and duplicate evidence must be enforced server-side.
- **Merge all memberships/roles into a union:** rejected because it can silently create privileges.
- **Allow one support session to authorize a multi-organization merge:** rejected because support access is organization-scoped by design.
