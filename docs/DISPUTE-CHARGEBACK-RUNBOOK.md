# Dispute/Chargeback Runbook

DESIGN-DOC.md §14.6 item #4. Rally26 is the merchant of record for every Stripe charge (ADR-005) — a cardholder dispute always lands against **Rally26's own Stripe account**, never a connected organization's. This runbook is the "reviewed runbook" that compliance item asks for before Live Payments Launch.

## What happens automatically

When Stripe sends `charge.dispute.created` (a dispute was opened, funds already withdrawn from Rally26's balance):

1. The disputed `payment_intent` is matched back to the order/contribution/sponsorship/fee payment it belongs to, and the owning organization.
2. The ledger reverses that organization's earning for the disputed amount, net of the platform fee (`LedgerService.recordDisputeOpened` — same shape as a refund, entry type `CHARGEBACK`).
3. Stripe's own ~$15 non-refundable dispute fee is recorded separately (`CHARGEBACK_FEE`) but **absorbed by Rally26** — it never reduces what the organization is owed.
4. Every active OWNER/ADMINISTRATOR for the organization gets an email notification.
5. An audit event (`payment_dispute.opened`) and a `payment_dispute` row are recorded — visible in-app at `/app/organizations/{organizationId}/disputes`.

When Stripe sends `charge.dispute.closed` (resolved):

- **Won**: the ledger reinstates the organization's earning (`LedgerService.recordDisputeWon`, entry type `CHARGEBACK` credit). The dispute fee is never reinstated — it's non-refundable even when Rally26 wins.
- **Lost**: no further ledger entry — the original reversal already reflects reality. The `payment_dispute` row's status updates to `LOST`.
- Either way, the organization's managers get a resolution email, and an audit event (`payment_dispute.resolved`) is recorded.

## What a human must do

This app does **not** submit dispute evidence to Stripe — that stays manual:

1. **Check the Stripe Dashboard regularly** (Payments → Disputes) for anything in `needs_response`.
2. **Note the `evidence_due_by` deadline** — surfaced in-app on the disputes list, but the Stripe Dashboard is authoritative. Missing this deadline forfeits the dispute automatically.
3. **Gather evidence** relevant to the dispute reason (receipt/confirmation email, delivery/fulfillment proof, communication with the cardholder, refund policy shown at checkout, etc.) — whatever Stripe's evidence form for that dispute reason asks for.
4. **Decide whether to contest.** For a small amount or an unwinnable reason (e.g. genuine fraud), it may be cheaper to accept the loss than spend time contesting. There's no in-app tooling to help with this decision yet — use judgment.
5. **Submit the response in the Stripe Dashboard** before the deadline, or accept the dispute (which also closes it, with a `lost` outcome).

## Verifying a resolved dispute reconciled correctly

After a `charge.dispute.closed` event fires:

1. Confirm the `payment_dispute` row's `status` matches Stripe's own dispute status (Dashboard or API).
2. If `WON`: confirm a `CHARGEBACK` credit and `ORGANIZATION_EARNING` credit exist in `ledger_entry` for that dispute's `source_type`/`source_id`, matching the original debited amounts.
3. If `LOST`: confirm no reversal entries exist beyond the original `charge.dispute.created` debits — the organization's earning should reflect the loss permanently.
4. Cross-check the `CHARGEBACK_FEE` entry's amount against Stripe's actual balance transaction for the dispute (the in-app amount is a configured estimate, `rally26.dispute.fee-minor`, not parsed from the live payload — see `DisputeProperties`). If Stripe's real fee differs meaningfully from the estimate, update the config value.

## Who's responsible

Until Rally26 has dedicated support/finance staff, the founder is responsible for Stripe Dashboard monitoring and evidence submission. This should be revisited once real dispute volume exists.

## Known limits (not built)

- No evidence-submission UI — Stripe Dashboard only.
- No automated reminder before `evidence_due_by` — a human must check the disputes list or Stripe Dashboard proactively.
- `CHARGEBACK_FEE` is a configured estimate, not parsed from Stripe's real balance transaction data (deliberate — that payload shape isn't reliable enough to parse without live verification against a real dispute).
