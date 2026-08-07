# ADR-075 — Phase 25 Slice 25.2: Coach-to-Family Two-Way Messaging

**Status:** Accepted / implementation slice
**Date:** 2026-08-07

## Context

Phase 25.1 (ADR-074, V55) established append-only message threads, immutable per-message recipient/delivery snapshots, one-way organization/team broadcasts, in-app inbox/read state, and automatic guardian visibility for athlete-targeted broadcasts. Phase 25 calls for the next tier to be coach-to-family two-way messaging before any athlete-to-athlete direct messaging is enabled.

The key constraints are youth privacy, team relationship authorization, guardian transparency, append-only history, delivery-preference reuse, and incremental compatibility with every V55 broadcast row.

## Decision

V56 widens `message_thread.thread_type` from only `BROADCAST` to `BROADCAST | CONVERSATION` and adds `SELECTED` as the conversation-only audience. Existing V55 rows are not rewritten.

A two-way conversation is always TEAM-scoped in this slice. A caller with `team.communication.manage` chooses one or more activated guardians/athletes returned by a server-authorized team contact directory. Arbitrary requested user IDs are rejected unless they resolve to active guardian/athlete relationships on that exact team.

`message_thread_member` is a durable membership record. The creator is a reply-capable STAFF member; explicitly selected guardians/athletes are reply-capable TARGETED members. If an activated athlete is selected, linked activated guardians are automatically merged into the thread with `GUARDIAN_VISIBILITY` and `can_reply=false`. If the same guardian is explicitly selected too, TARGETED membership wins and the guardian may reply.

Every sent message remains append-only in `message_entry`. The sender is not duplicated into the message recipient snapshot. Every other active thread member receives a new immutable `message_recipient` row for that send. TARGETED members may get email and opted-in SMS based on their current delivery settings; GUARDIAN_VISIBILITY members are always in-app-only, preserving the V55 privacy rule at both service and database layers.

The member inbox now resolves visibility through either V55 recipient snapshots or V56 durable conversation membership. Conversation members can read the whole conversation including their own sends. Only members with `can_reply=true` may post through the `/me` reply endpoint. Broadcasts still reject replies. Team managers may inspect/archive team conversations through the management surface, but conversation posting occurs through membership rather than allowing any manager to inject messages into a conversation they did not join.

No message edit/delete endpoint is introduced. Archiving closes a thread to new sends/replies and preserves all history.

## Why team-scoped only

The required Phase 25 tier is coach-to-family messaging up to a full team roster. Keeping conversation creation TEAM-scoped gives the server an authoritative relationship boundary for every selectable guardian/athlete and avoids inventing an organization-wide family-DM policy before pilot evidence requires one. Organization-wide one-way broadcasts remain available from 25.1.

## Out of scope

- Athlete-to-athlete direct/group messaging.
- Athlete-created conversations or groups.
- Blocking, reactions, message editing/deletion, disappearing messages.
- Attachments, voice/video, typing indicators, presence, or WebSockets.
- User-visible read receipts for other members.
- Membership removal UI; `left_at` is reserved for a later controlled lifecycle if needed.

Athlete-to-athlete messaging remains gated by the safe-sport/compliance requirement in the product design and is not enabled by V56.

## Consequences

- V55 broadcasts and their immutable snapshots remain valid without backfill.
- V56 establishes the durable thread membership needed by later messaging tiers.
- Guardian transparency is structural rather than a sender-controlled toggle.
- A conversation recipient must have an activated Rally26 account to participate in two-way in-app messaging; unactivated contacts continue to rely on invitation/onboarding before they can be selected.
- The existing outbox EmailProvider/SmsProvider architecture remains the only external delivery path.
