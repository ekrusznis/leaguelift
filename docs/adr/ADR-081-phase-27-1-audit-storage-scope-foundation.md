# ADR-081 — Phase 27.1 Audit Storage and Scope Foundation

**Status:** Accepted / implementation complete pending full repository integration test execution
**Date:** 2026-08-07
**Migration:** V62

## Context

Rally26 already records business audit events in a single PostgreSQL `audit_event` table, but the original Phase 0 schema only stores organization, actor, action, entity, metadata, and timestamp. Phase 27 introduces a role-scoped History Viewer where athlete, guardian, coach, organization administrator, and Platform Administrator visibility must be enforced from trustworthy relational scope.

The existing audit history is still small enough that this is the lowest-risk point to reshape storage before user-facing history queries depend on the legacy schema.

## Decision

### 1. Keep one logical PostgreSQL audit table, partitioned by month

`audit_event` remains the authoritative business-audit store. V62 recreates it as a declaratively range-partitioned table on `created_at`, with monthly UTC partitions from January 2026 through December 2030 and a default partition as a fail-safe.

The composite primary key is `(created_at, id)`, which satisfies PostgreSQL's partitioned uniqueness requirement and provides a stable future keyset-pagination cursor.

Historical rows are copied transactionally from the legacy table before the legacy table is dropped. No historical team, household, participant, or target-user scope is guessed from mutable current relationships.

### 2. Visibility scope is first-class relational data

The audit row now carries optional `team_id`, `household_id`, `participant_id`, and `target_user_id` in addition to `organization_id` and `actor_user_id`.

These columns are authorization inputs for Phase 27.2. Security-sensitive visibility must never depend on parsing arbitrary JSON metadata.

A database trigger rejects subordinate scopes that do not belong to the supplied organization and rejects a participant/household pairing that does not match the participant record.

### 3. Actor and outcome are explicit

Audit events add:

- `actor_type`: `USER`, `SYSTEM`, or `PROVIDER`
- `result`: `SUCCESS`, `FAILURE`, `DENIED`, or `PARTIAL`
- `summary`: safe human-readable/searchable text
- `correlation_id`: optional request/workflow correlation

Existing `AuditService.record(...)` callers remain source-compatible. User-backed events default to `USER`; null-user events default to `SYSTEM`; result defaults to `SUCCESS`; summary defaults to the action code.

### 4. Audit history is database-enforced append-only

V62 rejects direct `UPDATE` and `DELETE` against `audit_event`. Corrections must be represented by a new audit event.

This protects future Platform Admin merge history, messaging safety history, billing history, and ordinary organization activity from silent rewriting.

### 5. Index for the role-query shape, not arbitrary metadata search

Partition-local indexes are created for organization, team, household, participant, actor user, target user, entity, action, result, and correlation lookups ordered by `(created_at, id)`. A BRIN timestamp index supports large time-range scans.

Phase 27.1 deliberately does not create a keyword index over `metadata`. Phase 27.2 will define the safe keyword grammar over `summary`, action/entity labels, and authorized actor/target display information before choosing a full-text or trigram strategy.

## Testing

The slice adds:

- domain tests proving the original positional `AuditEvent` construction receives safe defaults,
- a real-PostgreSQL integration test asserting the parent is partitioned,
- physical routing into the August 2026 partition,
- rich-scope round-trip persistence,
- rejection of cross-organization team scope,
- database rejection of UPDATE and DELETE.

The integration test uses Rally26's existing Testcontainers `AbstractIntegrationTest` harness and is intended to run with the complete repository test suite.

## Consequences

- Phase 27.2 can implement SQL-level role visibility without schema surgery.
- Audit growth can be managed by partition maintenance and, later, detached cold-storage partitions without changing the application-facing table.
- Existing audit callers continue to compile while domains incrementally add richer scope.
- The default partition prevents an omitted future partition from breaking audit writes, while named partitions should still be extended through normal Flyway migrations before 2031.
