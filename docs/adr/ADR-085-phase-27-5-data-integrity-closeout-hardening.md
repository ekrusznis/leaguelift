# ADR-085: Phase 27.5 Data Integrity Closeout Hardening

- **Status:** Accepted
- **Date:** 2026-08-08
- **Decision owners:** Rally26 platform engineering
- **Related:** ADR-082 audit history, ADR-083 duplicate detection/preview, ADR-084 audited duplicate resolution

## Context

Phase 27.1–27.4 delivered structured audit history, role-scoped viewing, duplicate-identity detection/preview, and audited identity resolution. The closeout review against the combined branch found three integration gaps that should be corrected before Phase 27 is declared complete:

1. `AuditEventRepositoryIntegrationTest` still inserted the V1-era `app_user.external_subject` column even though V8 removed it.
2. ADR-084 and the 27.4 handoff described current messaging/announcement access as merge-aware, while the mutation implementation still treated those foreign-key dependencies as unsupported blockers.
3. The 27.4 handoff described database guards against restoring current access to a merged source account, but V64 did not actually create those guards.

These are hardening/integration corrections, not new product scope.

## Decision

### 1. Keep migration history immutable

V64 is not rewritten. Phase 27.5 adds forward-only `V65__identity_resolution_current_access_guards.sql`.

V65 rejects new current access for any `app_user` whose `merged_into_user_id` is non-null across:

- non-revoked `organization_membership` rows;
- active `role_assignment` rows;
- active `guardian_relationship` rows;
- active `message_thread_member` rows;
- in-app-visible `message_recipient` rows; and
- in-app-visible `announcement_recipient` rows.

Historical attribution columns are intentionally not guarded or rewritten.

### 2. Messaging authorship remains historical

`message_entry.sender_user_id` is classified as historical attribution, like `audit_event.actor_user_id` and `*_by_user_id` authorship fields. A user merge never rewrites who actually sent a historical message.

### 3. Current messaging access is handled explicitly

The duplicate planner now recognizes these current-access dependencies as supported:

- `message_thread_member.user_id`
- `message_recipient.user_id`
- `announcement_recipient.user_id`

The resolver handles them through domain-specific rules rather than a generic FK update.

For active message-thread membership:

- source-only membership moves to the survivor;
- an equivalent membership already held by the survivor closes the source membership using `left_at`;
- differing access semantics on the same thread abort the transaction rather than widening reply/access rights.

For in-app recipient access:

- source-only message/announcement access moves to the survivor;
- if the survivor already has access to the same message/announcement, the source delivery snapshot stays historically attributed to the retired source and is journaled as “already present”; no duplicate survivor recipient row is manufactured;
- delivery-only/non-visible snapshots remain historical.

### 4. Recovery journal records concrete messaging changes

The 27.4 `identity_resolution_operation.recovery_json` journal is extended with concrete IDs for:

- moved/closed message-thread memberships;
- moved message recipients;
- source message-recipient snapshots preserved because survivor access already existed;
- moved announcement recipients; and
- source announcement-recipient snapshots preserved because survivor access already existed.

No credential material is added and no automatic rollback endpoint is introduced.

### 5. Closeout tests use the existing PostgreSQL integration-test foundation

The existing Testcontainers/PostgreSQL integration-test pattern remains the source of truth. Phase 27.5 adds tests that cover:

- V65 merged-user current-access guards;
- preservation of audit/message authorship;
- messaging dependency classification;
- message-thread membership move/deduplication; and
- message/announcement in-app access reassignment.

The stale audit fixture is corrected to insert the current `app_user` schema without `external_subject`.

## Consequences

### Positive

- Phase 27 implementation and its handoff/ADR contracts agree.
- Normal messaging history no longer makes otherwise-safe duplicate merges impossible.
- Reply rights and other live access are not silently unioned.
- Merged source identities cannot accidentally regain current tenant/messaging access through later code paths.
- Historical audit and message attribution remains truthful.
- Flyway history remains forward-only and production-safe.

### Tradeoffs

- Recipient snapshots can continue to reference a retired source when the survivor already has equivalent access; this is intentional historical preservation, not live access.
- Unknown future app-user foreign keys still fail closed until explicitly classified.
- Full Testcontainers execution still requires Docker-capable local/CI infrastructure.

## Phase boundary

ADR-085 closes Phase 27. No further Phase 27 product slice is planned. The next roadmap phase is **Phase 28 — Consolidated Settings Page**.
