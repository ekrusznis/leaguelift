# ADR-076: Phase 25.3 Messaging Safety and Moderation Foundation

**Status:** Accepted
**Date:** 2026-08-07

## Context

Phase 25.1 introduced one-way organization/team broadcast threads and Phase 25.2 widened the same append-only thread/message model to staff-created, team-scoped coach-to-family conversations. The product roadmap also defines an athlete-to-athlete messaging tier, but explicitly gates that tier on a real safe-sport/compliance review, message retention for safety review, guardian transparency, and a moderation/reporting path.

Shipping athlete-created direct/group messaging before those controls exist would invert that dependency. Phase 25.3 therefore builds the safety boundary first and leaves athlete-created conversations disabled.

## Decision

### Report exact retained messages

Any authenticated user may report only a message they can actually see or that they sent. A report points to the exact `message_entry`, thread, organization, reporter, reason, and optional context. The database foreign key proves the message belongs to that thread and organization. One reporter may have only one active (`OPEN`/`IN_REVIEW`) report for the same message.

Supported reasons are `HARASSMENT`, `BULLYING`, `INAPPROPRIATE_CONTENT`, `SAFETY_CONCERN`, `SPAM`, and `OTHER`.

Reports are not broadcast to other conversation members. The reporter can see their own report status; authorized organization/team communication managers see the scoped review queue.

### Append-only moderation history

`message_moderation_event` records report creation, review start, resolution, dismissal, thread safety-lock, and thread unlock. Events cannot be updated or deleted. Report rows may change status but cannot be deleted. `RESOLVED` and `DISMISSED` are terminal states in application policy.

### Safety-lock instead of destructive moderation

Authorized reviewers may safety-lock a thread with a required reason. A lock preserves all thread/member/recipient/message history but blocks every new broadcast and every conversation reply. Unlocking requires a review note and creates another append-only moderation event.

Archiving and safety-locking remain different concepts: archive is the normal lifecycle; safety-lock is a reversible review control.

### Retention posture

V57 adds a database trigger rejecting `UPDATE` or `DELETE` of `message_entry`. Sent message content is therefore immutable after this migration. There is no product redaction/deletion path in Phase 25.3. This is the conservative safety-review posture while the final legal/data-retention duration remains an explicit pre-launch policy decision; this ADR does **not** claim that indefinite retention is the final legal policy.

Delivery/read state remains mutable in `message_recipient`, because those rows are operational state rather than message content.

### Guardian transparency remains mandatory

Phase 25.2's automatic guardian visibility membership remains unchanged. Phase 25.3 does not add any endpoint that allows an athlete to create a thread, invite another athlete, or remove a guardian observer.

### Athlete messaging stays gated

No athlete-created DM/group endpoint is introduced. The eventual athlete tier must be a later slice after the founder records the required safe-sport/compliance decision and the final retention/moderation policy is accepted. The safety foundation in this ADR is a prerequisite, not proof that the external review has happened.

## Authorization

- A user may report only a message visible to that user's account (recipient snapshot, active conversation membership, or own sent message).
- Organization-scoped reports require the existing organization manager role to review.
- Team-scoped reports require the existing `TEAM_COMMUNICATION_MANAGE` capability for that team.
- Cross-tenant IDs remain resource-scoped and do not expose report/message existence.
- Existing audited Platform Admin support-session access can reuse organization-scoped customer routes; no true impersonation is introduced.

## Consequences

- Safety concerns are reviewable without destroying evidence.
- Managers can pause a thread immediately while investigating.
- Every moderation outcome has actor/time/note history.
- Athlete-created messaging remains intentionally unavailable after this slice.
- A final retention duration, escalation/SLA policy, and safe-sport approval still require a real pre-launch stakeholder decision.

## Verification

- V57 constraints prove report message/thread/organization ownership and guardian/safety-lock consistency.
- Static checks assert no message-content update/delete repository path is added and the database trigger rejects those mutations.
- `MessageSafetyPolicyTest` covers normalization, terminal report states, required resolution notes, and lock/unlock note bounds.
- Frontend syntax and OpenAPI local references are validated in the slice handoff.
