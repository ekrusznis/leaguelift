# ADR-023: Phase 8 Slice 2 — Notification Trigger Expansion

## Status
Accepted

## Context

ADR-022 (Phase 8 slice 1) built the outbox worker and a real Resend-backed
`EmailProvider`, but only wired two triggers to it: `membership.invited` and
the sponsorship renewal reminder. DESIGN-DOC.md section 13's Communication
catalog lists a wider set: "payment reminders, campaign launch emails,
fundraising reminders, order confirmation, shipping updates,
event-change/RSVP reminders, thank-you messages, sponsor-renewal reminders,
notification preferences." Shipping updates and event-change/RSVP reminders
depend on infrastructure that doesn't exist yet (real non-draft fulfillment,
and Phase 10's event model respectively) and stay out of this slice. This ADR
covers the rest: fee-payment reminders, order confirmation, contribution
thank-you emails, and sponsorship approval/refund notices — plus a minimal
household-level email opt-out, the "notification preferences" catalog item's
first real implementation.

**"Campaign launch emails" is explicitly deferred, not built.** There is no
subscriber/mailing-list model anywhere in this codebase — a campaign has no
list of people who asked to be notified when it launches. Building one would
be a new, independently-scoped feature (subscription capture, consent,
unsubscribe), not a trigger wired to existing data the way every other item
in this slice is. Inventing a contrived recipient (e.g. emailing every
household in the org) was rejected as scope creep nobody asked for and a
likely spam complaint waiting to happen.

## Decision

