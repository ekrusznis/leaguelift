# ADR-052: Phase 18 slice 3 — offline financial records

**Status:** Accepted and implemented locally

**Date:** 2026-08-01

## Context

Pilot organizations receive some money outside Rally26: cash, checks, ACH, external card terminals, Venmo, Zelle, and other direct methods. The existing contribution, sponsorship, and order models assumed Stripe confirmation. Reusing fake checkout sessions or payment-intent identifiers would corrupt provider reconciliation. Treating outside funds as Rally26 payout earnings would be equally incorrect because Rally26 never received or held the money.

The workflow also needs a review boundary. A manager may enter a transaction from a paper receipt before a second person confirms the deposit. A record must therefore be durable while pending, but must not inflate confirmed campaign, sponsorship, store, or ledger totals until verification.

## Decision

### Explicit source and verification state

- Contributions, sponsorships, and orders gain an explicit `payment_source` of `STRIPE` or `OFFLINE`; existing rows migrate to `STRIPE`.
- An `offline_financial_record` links to exactly one contribution, sponsorship, or order and records payment method, reference, payer details, received time, internal notes, recorder, optional acknowledgement choice, and verification actor/time.
- New records begin as `PENDING_VERIFICATION` unless the authorized manager explicitly selects “verify now.”
- The API requires a caller idempotency key and stores a deterministic duplicate fingerprint. Repeating the same request returns the existing record; reusing a key for different content or creating a matching fingerprint is rejected.

### Verification and ledger treatment

- Verification locks the offline record and confirms only the linked pending `OFFLINE` source record.
- A confirmed offline contribution or sponsorship appends the ordinary gross credit plus an equal `OFFLINE_SETTLEMENT` debit.
- A confirmed offline order appends gross sale, snapshotted production cost, and an equal offline-settlement debit for the received gross.
- No `ORGANIZATION_EARNING` entry is created for outside money, so it cannot be included in a Rally26 payout transfer.
- An offline order may contain only active manual products from one active store and one manual vendor. Verification creates `READY` manual fulfillment if one does not already exist; no Printify call occurs.
- Original records and ledger entries are never edited to correct history. Void/reversal/refund extensions remain Phase 18.5.

### Authorization, audit, and communication

- Listing, creation, and verification require the existing organization manager boundary (`OWNER` or `ADMINISTRATOR`).
- Create and verify actions are audited.
- Pending records appear as a high-priority organization-manager Action Center item deep-linking to Financial Operations.
- When requested and a payer email is present, verification writes an outbox event for an acknowledgement explaining that the payment was recorded in Rally26 but processed outside Rally26. Email failure does not undo the financial record.

### Help Center maintenance

- V38 publishes an `OWNER_ADMIN` how-to article for recording and verifying offline payments.
- `docs/help/HELP-CENTER-COVERAGE.md` establishes an ongoing rule that major feature slices add or update their own audience-scoped documentation.
- A complete route/persona Help Center audit is required before Phase 21 production go/no-go.

## Consequences

- Pilot staff can reconcile real outside receipts without inventing Stripe or Printify activity.
- Pending entries are visible and reviewable but do not affect confirmed totals.
- Verified outside money is represented in the ledger without entering Rally26’s payout liability.
- The initial workflow intentionally has no edit/delete or financial correction button. Controlled voids and reversals must append linked correction records in a later slice.
- This slice does not implement payment plans/installments or a consolidated reconciliation dashboard; those remain Phase 18.4 and 18.6.
