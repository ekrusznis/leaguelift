# ADR-074 — Phase 25 Slice 25.1: Broadcast Messaging Foundation

**Status:** Accepted / implementation slice

## Context

Phase 25 defines three communication tiers. Tier 1 is organization/team one-way broadcast messaging, followed by coach-to-family two-way messaging and later athlete-to-athlete messaging. The existing Announcement system already proves recipient resolution, household email/SMS preference enforcement, immutable recipient snapshots, outbox delivery, and audit conventions, but an announcement is a one-time publication rather than a durable conversation thread.

## Decision

Introduce a separate messaging domain with `message_thread`, `message_entry`, and `message_recipient` records. Phase 25.1 supports only `BROADCAST` threads scoped to an organization or team. Authorized staff create a thread and append messages; recipients can read and mark messages read but cannot reply.

Each message independently resolves the current roster and snapshots recipients and delivery coordinates at send time. Later roster changes never rewrite prior delivery history. Email and opted-in SMS use the existing provider/outbox model. Sent messages have no edit/delete endpoint. Archiving a thread is terminal for new sends but retains history.

Recipient discovery deliberately reuses `AnnouncementRepository`'s proven organization/team staff, guardian, and activated-athlete queries so household email opt-out and SMS opt-in behavior remain identical.

When the selected audience is `ATHLETES`, linked guardians of those activated participant accounts receive an additional `GUARDIAN_VISIBILITY` recipient row. That row is in-app only and cannot produce email/SMS. If the same guardian is also a targeted recipient, the targeted record wins. This satisfies the founder requirement that guardians have non-optional read visibility into an athlete's messaging while preserving the sender's selected external-delivery audience.

## Boundaries

- No recipient replies in 25.1.
- No coach-to-family direct/group conversation yet; that is the next Phase 25 slice.
- No athlete-to-athlete messaging. It remains gated on the documented safe-sport/compliance review, retention, moderation, and reporting requirements.
- No tournament messaging in this slice; tournament Announcements remain the supported one-way mechanism because the Phase 25 scope explicitly begins with organization/team messaging.
- No WebSocket/realtime-presence dependency is introduced. In-app state is durable API data; email/SMS are offline fallbacks through the existing outbox worker.

## Consequences

Tier 2 can extend the thread/message foundation with explicit participants and reply permissions without migrating one-time Announcements into a chat model. Historical audience/delivery evidence stays immutable and auditable.
