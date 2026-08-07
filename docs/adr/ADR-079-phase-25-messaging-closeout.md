# ADR-079: Phase 25 Messaging closeout and lifecycle reconciliation

Status: Accepted — Phase 25 code-complete, with athlete messaging still operationally gated by ADR-077 until an external review is recorded.

## Lifecycle reconciliation

Open two-way threads are reconciled hourly and immediately before new sends. Future delivery/access follows current relationships without rewriting historical recipient snapshots:

- athletes no longer assigned to the team are marked left for future conversation delivery;
- targeted guardians no longer linked to an active athlete on the team are marked left;
- guardian visibility members are recalculated from the currently linked guardians of active athlete thread members; missing current guardians are added read-only and stale observer memberships are ended;
- staff history is retained, but every staff send re-runs the current `TEAM_COMMUNICATION_MANAGE` authorization check.

A former participant may still see messages that were historically delivered to their account, but cannot receive/send future thread messages based only on stale membership.

## Evidence preservation

V60 adds no-delete triggers for thread, member, and recipient rows. Threads continue to archive by status, memberships end via `left_at`, and delivery rows may update delivery/read state but are not deleted. Together with V57 append-only messages/moderation events, this keeps safety evidence coherent.

## Phase closeout

Phase 25 now contains all three planned tiers and the safety controls required to technically gate Tier 3. Athlete messaging is **not globally enabled by this ADR**. The separate SafeSport/compliance review remains a real launch prerequisite and must be recorded through V58 before the gate opens for an organization.
