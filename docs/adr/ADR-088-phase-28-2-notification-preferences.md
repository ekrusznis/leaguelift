# ADR-088 — Phase 28.2 Typed Notification Preferences and Individual SMS Consent

**Status:** Accepted
**Date:** 2026-08-09

## Decision

Rally26 stores optional notification choices as typed `user_notification_preference` rows keyed by account and topic. Each topic has independent `DEFAULT`, `ENABLED`, or `DISABLED` state for in-app, email, and SMS. There is no generic settings JSON document.

Optional channel defaults are intentionally asymmetric: in-app defaults on; email defaults on subject to the existing household email compatibility guard when a guardian context exists; SMS defaults off. Account-level SMS requires a separate, current individual consent event plus an explicit `ENABLED` topic state. `user_sms_consent_event` is append-only so grant/revoke history is not overwritten.

Required account/security, invitation, payment/order/refund receipt, and legally required eligibility/e-sign receipt communications are outside this optional preference system and cannot be disabled by a user or future organization default.

## Compatibility

Existing household flags remain authoritative for contact-only recipients that do not have an activated account. Once a candidate has an active account, Rally26 resolves the user's typed preference before creating the immutable announcement/message recipient snapshot. An explicit account email preference may therefore supersede the household email fallback for that account. Household SMS opt-in never substitutes for individual account SMS consent.

Historical recipient rows remain immutable. A later preference change affects only future recipient snapshots; it does not rewrite prior announcements/messages or change a delivery retry after publication.

## Integration boundary

- `AnnouncementService` maps existing kinds to notification topics: GENERAL -> ANNOUNCEMENTS, CAMPAIGN_LAUNCH -> FUNDRAISING, EVENT_REMINDER -> EVENTS_SCHEDULE, FEE_REMINDER -> FEES_PAYMENTS, DOCUMENT_REMINDER -> DOCUMENTS_ELIGIBILITY.
- `BroadcastMessageService` and `ConversationMessageService` use MESSAGES for optional targeted delivery. Safety-required guardian visibility remains in-app even when optional message notifications are disabled, and those safety-only observer snapshots do not retain unused email/phone destinations.
- Existing provider handlers remain snapshot-driven and do not re-evaluate preferences during retries.

## Consequences

Phase 28.3 may add organization defaults only where a real delivery path consumes them. Organization settings may never grant SMS consent or suppress required communications. Future delivery producers should use `NotificationDeliveryResolver` before snapshot creation rather than duplicating preference logic.
