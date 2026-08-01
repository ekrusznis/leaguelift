# ADR-050: Phase 17 Action Center, announcements, and reminder delivery

**Status:** Accepted and implemented locally
**Date:** 2026-08-01

## Context

ADR-049 delivered the Help Center, durable support intake, Platform Admin support tooling, and the shared footer prerequisite first. The remaining Phase 17 scope requires a role-aware Action Center, organization/team/tournament announcements, and campaign/event/payment/document reminders delivered through the existing in-app/audit/outbox/email/SMS architecture.

LeagueLift already has authoritative domain tables for the work items users need to address, an Activity Feed backed by `audit_event`, an outbox worker, `EmailProvider`, `SmsProvider`, and household email/SMS preferences. It does not have a tournament-to-participating-team relationship table, a general dismissal/snooze model, a chat model, or production LeagueLift-domain delivery credentials.

## Decision

1. **Compute the Action Center from source-of-truth records.** `GET /api/v1/me/action-center` aggregates currently actionable records across every context the authenticated user actually holds. No copied task table, dismissal flag, or manually editable count is introduced.
2. **Expose Action Center and Announcements universally.** Every authenticated dashboard context and the shared application shell link to `/app/action-center` and `/app/announcements`; compact badges use the same real endpoints.
3. **Use explicit role-aware action rules.** Owner/administrator actions include pending profile-correction review, overdue fees, and unpublished events. Scoped team/tournament staff receive event-review actions. Guardians receive fee, document-acknowledgment, and RSVP actions. Controlled athletes receive their own RSVP actions. Requesters receive support cases waiting on them. Platform Administrators receive urgent/high support queue and delivery-failure actions.
4. **Add one durable announcement model.** V36 adds organization-owned `announcement` records scoped to `ORGANIZATION`, `TEAM`, or `TOURNAMENT`, with `DRAFT`, `PUBLISHED`, and `ARCHIVED` lifecycle states, explicit audience and channel choices, optional related-resource identity, and a unique source/idempotency key.
5. **Snapshot recipients at publication.** `announcement_recipient` records the resolved destination, recipient type, optional user/household, in-app visibility, read state, channel delivery states, and redacted failure summary. Later roster, role, or preference changes do not silently rewrite the historical publication audience.
6. **Authorize communication by scope.** Organization communication remains owner/administrator controlled. Team and tournament communication use dedicated `team.communication.manage` and `tournament.communication.manage` capabilities. The React UI filters controls, but the Kotlin service is the enforcement boundary.
7. **Keep delivery one-way.** Every recipient with an active account receives an in-app inbox copy. Email is attempted only when enabled and an eligible address exists. SMS is attempted only when enabled and the household opted in. Household email reminder opt-out is honored for guardian destinations. No reply thread, chat, voice, or SLA behavior is added.
8. **Reuse the outbox.** Publishing writes one `announcement.published` outbox event. A handler retries only destinations not already marked `SENT` and records `SENT`, `FAILED`, or `SKIPPED`; email also carries a provider idempotency key. SMS remains at-least-once if a provider accepts a message but the local success update fails, because the existing `SmsProvider` seam has no provider-idempotency field. A provider failure never removes the announcement or recipient snapshot. Existing failed/dead-letter operations remain the retry boundary.
9. **Keep Activity Feed semantics through audit.** Create, update, publish, archive, and reminder actions are audited. The dedicated inbox is the recipient-facing in-app message surface; the existing audit-backed Activity Feed remains the operational activity record rather than being replaced.
10. **Use the same pipeline for explicit reminders.** Authorized staff may send campaign-launch, upcoming-event, outstanding-fee, and household-document reminders. Each requires a caller-supplied idempotency key and creates a typed announcement. Ordinary imports, edits, and scanner runs do not silently mass-message families.
11. **Do not fabricate tournament family delivery.** Because no `tournament_team`/participating-team relationship exists, tournament announcements currently resolve staff only. Guardian/athlete audience choices are not presented for tournament scope until a real relationship model exists.
12. **Keep production activation separate.** Local/logging and configured provider adapters can exercise the workflow, but LeagueLift-owned sender identities, DNS, provider credentials, bounce/complaint verification, and production delivery remain Phase 20.

## Consequences

- Every authenticated persona has one real work queue and one real announcement inbox.
- Communication history and delivery outcomes survive provider failures and are auditable.
- Recipient snapshots make a publication explainable and prevent retroactive audience drift.
- Explicit reminders avoid import/edit floods and preserve staff review before outreach.
- Action Center items disappear when the underlying domain record is resolved; there is intentionally no independent dismiss/snooze state.
- Tournament-wide guardian/athlete communication remains unavailable until participating-team relationships are modeled.
- Scheduled announcements, digests, reply threads, chat, voice, attachments, read receipts beyond the current user, and SLA promises remain out of scope.
