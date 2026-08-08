# ADR-082: Phase 27.2 Role-Scoped Audit History Viewer

**Status:** Accepted
**Date:** 2026-08-07

## Context

Phase 27.1 (ADR-081, V62) made `audit_event` partitioned, append-only, and explicitly scoped by organization, team, household, participant, actor, and affected user. The product now needs one History/Audit viewer without turning the audit table into a cross-tenant directory.

The visibility contract is hierarchical but relationship-based:

- an athlete sees events performed by that user or scoped to that user's participant record;
- a guardian sees self activity plus events scoped to currently linked household(s)/participant(s);
- team staff see events explicitly scoped to teams for which they currently hold an active `COACH_READ`, `TEAM_EDITOR`, or `TEAM_MANAGER` grant;
- an organization OWNER/ADMINISTRATOR sees all events scoped to organizations they currently administer;
- a Platform Administrator sees the full platform audit stream.

Users can filter/sort only inside the rows they are already entitled to see. An optional filter can never widen visibility.

## Decision

1. **One endpoint and one viewer.** `GET /api/v1/audit-history` backs both `/app/history` and the existing Platform Admin Audit route.
2. **Authorization is SQL-side.** The audit query itself contains the current membership/role/guardian/athlete relationship predicates. Rally26 never retrieves a broad audit set and filters it afterward in application code.
3. **Current relationships govern current visibility.** Revoking a team assignment or guardian relationship removes future query access to that scope without altering historical rows. OWNER/ADMINISTRATOR access is similarly based on active organization membership.
4. **Self/affected visibility is explicit.** `actor_user_id = viewer` and `target_user_id = viewer` are visible regardless of broader role. Participant-self grants additionally expose rows scoped by `participant_id`.
5. **Role-aware filters.** Everyone can filter date/action/result/keyword and sort date/action/result. Guardian and higher scopes can filter by user name; coach/team-manager and higher can filter by team; owner/administrator and Platform Admin can filter by organization. Unauthorized filter parameters are rejected, not silently honored.
6. **Safe keyword surface only.** Keyword search covers `summary`, `action`, `entity_type`, actor/target display names, and scoped organization/team/household/participant labels. `audit_event.metadata` is deliberately excluded.
7. **Stable keyset pagination.** Cursor pagination uses the selected sort value plus `(created_at, id)` as deterministic tie-breakers. Offset pagination is not used on the potentially large audit stream.
8. **Search index.** V63 enables `pg_trgm` and adds a GIN trigram index only over the immutable safe event text (`summary + action + entity_type`). Joined display names remain queryable but are not copied into the audit record.
9. **Compatibility scope inference.** Existing audit writers are not required to migrate atomically. `AuditEventRepository` infers safe structural scope for ORGANIZATION, TEAM, HOUSEHOLD, PARTICIPANT/ATHLETE, EVENT, MESSAGE_THREAD/MESSAGE, and USER entities when the caller did not already supply that scope; explicit caller-provided scope always wins and V62 integrity triggers still verify it.
10. **No sensitive payload surface.** The API never serializes `metadata` or raw provider/request payloads.
11. **Existing platform route remains.** `/app/platform/audit` becomes a Platform-specific presentation of the same `AuditHistoryPage`, preventing two audit implementations from drifting.

## Consequences

- Phase 27.3 can build duplicate-account detection/merge preview with a trustworthy, role-scoped history surface already available.
- Older audit rows that predate explicit team/household/participant scope remain visible only through the safe scopes they actually contain; Rally26 does not infer missing historical scope.
- Domain services should progressively populate the richer V62 scope columns so team/household/athlete viewers receive meaningful history.
- Platform-wide queries remain safe but can become large; the V62 monthly partitioning and V63 safe-search index support the expected access pattern.
