# ADR-017: Phase 5 Financial Model — Platform Fee, Payout Timing, Refunds, Negative Balance

## Status
Accepted

## Context

Phase 5 ("Financial controls and live pilot," DESIGN-DOC.md §14.1) requires an
immutable ledger and real Stripe Connect Transfer execution — the second half
of ADR-005's "separate charges and transfers" model, which so far only
implemented the charge half (test-mode Checkout for contributions and orders).
Building the ledger and transfer logic concretely required founder decisions
on four previously-open questions (§19.3 #5, plus three items that were
discussed in §16's prose but never actually tracked as numbered open
questions): the platform's transaction fee, when an org's earned share is
actually transferred, the refund policy, and what happens if a refund arrives
after a transfer already went out (negative balance).

Per §19.3's own instruction ("do not guess at these"), these were resolved
directly with the founder (2026-07-29) before any Phase 5 ledger/transfer code
is written.

## Decision

**Platform fee: 5% flat, stored as configuration, not hardcoded.** This is a
starting pilot value, not a final price — DESIGN-DOC.md §1 already requires
pricing to be "configurable, never hard-coded," and this ADR treats the fee
percentage the same way. Every `RALLY26_PLATFORM_FEE` ledger entry
references the rate that was actually applied at the time (immutable,
consistent with §8.6's ledger rules), so changing the configured rate later
never rewrites history.

**Payout timing: a configurable holding period (default 7 days) gates
eligibility; actual transfer firing requires an explicit admin trigger, not an
automatic schedule.** A confirmed contribution/order becomes eligible for
transfer once its holding period has elapsed (tracked, not automatically
acted on), but no cron-fired automatic transfer exists yet — a platform or
org-owner action calls the actual `Transfer` API. This is a deliberate
middle ground: it gives standard marketplace-style clawback protection (a
refund during the holding window never needs to touch an already-issued
transfer) without requiring a scheduled-job worker, which doesn't exist yet
(the outbox worker remains unconsumed, DESIGN-DOC.md §17). Automatic,
unattended transfer firing is a natural fast-follow once that infrastructure
exists, not a re-litigation of this decision.

**Refund policy: org-admin-initiated, within a 14-day window from payment.**
An org admin can request a refund for a contribution or order up to 14 days
after `confirmed_at`; Rally26 (as merchant of record, per ADR-005) executes
it via Stripe. This matches how the app already treats org admins as trusted
operators for fee payments/adjustments (`fee_payment`/`fee_adjustment`,
Phase 2 remainder) — no separate platform-admin approval step for this phase.

**Negative balance: deducted from the org's next payout.** If a refund is
issued after the corresponding transfer already went out, the shortfall is
not immediately clawed back or absorbed by Rally26 — it's recorded as a
negative balance against that organization and automatically deducted from
their next eligible transfer. This is standard Stripe Connect marketplace
practice and requires the ledger to support a running negative balance per
organization, not just per-transaction entries.

## Consequences

- The ledger (`ledger_entry`, §8.6) becomes buildable now: `PAYMENT_PROCESSING_FEE`,
  `RALLY26_PLATFORM_FEE` (5%, configurable), `ORGANIZATION_EARNING`,
  `TRANSFER`, `REFUND`, and `MANUAL_ADJUSTMENT` entry types all have a concrete
  computation rule now.
- `organization_payout_account` (or a sibling table) needs a running balance
  concept to support the negative-balance deduction rule — a transfer request
  must check for and net against any outstanding negative balance before
  transferring the current period's earnings.
- No scheduled/automatic transfer execution exists yet — every transfer this
  phase is admin-triggered. Building a real scheduler (and deciding who is
  authorized to trigger transfers — platform admin only, or org owners too) is
  in scope for this phase's implementation, not deferred further.
- Refund UI/API is now in scope for this phase (not deferred, unlike earlier
  phases where refunds were explicitly out of scope) — bounded to the 14-day,
  org-admin-initiated model above. Disputes (chargebacks) are **not** covered
  by this ADR and remain Phase 5's harder, still-open sub-problem.
- Tax calculation/remittance (§19.3 #4) and sponsorship payment-account sharing
  (§19.3 #19) remain genuinely open — this ADR does not resolve them, and
  nothing in this phase's ledger/transfer work should assume an answer to
  either.
- Family credit percentages/cross-season/expiry (§19.3 #6/#16/#17) remain
  open and unrelated to this ADR — the credit system stays deferred
  regardless of this phase's ledger work landing.

## Alternatives Considered

- **Immediate transfer on confirmation**: rejected — no buffer before a
  refund could require negative-balance handling, and the founder preferred
  the holding-period model for exactly that reason.
- **Fully automatic scheduled transfers**: rejected for this phase — would
  require building the outbox-consumer worker (or an equivalent scheduler)
  now, ahead of Phase 8's notifications infrastructure where that worker was
  already planned to land first. Manual-trigger-after-eligibility gets the
  safety property (holding period) without pulling that infrastructure
  forward.
- **Rally26 absorbing negative balances**: rejected — the founder chose
  standard marketplace clawback (deduct from next payout) over Rally26
  bearing the risk of every late refund.
- **Platform-admin-mediated refunds only**: rejected — org admins are already
  trusted with manual fee payments/adjustments; requiring Rally26 staff
  approval for every refund would add operational overhead without a clear
  safety benefit at pilot scale.