**1. Fee-payment reminders: a new `FeePaymentReminderScanner`/`Handler` pair,
mirroring `SponsorshipRenewalScanner`/`Handler` exactly, including its
mark-reminded-in-the-scanner correction.** `fee_assignment` gains
`payment_reminder_sent_at` (V20). `FeeRepository.findNeedingPaymentReminder`
finds `OPEN`/`PARTIALLY_PAID` assignments with a real positive balance, a
`due_date` within `rally26.fee.payment-reminder.days-before` (default 3),
not yet reminded, whose household hasn't opted out (decision 5). The scanner
enqueues a `fee.payment_reminder_due` outbox event per candidate and marks it
reminded immediately (not in the handler) for the same reason ADR-022
corrected the sponsorship version: a fee assignment stuck retrying through
outbox backoff must not be re-enqueued by the next day's scan. The recipient
is `household.contact_email` — the only per-household contact field that
already exists (not a specific `household_adult`, which would need a "who's
the billing contact" decision this slice doesn't need to make).

**2. Order confirmation and contribution thank-you emails: written directly
in `OrderService.confirmFromWebhook`/`ContributionService.confirmFromWebhook`,
not scanned.** Unlike fee/sponsorship reminders, there's no polling
involved — confirmation is a webhook-driven, one-time event with everything
the email needs already in hand (`Order.supporterEmail`/`supporterName`,
`Contribution.supporterEmail`/`supporterName`). Both services gained an
`OutboxWriter` and `ObjectMapper` dependency; the event (`order.confirmed`
payload: email/name/total/currency; `contribution.confirmed` payload:
email/name/amount/currency/campaign name — looked up once via
`campaignRepository.findById` since `Contribution` doesn't carry its
campaign's name) is written only when `supporterEmail != null`, since that's
the only reason either event would ever exist — unlike the invitation/
renewal-reminder pattern, there's nothing for a handler to usefully skip at
send time if there's no recipient to begin with. `OrderConfirmationEmailHandler`/
`ContributionThankYouEmailHandler` just send.

**3. Sponsorship approval and refund notices: written directly in
`SponsorshipService.approve`/`performRefund`.** `sponsorship.approved` fires
from `approve()`; `sponsorship.refunded` fires from `performRefund()` —
the one method both `reject()` (refund-triggered-by-rejection) and the
general `refund()` already share, so one trigger point covers both refund
paths without duplicating logic. Unlike order/contribution, a sponsor's
contact email is genuinely optional (`Sponsor.contactEmail: String?`) and
known only via a repository lookup at the point of the state change (not
carried on `Sponsorship` itself) — both handlers (`SponsorshipApprovedEmailHandler`/
`SponsorshipRefundedEmailHandler`) still check for a null contact email and
skip silently, matching the renewal reminder's existing posture, because
*that* lookup can still turn up nothing even though the write always happens
when a sponsor record exists.

**4. All four new payloads use typed Jackson serialization
(`objectMapper.writeValueAsString`/`readValue(..., XPayload::class.java)`),
not hand-built JSON strings.** `InvitationService`/`MediaUploadService`/
`MediaAssignmentService` (Phase 0-era) all hand-interpolate JSON strings
because every field they embed is a UUID or enum name — safe to interpolate.
This slice's payloads embed genuinely free-text fields (a household's
`description`, a sponsor's `name`, a contribution's `supporterName`) that
could contain a `"` or `\` and produce invalid JSON if hand-interpolated.
Typed (de)serialization avoids that class of bug outright. The fee/
sponsorship-renewal scanners still use `objectMapper.writeValueAsString`
paired with `readTree` on the handler side (not `readValue` into a typed
class) — writing typed but reading generically was an oversight-turned-
convenience, not a deliberate inconsistency worth re-doing; both are safe.

**5. Notification preferences: a single `household.email_reminders_opt_out`
boolean (V20), not a per-channel/per-notification-type preferences table.**
`HouseholdRepository.update`/`HouseholdService.update`/`UpdateHouseholdRequest`
all gained an optional `emailRemindersOptOut` parameter following the exact
coalesce-on-null partial-update pattern every other household field already
uses. `FeePaymentReminderScanner`'s query checks it directly
(`h.email_reminders_opt_out = false`). Order/contribution/sponsorship
notifications are **not** gated by it — those are one-time transactional
confirmations of an action the recipient just took (paid, contributed,
sponsored), not recurring nags a household would reasonably want to silence,
the same distinction CAN-SPAM draws between transactional and marketing
email. Only recurring reminders (fee-payment today, any future recurring
reminder) check this flag. Setting it is currently an org-admin action via
the existing household-update endpoint (manager-role-gated,
`membershipService.requireManagerRole`) — no self-service Parent-dashboard
UI toggle exists yet; that remains a frontend gap, not a backend one.

## Consequences

- Every trigger added this slice writes at most one outbox event per
  business action — no new scanning infrastructure beyond the one new
  scanner (fee reminders), which is itself a near-identical copy of the
  sponsorship-renewal scanner ADR-022 already built.
- `OrderService`, `ContributionService`, and `SponsorshipService` each grew
  two new constructor dependencies (`OutboxWriter`, `ObjectMapper`) —
  consistent with how `InvitationService` already carries `OutboxWriter`,
  not a new pattern.
- "Campaign launch emails" remains unbuilt, and deliberately so — see
  Context. A future slice that wants it needs its own ADR resolving what a
  campaign "subscriber" even is (every org member? household opt-in? a
  public sign-up form on the campaign page?) before there's anything to
  wire an outbox event to.
- Shipping-update and event/RSVP-change notifications remain unbuilt,
  correctly blocked on real (non-draft) fulfillment and Phase 10's event
  model respectively — not pulled forward.
- The opt-out flag only gates fee reminders today. A future recurring
  reminder (there are none else yet) must remember to check it too; nothing
  enforces that structurally — a code-review discipline note, not a design
  flaw worth adding abstraction to prevent given there's exactly one
  recurring reminder type in this codebase right now.
- SMS (Twilio) remains out of this slice — slice 3, gated on its own consent
  decision (a household opting into/out of email is a separate decision
  from opting into SMS, which carries real per-message cost and stricter
  consent norms).

## Alternatives Considered

- **Inventing a campaign "subscriber" concept (e.g. capturing an email at
  campaign-page view, or treating every past contributor as a subscriber)
  to make "campaign launch emails" buildable this slice**: rejected — see
  Context; this is a real, independent feature decision the founder hasn't
  made, not a gap this slice's existing data can paper over.
- **Gating order/contribution/sponsorship transactional emails on the same
  household opt-out flag as fee reminders**: rejected — conflating
  transactional confirmations (which a supporter/sponsor expects and
  arguably needs, e.g. as a receipt) with recurring marketing-adjacent
  reminders would be both a worse user experience and arguably against
  CAN-SPAM's own transactional/marketing distinction.
- **A full per-notification-type preferences table
  (`notification_preference(household_id, notification_type, channel,
  enabled)`) instead of one boolean column**: rejected as exactly the
  "unnecessary framework around a question nobody asked" DESIGN-DOC.md
  section 19.3 warns against — there is exactly one recurring reminder type
  today; a granular table would be speculative infrastructure for
  notification types that don't exist yet.
- **Putting `markPaymentReminderSent` in `FeePaymentReminderHandler` instead
  of the scanner**: rejected for the same reason ADR-022 already rejected it
  for sponsorships — see decision 1.
- **A dedicated `household_adult`-level billing-contact selection for fee
  reminders instead of `household.contact_email`**: rejected as more
  decision than this slice needs; `household.contact_email` already exists
  and is exactly the field the household-update endpoint lets an org admin
  maintain — introducing a "which adult is the billing contact" concept is
  a bigger household-CRM question for a later slice, if ever.
