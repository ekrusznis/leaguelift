# ADR-114 — Phase 37.6: subscription/billing lifecycle emails

**Status:** Accepted
**Date:** 2026-08-11

## Context

Item 45's transactional-email audit (2026-08-11, this same phase) flagged two gaps:

- **(a)** Organization subscription/billing lifecycle had no email at all — `OrganizationSubscriptionService.handleInvoicePaid`/`handleInvoicePaymentFailed` only wrote an `audit_event` row, so an owner learned of billing trouble only by noticing suspended access, never by being told.
- **(b)** "A successful online fee payment has no receipt/confirmation email."

**Correction to (b) before any code was written:** a research pass to build this found there is no online Stripe fee-payment path in this codebase at all — `FeePayment.PaymentMethod` is `{CASH, CHECK, VENMO, ZELLE, OTHER}` only (DB-enforced, `V10__fee_payments_and_adjustments.sql`'s check constraint), `FeeService.recordPayment` is a manager-entered offline action requiring `requireManagerRole`, and `StripeWebhookController`'s `checkout.session.completed` disambiguation has no `feeAssignmentId` branch alongside its `orderId`/`sponsorshipId` ones. Item 45's own note asserted "a real online Stripe fee payment sends nothing" — true in spirit, but there's no such payment to send a receipt *for* yet. A receipt email requires the online fee-payment flow to exist first (a Checkout Session endpoint for fees, a webhook branch, a `PaymentMethod.ONLINE` value + migration) — that's a materially bigger scope than "add an email handler" and wasn't requested. **This slice ships (a) only**; DESIGN-DOC.md item 45 is corrected to record this rather than carry the stale premise forward.

## Decision

**Payment-failed and payment-succeeded/recovered detection reuses state already being written, not new tracking.** `OrganizationSubscriptionRepository.markPaymentSuccess` only ever touches `last_payment_success_at` — it never changes `status` — so `local.status` (read before the update) is exactly "was this subscription in trouble." `handleInvoicePaymentFailed` always enqueues (Stripe only calls this on a genuine failure); `handleInvoicePaid` only enqueues a "payment recovered" email when `local.status == PAST_DUE` beforehand, so a routine successful monthly renewal — the overwhelming majority of `invoice.paid` events — never sends anything. Recovery is a real state transition, not "we got paid again."

**Cancellation reuses the existing webhook route, detected by comparing old vs. new status.** `customer.subscription.deleted` already reached `handleSubscriptionChanged` before this ADR; no new webhook routing was needed. `previousStatus` (captured before `syncExternalState` overwrites it) vs. `mapped` (the new status) tells the difference between a genuine new cancellation and Stripe redelivering the same `.deleted` event for an already-canceled subscription (its own retry/replay behavior) — only the former enqueues an email.

**Trial-ending is the one genuinely new webhook route**, since `customer.subscription.trial_will_end` (fires ~3 days before trial end) previously fell into `StripeWebhookController`'s `else -> WebhookProcessingStatus.IGNORED` branch. Unlike the other three, there's no "was it different before" comparison to make here — it's inherently a point-in-time notification, so `handleTrialWillEnd` always enqueues when a local subscription and at least one recipient exist.

**Recipients are every active OWNER/ADMINISTRATOR, resolved once at write time.** `MembershipRepository.listActiveManagers` (already used elsewhere for team-manager notification resolution) plus `AppUserRepository.findById` per member, called inside the same `@Transactional` service method that writes the outbox row — a snapshot at the moment of the event, matching the pattern `FeePaymentReminderScanner` already established, not a re-lookup at dispatch time. An organization with no resolvable owner/admin email skips the outbox write entirely (nothing to send, no retry would ever help — same posture every existing reminder handler takes for a missing recipient).

**Four handler classes, not one, one file each** — `OutboxEventHandler.eventType` is a single string per implementation (`OutboxWorker` builds an `eventType -> handler` map via Spring's multi-bean injection; adding a `@Component` is the entire registration step, nothing manual to wire), and no file in this codebase groups multiple `@Component` classes together, so `SubscriptionPaymentFailedEmailHandler`/`SubscriptionPaymentRecoveredEmailHandler`/`SubscriptionCanceledEmailHandler`/`SubscriptionTrialEndingEmailHandler` each get their own file, matching `SponsorshipRenewalReminderHandler`/`FeePaymentReminderHandler`'s existing one-class-per-file shape. All four are plain `subject`/`body` text, no Resend template — consistent with 13 of the 15 existing handlers per item 45's own audit; nothing about the outbox mechanism requires a template.

**No new migration.** `outbox_event.event_type` is a bare `text not null` column with no check constraint (only `status` is constrained) — new event-type strings (`organization_subscription.payment_failed`/`.payment_recovered`/`.canceled`/`.trial_ending`) are valid without a schema change.

**Verification:** ktlint clean, full backend suite green (938 tests, no regressions) — including new unit tests for all four handlers and for `OrganizationSubscriptionService`'s four lifecycle methods (payment-failed enqueues; payment-succeeded-while-already-active does *not* enqueue; payment-succeeded-while-past-due does; a redelivered already-canceled webhook does not re-enqueue; a genuine new cancellation does; trial-will-end enqueues with a real formatted date; no resolvable recipient means no outbox write at all).

## Consequences

- Item (b) (fee-payment receipt) stays open and is now correctly scoped as "build the online fee-payment flow, then add its receipt email" — a future decision, not a follow-on to this slice.
- The recipient-resolution helpers (`ownerEmailsFor`, `enqueueLifecycleEmail`) live as private methods on `OrganizationSubscriptionService` rather than a shared utility — if a future phase adds more organization-level lifecycle notifications (e.g. plan-change confirmation), this is the natural place to extend, not duplicate.
